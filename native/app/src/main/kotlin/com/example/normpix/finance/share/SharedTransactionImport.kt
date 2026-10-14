package com.example.normpix.finance.share

import com.example.normpix.banks.BankCatalog
import com.example.normpix.finance.data.FinanceRepository
import com.example.normpix.finance.domain.Account
import com.example.normpix.finance.domain.AccountKind
import com.example.normpix.finance.domain.FinanceTransaction
import com.example.normpix.finance.domain.MoneyFormatter
import com.example.normpix.finance.domain.NUMBER_ENDING_LENGTH
import com.example.normpix.finance.domain.TransactionType
import com.example.normpix.finance.ui.FinanceState
import com.example.normpix.finance.ui.label
import com.example.normpix.finance.ui.shortDate
import com.example.normpix.finance.ui.timeLabel
import com.example.normpix.wallet.StoredCard
import java.io.File
import java.time.LocalDateTime
import kotlinx.coroutines.CancellationException

/** Where a draft's category came from: the user's own last entry for the payee, or the app's table of merchants. */
enum class CategoryOrigin { History, Merchant }

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
        val account = match.account
        val payee = draft.counterparty
        // The user's own filing of this payee first; a merchant the app knows only for a payee they haven't filed yet.
        val fromHistory = if (draft.type != null && payee != null) quietly { repository.lastCategoryForPayee(payee, draft.type) } else null
        val fromMerchant = if (fromHistory == null) MerchantCategories.categoryFor(payee, draft.type, state.categories) else null
        val categoryId = fromHistory ?: fromMerchant
        val categoryOrigin = when {
            fromHistory != null -> CategoryOrigin.History
            fromMerchant != null -> CategoryOrigin.Merchant
            else -> null
        }
        val duplicate = quietly {
            repository.possibleDuplicates(
                draft.type, draft.amount, occurredAt ?: now, payee, draft.reference, limit = 1, timeKnown = draft.date != null && draft.time != null,
            ).firstOrNull()
        }
        // With one account there is nothing to choose between.
        val needsChoice = account == null && draft.maskedNumbers.isNotEmpty() && state.activeAccounts.size > 1
        return SharedTransactionPrefill(
            type = draft.type,
            amount = draft.amount,
            payee = payee,
            note = draft.note,
            occurredAt = occurredAt,
            timeRead = draft.date != null && draft.time != null,
            dateNeedsChoice = draft.dateUnreadable,
            accountId = account?.id,
            accountNeedsChoice = needsChoice,
            accountNotice = accountNotice(draft, account, match.fitting).takeIf { needsChoice },
            categoryId = categoryId,
            notices = notices(draft, state.money, now, account, categoryOrigin, duplicate = duplicate, accountByNumber = match.byNumber, accountsFit = match.fitting),
            reference = draft.reference,
            app = draft.app,
        )
    }

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
     * The one active account the message points to. First by its number: the digits it shows ([maskedNumbers]) against each
     * account's own last digits and the number of its Wallet [cards] - when exactly one account has them. Otherwise by bank
     * (and kind of card or account), when it names one the user has exactly one fitting account with; an account whose
     * number is known and different from the one shown is not one of those. The payee's name and the "Powered by" bank a
     * UPI app prints are left out first, so "Paid to Airtel" is not read as Airtel Payments Bank.
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
        val active = accounts.filter { !it.archived }
        val fits = active.associateWith { AccountNumbers.fit(it, maskedNumbers, cards) }
        val ofBank = bankFitting(text, counterparty, instrument, active, catalog)
        val byNumber = active.filter { fits[it] == NumberFit.Match }
        if (byNumber.size == 1) return AccountMatch(byNumber.single(), byNumber = true, fitting = 1)
        if (byNumber.size > 1) {
            // Two accounts end the same way (at different banks, say): the message's bank may tell them apart.
            val narrowed = byNumber.filter { it in ofBank }.singleOrNull()
            return AccountMatch(narrowed, byNumber = narrowed != null, fitting = byNumber.size)
        }
        return AccountMatch(ofBank.filter { fits[it] != NumberFit.Contradicts }.singleOrNull())
    }

    /** What the receipt's own wording says about the user's bank: not its payee, not the bank that "powers" the UPI app. */
    private val poweredBy = Regex("""(?i)\bpowered\s+by\b[^\n]*(?:\n[^\n]{0,40})?""")

    private fun bankFitting(
        text: String,
        counterparty: String?,
        instrument: SharedInstrument?,
        accounts: List<Account>,
        catalog: BankCatalog,
    ): List<Account> {
        val withoutPayee = if (counterparty.isNullOrBlank()) text else text.replace(counterparty, " ", ignoreCase = true)
        val bank = catalog.resolve(poweredBy.replace(withoutPayee, " ")) ?: return emptyList()
        val ofBank = accounts.filter { it.bankKey == bank.key }
        return when (instrument) {
            SharedInstrument.CreditCard -> ofBank.filter { it.kind == AccountKind.Card }
            SharedInstrument.DebitCard, SharedInstrument.BankAccount, SharedInstrument.Upi -> ofBank.filter { it.kind != AccountKind.Card }
            SharedInstrument.Wallet -> ofBank.filter { it.kind == AccountKind.Wallet }
            SharedInstrument.Card, null -> ofBank
        }
    }

    /**
     * A number is shown and no account is chosen by it: the account is the user's to pick, not the default's to be assumed.
     * Null when an account was chosen, or the message shows no number.
     */
    fun accountNotice(draft: ParsedTransactionDraft, account: Account?, accountsFit: Int): ShareNotice? {
        if (account != null || draft.maskedNumbers.isEmpty()) return null
        val shown = draft.maskedNumbers.first().let {
            if (it.prefix.isEmpty()) "ending ${it.suffix}" else "starting ${it.prefix} and ending ${it.suffix}"
        }
        // A number printed with its first digits is a card's: its Wallet card can be linked as well.
        val isCard = draft.maskedNumbers.first().prefix.isNotEmpty()
        val text = if (accountsFit > 1) {
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
            accountNotice(draft, account, accountsFit)?.let { add(it.text) }
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
