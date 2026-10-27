package com.example.normpix.banks

import androidx.compose.runtime.Immutable
import com.example.normpix.finance.domain.CustomBank
import com.example.normpix.wallet.BankCategory
import com.example.normpix.wallet.BankDirectory
import com.example.normpix.wallet.BankLogoArt
import com.example.normpix.wallet.BankLogoResolver

/**
 * A bank an account or a card can belong to: one of the built-in [BankDirectory]'s, or one the user added.
 * Accounts and cards point at a bank by its [key]; the bank is the relationship between them, nothing more -
 * a transaction still belongs to the account (or card) it was made with, whatever bank that is linked to today.
 */
@Immutable
data class Bank(
    /** Stable reference: the directory id of a built-in bank ("hdfc"), or `custom-...` for one the user added. */
    val key: String,
    val name: String,
    /** The bank's bundled artwork; null for banks with none on file (a generic bank icon stands in). */
    val logo: BankLogoArt?,
    val builtIn: Boolean,
    /** What kind of bank it is, as the Banks page groups them; null for one the user added. */
    val category: BankCategory? = null,
    /** A line the Banks page says under the name ("Now part of Canara Bank"), where the name needs one. */
    val note: String? = null,
    /** The other names the bank is known by, as the directory lists them; none for one the user added. */
    val aliases: List<String> = emptyList(),
)

/**
 * Every bank the app knows: the built-in directory (names, aliases and logos, the one place they are defined)
 * plus the banks the user added. This is the single source of truth for bank pickers, filters and logos.
 *
 * Names are compared the way [BankLogoResolver] compares them: case, punctuation and legal suffixes ("Ltd") don't
 * matter, so adding "hdfc bank ltd" finds HDFC Bank instead of creating a second one.
 */
class BankCatalog(custom: List<CustomBank> = emptyList()) {
    private val customBanks: List<Bank> = custom.map { Bank(it.key, it.name, logo = null, builtIn = false) }

    /** Built-in banks in directory order, then the user's own, alphabetically. */
    val all: List<Bank> = BUILT_IN + customBanks.sortedBy { it.name.lowercase() }

    private val byKey: Map<String, Bank> = all.associateBy { it.key }

    /** Each way a bank is written (its name, its aliases) as compared, with the bank it names. */
    private val names: List<Pair<String, Bank>> = buildList {
        for (bank in BUILT_IN) for (written in BUILT_IN_NAMES.getValue(bank.key)) add(written to bank)
        for (bank in customBanks) BankLogoResolver.normalize(bank.name).takeIf { it.isNotEmpty() }?.let { add(it to bank) }
    }

    fun byKey(key: String?): Bank? = key?.let(byKey::get)

    /** The bank named exactly [name] (by its name or an alias, ignoring case, punctuation and "Ltd"), or null. */
    fun exact(name: String): Bank? {
        val wanted = BankLogoResolver.normalize(name)
        if (wanted.isEmpty()) return null
        return names.firstOrNull { it.first == wanted }?.second
    }

    /**
     * The bank [name] refers to: an exact match, else a built-in bank mentioned in it ("HDFC Bank, Fort branch").
     * For free-typed names saved before banks were shared; for anything new use [exact].
     */
    fun resolve(name: String): Bank? =
        exact(name) ?: BankLogoResolver.bankFor(name)?.let { byKey[it.id] }

    /**
     * The bank whose name an account's [name] *starts with* ("HDFC Savings", "ICICI Credit Card", "Paytm Wallet"). Only a
     * leading bank name counts, so "Savings for Axis bike" is nobody's account; the longest name wins ("State Bank of India
     * Salary" is SBI, not Bank of India).
     */
    fun leading(name: String): Bank? {
        val words = BankLogoResolver.normalize(name)
        if (words.isEmpty()) return null
        return names.filter { (written, _) -> words == written || words.startsWith("$written ") }
            .maxByOrNull { it.first.length }?.second
    }

    /** The banks as a picker lists them: the ones already in use first (so the user's own banks are one tap away), then the rest. */
    fun ordered(usedKeys: Set<String>): List<Bank> {
        val used = all.filter { it.key in usedKeys }.sortedBy { it.name.lowercase() }
        return used + all.filter { it.key !in usedKeys }
    }

    /** The banks matching what the user has typed: their name or any alias contains it. */
    fun matching(query: String, usedKeys: Set<String> = emptySet()): List<Bank> {
        val wanted = query.trim().lowercase()
        val ordered = ordered(usedKeys)
        if (wanted.isEmpty()) return ordered
        val normalized = BankLogoResolver.normalize(query)
        return ordered.filter { bank ->
            bank.name.lowercase().contains(wanted) ||
                names.any { (written, owner) -> owner === bank && normalized.isNotEmpty() && written.contains(normalized) }
        }
    }

    companion object {
        private val BUILT_IN: List<Bank> = BankDirectory.entries.map {
            Bank(it.id, it.name, it.logo, builtIn = true, category = it.category, note = it.note, aliases = it.aliases)
        }

        private val BUILT_IN_NAMES: Map<String, List<String>> = BankDirectory.entries.associate { entry ->
            entry.id to (listOf(entry.name) + entry.aliases).map(BankLogoResolver::normalize).filter { it.isNotEmpty() }.distinct()
        }

        /** The built-in banks only, for the places that have no user banks to consult (and the migration). */
        val BuiltIn: BankCatalog by lazy { BankCatalog() }

        /** A new key for a bank the user adds. */
        fun newCustomKey(): String = "custom-" + java.util.UUID.randomUUID()
    }
}
