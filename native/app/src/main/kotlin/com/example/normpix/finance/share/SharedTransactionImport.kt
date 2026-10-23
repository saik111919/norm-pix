package com.example.normpix.finance.share

import com.example.normpix.banks.BankCatalog
import com.example.normpix.finance.data.FinanceRepository
import com.example.normpix.finance.domain.Account
import com.example.normpix.finance.domain.FinanceTransaction
import com.example.normpix.finance.domain.MoneyFormatter
import com.example.normpix.finance.domain.NUMBER_ENDING_LENGTH
import com.example.normpix.finance.domain.TransactionType
import com.example.normpix.finance.domain.cardBillTarget
import com.example.normpix.finance.ui.FinanceState
import com.example.normpix.finance.ui.label
import com.example.normpix.finance.ui.shortDate
import com.example.normpix.finance.ui.timeLabel
import com.example.normpix.wallet.CardKind
import com.example.normpix.wallet.StoredCard
import java.io.File
import java.time.LocalDateTime
import kotlinx.coroutines.CancellationException

/**
 * Where a draft's category came from: the user's own last entry for the payee, the app's table of merchants, or - for a
 * payee neither knows - the word the payer wrote with the payment ("Auto").
 */
enum class CategoryOrigin { History, Merchant, Message }

/** One thing the form says about a shared draft; [attention] marks what has to be checked or entered before saving. */
data class ShareNotice(val text: String, val attention: Boolean = false)

/**
 * What the new-transaction form starts with when it comes from text shared by another app, and what it says about it.
 * Nothing here is saved until the user saves the form.
 */
data class SharedTransactionPrefill(
    /** Null when the message didn't say whether money went out or came in: the form asks. */
    val type: TransactionType? = null,
    val amount: Long? = null,
    val payee: String? = null,
    val note: String? = null,
    /** Null to start at now, as any new entry does. */
    val occurredAt: LocalDateTime? = null,
    /** Whether the message gave the time as well: without it, [occurredAt] has the time of reading. */
    val timeRead: Boolean = false,
    /** The message's date couldn't be read, so the date has to be chosen before saving. */
    val dateNeedsChoice: Boolean = false,
    val accountId: Long? = null,
    /**
     * Set when the message is the user's own credit card's bill being paid ("Paid to HDFC Bank Credit Card"): the entry is
     * then a [TransactionType.Transfer] onto that card (see `cardBillTarget`), not an expense.
     */
    val toAccountId: Long? = null,
    /**
     * The message shows an account's number and none of the user's accounts (of several) is that one, or more than one is: the
     * form starts with no account chosen, rather than the default one, which would be a guess.
     */
    val accountNeedsChoice: Boolean = false,
    /** What to say about it when [accountNeedsChoice]: which number the message shows, and that the account is the user's to choose. */
    val accountNotice: ShareNotice? = null,
    val categoryId: Long? = null,
    /** What to tell the user, things to check first. */
    val notices: List<ShareNotice> = emptyList(),
    /** Looked for again at save time, in case the same payment is already recorded. */
    val reference: String? = null,
    /** Read from a shared picture's text, which the recognizer may have got wrong: the form says so. */
    val fromPicture: Boolean = false,
    /** The text the phone read from the picture, which the form can show as it is (to check it, or copy it). */
    val readText: String? = null,
    /** The shared picture, staged to be attached to the transaction (see `TransactionPictures.stage`); the form owns it. */
    val picture: File? = null,
    /** The payment app whose receipt it was read as, by that app's own reader (see [PaymentAppReceipts]). */
    val app: String? = null,
) {
    companion object {
        /** For a share nothing could be read from, which the user chose to enter by hand. */
        fun nothingRead(fromPicture: Boolean) = SharedTransactionPrefill(
            notices = listOf(
                ShareNotice(
                    "Nothing could be read from the shared ${if (fromPicture) "picture" else "text"}. Enter the details yourself.",
                    attention = true,
                ),
            ),
            fromPicture = fromPicture,
        )
    }
}

/**
 * Turns what was read from a shared message into the form's starting point: the amount, payee, date and description as
 * read; the account, when the message names a bank the user has exactly one fitting account with; the category, when the
 * user filed this payee before; and what needs checking - a missing amount, an unknown direction, a date that couldn't be
 * read, a payment that may already be recorded.
 */
object SharedTransactionImport {
    suspend fun prefill(
        draft: ParsedTransactionDraft,
        text: String,
        state: FinanceState,
        repository: FinanceRepository,
        now: LocalDateTime,
        /** The Wallet's cards, whose numbers say which account a receipt's partly hidden number is (see [Account.cardId]). */
        cards: List<StoredCard> = emptyList(),
    ): SharedTransactionPrefill {
        val occurredAt = draft.date?.let { LocalDateTime.of(it, draft.time ?: now.toLocalTime().withSecond(0).withNano(0)) }
        // An app's receipt says which part is the user's account; the rest of it is the other side's, and the app's bank.
        val ownSide = draft.accountText ?: text
        val match = findAccount(ownSide, draft.counterparty, draft.instrument, state.activeAccounts, state.catalog, draft.maskedNumbers, cards)
        val payee = draft.counterparty
        // Money paid to one of the user's own credit cards is that card's bill: a transfer onto it, not spending.
        val billCard = if (draft.type == TransactionType.Expense && payee != null) {
            cardBillTarget(payee, state.activeAccounts, paidFrom = null) { state.catalog.resolve(it)?.key }
        } else {
            null
        }
        // The card is where the money went, so it is never also where it came from.
        val account = match.account?.takeIf { it.id != billCard?.id }
        val type = if (billCard != null) TransactionType.Transfer else draft.type
        // The user's own filing of this payee first; a merchant the app knows only for a payee they haven't filed yet.
        val fromHistory = if (billCard == null && draft.type != null && payee != null) {
            quietly { repository.lastCategoryForPayee(payee, draft.type) }
        } else {
            null
        }
        val fromMerchant = if (billCard == null && fromHistory == null) MerchantCategories.categoryFor(payee, draft.type, state.categories) else null
        val fromMessage = if (billCard == null && fromHistory == null && fromMerchant == null) {
            MerchantCategories.categoryForMessage(draft.message, draft.type, state.categories)
        } else {
            null
        }
        val categoryId = fromHistory ?: fromMerchant ?: fromMessage
        val categoryOrigin = when {
            fromHistory != null -> CategoryOrigin.History
            fromMerchant != null -> CategoryOrigin.Merchant
            fromMessage != null -> CategoryOrigin.Message
            else -> null
        }
        val duplicate = quietly {
            repository.possibleDuplicates(
                type, draft.amount, occurredAt ?: now, if (billCard != null) null else payee, draft.reference, limit = 1,
                timeKnown = draft.date != null && draft.time != null,
            ).firstOrNull()
        }
        // With one account there is nothing to choose between.
        val needsChoice = account == null && draft.maskedNumbers.isNotEmpty() && state.activeAccounts.size > 1
        val notices = notices(draft, state.money, now, account, categoryOrigin, duplicate = duplicate, accountByNumber = match.byNumber, accountsFit = match.fitting, accountCard = match.card)
        return SharedTransactionPrefill(
            type = type,
            amount = draft.amount,
            payee = payee,
            // A transfer keeps no payee, so the note says what it was (and keeps the reference duplicates are found by).
            note = if (billCard != null) listOfNotNull("Card bill payment", draft.note).joinToString(" · ") else draft.note,
            occurredAt = occurredAt,
            timeRead = draft.date != null && draft.time != null,
            dateNeedsChoice = draft.dateUnreadable,
            accountId = account?.id,
            toAccountId = billCard?.id,
            accountNeedsChoice = needsChoice,
            accountNotice = accountNotice(draft, account, match.fitting, match.card).takeIf { needsChoice },
            categoryId = categoryId,
            notices = if (billCard != null) notices + ShareNotice(cardBillNotice(billCard)) else notices,
            reference = draft.reference,
            app = draft.app,
        )
    }

    /** A Wallet card as the form names it to the user: "HDFC Bank credit card ending 4242". Only what the user typed, and its last four digits. */
    fun describeCard(card: StoredCard): String =
        "${card.bankName} ${if (card.kind == CardKind.Credit) "credit" else "debit"} card ending ${card.last4}"

    /** What the form says when a shared payment is a card's bill, and so was made a transfer onto the card. */
    fun cardBillNotice(card: Account): String =
        "Paid to your ${card.name}, so it's recorded as a bill payment: a transfer onto the card. What's owed on it goes down, " +
            "and the money isn't counted as spending twice. Switch to Expense if it wasn't your card's bill."

    /** A lookup that only adds to the draft: if it fails, the draft goes without it. */
    private suspend fun <T> quietly(block: suspend () -> T): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }

    /**
     * Which account a message is about, and how that was found: [account] is null when it can't be told - the message names no
     * account, or more than one fits.
     */
    class AccountMatch(
        val account: Account?,
        /** The digits the message shows chose it, not just its bank. */
        val byNumber: Boolean = false,
        /** How many accounts those digits fit: more than one means they could not choose between them. */
        val fitting: Int = 0,
        /** The Wallet card the digits are, when no account had them and a card did (see [AccountMatching]); with or without an account for it. */
        val card: StoredCard? = null,
    )

    /** [findAccount]'s answer, for a caller that has no use for how it was found. */
    fun matchAccount(
        text: String,
        counterparty: String?,
        instrument: SharedInstrument?,
        accounts: List<Account>,
        catalog: BankCatalog,
        maskedNumbers: List<MaskedNumber> = emptyList(),
        cards: List<StoredCard> = emptyList(),
    ): Account? = findAccount(text, counterparty, instrument, accounts, catalog, maskedNumbers, cards).account

    /**
     * The one active account the message points to - a picture of a receipt is read the same way as its text. The accounts are
     * asked first: by the digits the message shows ([maskedNumbers]) against each account's own first and last digits and the
     * number of its Wallet [cards] - when exactly one account has them - and then by bank (and kind of card or account), when it
     * names one the user has exactly one fitting account with; an account whose number is known and different from the one
     * shown is not one of those. Only when no account fits are the Wallet's cards asked: a card that has the digits but is
     * linked to no account points to the one account of that card's bank and kind. The payee's name and the "Powered by" bank a
     * UPI app prints are left out first, so "Paid to Airtel" is not read as Airtel Payments Bank. See [AccountMatching].
     */
    fun findAccount(
        text: String,
        counterparty: String?,
        instrument: SharedInstrument?,
        accounts: List<Account>,
        catalog: BankCatalog,
        maskedNumbers: List<MaskedNumber> = emptyList(),
        cards: List<StoredCard> = emptyList(),
    ): AccountMatch {
        val pick = AccountMatching.find(text, counterparty, instrument, accounts, catalog, maskedNumbers, cards)
        return AccountMatch(pick.account, pick.byNumber, pick.fitting, pick.card)
    }

    /**
     * A number is shown and no account is chosen by it: the account is the user's to pick, not the default's to be assumed.
     * Null when an account was chosen, or the message shows no number.
     */
    fun accountNotice(draft: ParsedTransactionDraft, account: Account?, accountsFit: Int, card: StoredCard? = null): ShareNotice? {
        if (account != null || draft.maskedNumbers.isEmpty()) return null
        val shown = draft.maskedNumbers.first().let {
            if (it.prefix.isEmpty()) "ending ${it.suffix}" else "starting ${it.prefix} and ending ${it.suffix}"
        }
        // A number printed with its first digits is a card's: its Wallet card can be linked as well.
        val isCard = draft.maskedNumbers.first().prefix.isNotEmpty()
        val text = if (card != null && accountsFit <= 1) {
            // No account has the number, but a card in the Wallet does: say which, and that it has no account to be chosen.
            "The message shows a number $shown, which is your ${describeCard(card)} in the Wallet, but none of your accounts is " +
                "linked to it. Choose the account it was paid from, and link the card to it so it's picked next time."
        } else if (accountsFit > 1) {
            "The message shows a number $shown, which fits more than one of your accounts. Choose the right one."
        } else {
            "The message shows a number $shown, but none of your accounts has it. Choose the account, and add the last " +
                "$NUMBER_ENDING_LENGTH digits${if (isCard) " (or its card from the Wallet)" else ""} to it so it's picked next time."
        }
        return ShareNotice(text, attention = true)
    }

    /** What the form says about a draft, what needs attention first. */
    fun notices(
        draft: ParsedTransactionDraft,
        money: MoneyFormatter,
        now: LocalDateTime,
        account: Account?,
        category: CategoryOrigin?,
        duplicate: FinanceTransaction?,
        /** [account] was chosen by the digits the message shows, not just by its bank. */
        accountByNumber: Boolean = false,
        /** How many accounts the digits the message shows fit. */
        accountsFit: Int = 0,
        /** The Wallet card the digits are, when no account had them and a card did. */
        accountCard: StoredCard? = null,
    ): List<ShareNotice> {
        val attention = buildList {
            duplicate?.let { add("You may have recorded this already: ${describe(it, money, now)}.") }
            when (draft.status) {
                SharedStatus.Failed -> add("The message says this payment failed. A failed payment usually moves no money.")
                SharedStatus.Requested -> add("This is a payment request: nothing has been paid yet.")
                SharedStatus.Pending -> add("The message says this is still pending, so it may not go through.")
                else -> Unit
            }
            when {
                draft.amount != null -> Unit
                draft.foreignCurrency != null ->
                    add("The amount is in ${draft.foreignCurrency}. NormPix doesn't convert it, so enter it in ${money.currency.code}.")
                draft.ambiguousAmounts.size > 1 ->
                    add("The message mentions ${draft.ambiguousAmounts.joinToString(" and ") { money.format(it) }}. Enter the one that moved.")
                else -> add("No amount found in the message. Enter it.")
            }
            if (draft.type == null) add("Couldn't tell whether money went out or came in. Choose Expense or Income.")
            accountNotice(draft, account, accountsFit, accountCard)?.let { add(it.text) }
            when {
                draft.dateUnreadable -> add("The message's date couldn't be read. Choose the date.")
                draft.date != null && draft.date.isAfter(now.toLocalDate()) -> add("The message's date is in the future. Check it.")
            }
        }
        val info = buildList {
            draft.app?.let { add("Read as a $it receipt.") }
            when (draft.kind) {
                SharedKind.Refund -> add("A refund is money coming back, so it's recorded as income.")
                SharedKind.Reversal -> add("A reversal returns money from a payment that didn't go through. Save it only if you recorded that payment.")
                else -> Unit
            }
            if (draft.date == null && !draft.dateUnreadable) add("No date in the message, so it's set to now.")
            when (category) {
                CategoryOrigin.History -> draft.counterparty?.let {
                    add("Category from your last ${if (draft.type == TransactionType.Income) "income from" else "payment to"} $it.")
                }
                CategoryOrigin.Merchant -> draft.counterparty?.let { add("Category picked for $it. Change it if it doesn't fit.") }
                CategoryOrigin.Message -> draft.message?.let { add("Category picked from your message \"$it\". Change it if it doesn't fit.") }
                null -> Unit
            }
            account?.let {
                add(if (accountByNumber) "Account from the number in the message: ${it.name}." else "Account from the bank in the message: ${it.name}.")
            }
        }
        return attention.map { ShareNotice(it, attention = true) } + info.map { ShareNotice(it) }
    }

    /**
     * "an expense of ₹250.00 to Swiggy on 10 Oct at 9:05 AM" - one already recorded, for a duplicate warning. The time
     * says which of the day's payments it is (the morning cab, not the evening one).
     */
    fun describe(t: FinanceTransaction, money: MoneyFormatter, now: LocalDateTime, is24Hour: Boolean = false): String {
        val party = t.payee?.let { if (t.type == TransactionType.Income) " from $it" else " to $it" }.orEmpty()
        val noun = t.type.label.lowercase()
        val article = if (noun.first() in "aeiou") "an" else "a"
        return "$article $noun of ${money.format(t.amount)}$party on ${shortDate(t.occurredAt, now)} at ${timeLabel(t.occurredAt, is24Hour)}"
    }
}
