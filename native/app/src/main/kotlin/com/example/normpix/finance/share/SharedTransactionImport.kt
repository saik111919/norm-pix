package com.example.normpix.finance.share

import com.example.normpix.banks.BankCatalog
import com.example.normpix.finance.data.FinanceRepository
import com.example.normpix.finance.domain.Account
import com.example.normpix.finance.domain.AccountKind
import com.example.normpix.finance.domain.FinanceTransaction
import com.example.normpix.finance.domain.MoneyFormatter
import com.example.normpix.finance.domain.TransactionType
import com.example.normpix.finance.ui.FinanceState
import com.example.normpix.finance.ui.label
import com.example.normpix.finance.ui.shortDate
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
    /** The message's date couldn't be read, so the date has to be chosen before saving. */
    val dateNeedsChoice: Boolean = false,
    val accountId: Long? = null,
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
    ): SharedTransactionPrefill {
        val occurredAt = draft.date?.let { LocalDateTime.of(it, draft.time ?: now.toLocalTime().withSecond(0).withNano(0)) }
        val account = matchAccount(text, draft.counterparty, draft.instrument, state.activeAccounts, state.catalog)
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
            repository.possibleDuplicates(draft.type, draft.amount, occurredAt ?: now, payee, draft.reference, limit = 1).firstOrNull()
        }
        return SharedTransactionPrefill(
            type = draft.type,
            amount = draft.amount,
            payee = payee,
            note = draft.note,
            occurredAt = occurredAt,
            dateNeedsChoice = draft.dateUnreadable,
            accountId = account?.id,
            categoryId = categoryId,
            notices = notices(draft, state.money, now, account, categoryOrigin, duplicate = duplicate),
            reference = draft.reference,
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
     * The one active account the message's bank (and kind of card or account) points to, or null when it names no bank
     * the user has an account with, or more than one account fits. The payee's name is left out first, so "Paid to
     * Airtel" is not read as Airtel Payments Bank.
     */
    fun matchAccount(
        text: String,
        counterparty: String?,
        instrument: SharedInstrument?,
        accounts: List<Account>,
        catalog: BankCatalog,
    ): Account? {
        val scrubbed = if (counterparty.isNullOrBlank()) text else text.replace(counterparty, " ", ignoreCase = true)
        val bank = catalog.resolve(scrubbed) ?: return null
        val ofBank = accounts.filter { !it.archived && it.bankKey == bank.key }
        val fitting = when (instrument) {
            SharedInstrument.CreditCard -> ofBank.filter { it.kind == AccountKind.Card }
            SharedInstrument.DebitCard, SharedInstrument.BankAccount, SharedInstrument.Upi -> ofBank.filter { it.kind != AccountKind.Card }
            SharedInstrument.Wallet -> ofBank.filter { it.kind == AccountKind.Wallet }
            SharedInstrument.Card, null -> ofBank
        }
        return fitting.singleOrNull()
    }

    /** What the form says about a draft, what needs attention first. */
    fun notices(
        draft: ParsedTransactionDraft,
        money: MoneyFormatter,
        now: LocalDateTime,
        account: Account?,
        category: CategoryOrigin?,
        duplicate: FinanceTransaction?,
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
            when {
                draft.dateUnreadable -> add("The message's date couldn't be read. Choose the date.")
                draft.date != null && draft.date.isAfter(now.toLocalDate()) -> add("The message's date is in the future. Check it.")
            }
        }
        val info = buildList {
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
            account?.let { add("Account from the bank in the message: ${it.name}.") }
        }
        return attention.map { ShareNotice(it, attention = true) } + info.map { ShareNotice(it) }
    }

    /** "an expense of ₹250.00 to Swiggy on 10 Oct" - one already recorded, for a duplicate warning. */
    fun describe(t: FinanceTransaction, money: MoneyFormatter, now: LocalDateTime): String {
        val party = t.payee?.let { if (t.type == TransactionType.Income) " from $it" else " to $it" }.orEmpty()
        val noun = t.type.label.lowercase()
        val article = if (noun.first() in "aeiou") "an" else "a"
        return "$article $noun of ${money.format(t.amount)}$party on ${shortDate(t.occurredAt, now)}"
    }
}
