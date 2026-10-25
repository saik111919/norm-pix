package com.example.normpix.finance.share

import com.example.normpix.banks.BankCatalog
import com.example.normpix.finance.domain.Account
import com.example.normpix.finance.domain.TransactionType
import com.example.normpix.wallet.StoredCard

/*
 * Money moved between two of the user's own accounts - Google Pay's "Self Transfer", PhonePe's "To Self Account", Paytm's
 * "To Bank/Self" - is neither spending nor income. Recorded as an expense from one account it would be counted as spent, and
 * the other account would never see it arrive; so a shared receipt of one becomes a transfer between the two.
 *
 * No app publishes what its receipt of one looks like, and the name a UPI receipt gives the other side is the one the
 * receiving bank has for the account - the user's own. So a self transfer is told by any one of:
 * 1. the other side's number being one of the user's other accounts' - by its digits, never by its bank alone: paying a
 *    friend who banks where the user does is a payment;
 * 2. the message saying so ([SelfTransferWords]);
 * 3. the same person named on both sides ([samePerson]), where a receipt names both (Google Pay's and Paytm's "To:" and
 *    "From:").
 * With 2 or 3, the other side's bank is enough to say which of the user's accounts it is, as it is for their own side.
 */

/** How a message says the money moved between the user's own accounts. */
enum class SelfTransferCue {
    /** In words: "Self transfer", "Transfer to self", "To self account". */
    Words,

    /** The same person named on both sides of the payment. */
    SameName,
}

/** The words a receipt or a bank's message says a self transfer in. */
internal object SelfTransferWords {
    private val said = Regex(
        """(?i)\bself[\s\-]*(?:transfer(?:red)?|trf|txn|a/?c|acc(?:oun)?t)\b|""" +
            """\b(?:to|into)\s+(?:your\s*self\b|(?:your\s+)?(?:self\b|own\s+(?:a/?c|acc(?:oun)?t|bank)))|""" +
            """\bown\s+(?:a/?c|acc(?:oun)?t)s?\s+transfer\b|\bbetween\s+(?:your|my)\s+(?:own\s+)?(?:bank\s+)?accounts\b|\(\s*self\s*\)""",
    )

    fun saidIn(text: String): Boolean = said.containsMatchIn(text)
}

/** "Mr", "Smt." in front of a name: a payee is printed with one, the payer's own name without. */
private val honorific = Regex("""(?i)^(?:mr|mrs|ms|miss|dr|shri|smt|sri)\.?\s+""")

/** Fewer letters than this is not enough of a name to say two are the same person. */
private const val MIN_NAME_LETTERS = 4

/**
 * Whether [a] and [b] name the same person: the same words, in any order (a bank may put the surname first), in any case,
 * with a title in front of either left out.
 */
internal fun samePerson(a: String?, b: String?): Boolean {
    val x = personKey(a) ?: return false
    return x == personKey(b)
}

private fun personKey(name: String?): List<String>? {
    val words = name?.trim()?.replace(honorific, "")?.lowercase()?.split(Regex("""[^\p{L}]+"""))?.filter { it.isNotEmpty() }
    return words?.sorted()?.takeIf { it.sumOf(String::length) >= MIN_NAME_LETTERS }
}

/** A phone number's line ("+91 ••••• •6411", "Mobile XXXXXX6411"): its last digits are not an account's. */
private val phoneLine = Regex("""(?i)(?:^|\s)\+\d{1,3}\b|\b(?:mobile|mob|phone|ph)\b""")

/** The account numbers [lines] show for the other side: none from a phone number, nor from a line with a UPI id. */
internal fun otherSideNumbers(lines: List<String>): List<MaskedNumber> =
    lines.filterNot { '@' in it || phoneLine.containsMatchIn(it) }.flatMap(SharedTransactionParser::maskedNumbers).distinct()

/** A shared payment found to be a transfer between the user's own accounts: where it came from and went to, as far as known. */
internal class SelfTransfer(
    val from: Account?,
    val to: Account?,
    /** What the message said, or null when only the other side's number told it. */
    val said: SelfTransferCue?,
    /** The money came into the user's own side: the other side is where it came from. */
    val incoming: Boolean,
    /** How the other side's account was found, for what the form says about it. */
    val other: AccountMatching.Pick,
) {
    /** The other side's account, wherever it stands in the transfer. */
    val otherAccount: Account? get() = if (incoming) from else to
}

internal object SelfTransfers {
    /**
     * The transfer [draft] is between the user's own accounts, or null when it isn't one: [own] is the account its own side was
     * found to be (null when that couldn't be told), and the other side is looked for among the rest of [accounts] - by the
     * numbers it shows, and by its bank only when the message says it is the user's (see the top of this file). A request
     * moves nothing, so it is never one.
     */
    fun find(
        draft: ParsedTransactionDraft,
        own: Account?,
        accounts: List<Account>,
        catalog: BankCatalog,
        cards: List<StoredCard>,
    ): SelfTransfer? {
        if (draft.status == SharedStatus.Requested) return null
        val said = draft.selfTransfer
        val others = accounts.filter { it.id != null && it.id != own?.id }
        val text = if (said != null) draft.otherAccountText.orEmpty() else ""
        val other = AccountMatching.find(text, null, null, others, catalog, draft.otherMaskedNumbers, cards)
        if (said == null && !(other.account != null && other.byNumber)) return null
        // The payer's receipt is the usual one: when it doesn't say which way, the money left the user's own side.
        val incoming = draft.type == TransactionType.Income
        return SelfTransfer(
            from = if (incoming) other.account else own,
            to = if (incoming) own else other.account,
            said = said,
            incoming = incoming,
            other = other,
        )
    }
}
