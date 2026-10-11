package com.example.normpix.wallet

import androidx.compose.runtime.Immutable

/** A single bank the user can pick from the Add/Edit card bank dropdown. */
class BankDirectoryEntry(
    /** Stable lookup key. */
    val id: String,
    val name: String,
    /** Other names people use for the bank ("SBI", "J&K Bank"); matched by [BankLogoResolver] alongside [name]. */
    val aliases: List<String> = emptyList(),
    /** The bank's bundled logo artwork; null when none is on file (a generic bank icon stands in). */
    val logo: BankLogoArt? = null,
) {
    /** The icon best suited to a small, roughly-square UI slot. */
    val compactIcon: String? get() = logo?.pick(compact = true, onDark = false)?.path
}

/**
 * A bank's bundled logo artwork: the full [wordmark] (mark and name) and the
 * compact, roughly square [symbol]; either may be missing, and each stands in
 * for the other. The `OnDark` versions are reversed-out artwork made for dark
 * backgrounds; without one, the regular full-color artwork goes on dark
 * surfaces over a light backing.
 */
@Immutable
data class BankLogoArt(
    val wordmark: String? = null,
    val symbol: String? = null,
    val wordmarkOnDark: String? = null,
    val symbolOnDark: String? = null,
) {
    /** The file to draw for a [compact] (symbol) or full (wordmark) slot, on a dark surface or not. */
    fun pick(compact: Boolean, onDark: Boolean): BankLogoFile? {
        val preferred = if (compact) listOf(symbol to symbolOnDark, wordmark to wordmarkOnDark) else listOf(wordmark to wordmarkOnDark, symbol to symbolOnDark)
        for ((regular, reversed) in preferred) {
            if (onDark && reversed != null) return BankLogoFile(reversed, needsLightBacking = false)
            if (regular != null) return BankLogoFile(regular, needsLightBacking = onDark)
        }
        return null
    }
}

/** One logo file to draw; [needsLightBacking] when it is full-color artwork going on a dark surface. */
data class BankLogoFile(val path: String, val needsLightBacking: Boolean)

/**
 * Local, offline directory of banks offered in the card bank picker: India's
 * public-sector, private, small-finance and payments banks, plus the foreign
 * banks that issue or service cards in India. Logos are the bundled
 * `assets/banks/` set - no network lookup. Banks without a logo are still
 * listed so they stay searchable and recognizable; they show a generic bank icon.
 *
 * Adding a bank, or a logo for one, is a change to this list only: every
 * screen that shows a bank's logo finds it through [BankLogoResolver].
 */
object BankDirectory {
    private const val DIR = "banks"

    /** The bundled `<code>.svg` wordmark and `<code>_symbol.svg` symbol; codes are the bank's IFSC prefix. */
    private fun bundled(code: String) = BankLogoArt(wordmark = "$DIR/$code.svg", symbol = "$DIR/${code}_symbol.svg")

    val entries: List<BankDirectoryEntry> = listOf(
        BankDirectoryEntry("sbi", "State Bank of India", listOf("SBI", "State Bank"), bundled("sbin")),
        BankDirectoryEntry("pnb", "Punjab National Bank", listOf("PNB"), bundled("punb")),
        BankDirectoryEntry("bob", "Bank of Baroda", listOf("BOB", "BOBCARD"), bundled("barb")),
        BankDirectoryEntry("canara", "Canara Bank", listOf("Canara"), bundled("cnrb")),
        BankDirectoryEntry("union", "Union Bank of India", listOf("Union Bank"), bundled("ubin")),
        BankDirectoryEntry("boi", "Bank of India", listOf("BOI"), bundled("bkid")),
        BankDirectoryEntry("indian", "Indian Bank", logo = bundled("idib")),
        BankDirectoryEntry("central", "Central Bank of India", listOf("Central Bank"), bundled("cbin")),
        BankDirectoryEntry("iob", "Indian Overseas Bank", listOf("IOB"), bundled("ioba")),
        BankDirectoryEntry("uco", "UCO Bank", listOf("UCO"), bundled("ucba")),
        BankDirectoryEntry("bom", "Bank of Maharashtra", listOf("Mahabank"), bundled("mahb")),
        BankDirectoryEntry("psb", "Punjab & Sind Bank", listOf("Punjab Sind Bank"), bundled("psib")),
        BankDirectoryEntry("idbi", "IDBI Bank", listOf("IDBI"), bundled("ibkl")),
        BankDirectoryEntry("hdfc", "HDFC Bank", listOf("HDFC"), bundled("hdfc")),
        BankDirectoryEntry("icici", "ICICI Bank", listOf("ICICI"), bundled("icic")),
        BankDirectoryEntry("axis", "Axis Bank", listOf("Axis"), bundled("utib")),
        BankDirectoryEntry("kotak", "Kotak Mahindra Bank", listOf("Kotak"), bundled("kkbk")),
        BankDirectoryEntry("indusind", "IndusInd Bank", listOf("IndusInd", "Indus Ind"), bundled("indb")),
        BankDirectoryEntry("yes", "Yes Bank", listOf("YES"), bundled("yesb")),
        BankDirectoryEntry("idfcfirst", "IDFC First Bank", listOf("IDFC First", "IDFC"), bundled("idfb")),
        BankDirectoryEntry("federal", "Federal Bank", listOf("Federal"), bundled("fdrl")),
        BankDirectoryEntry("southindian", "South Indian Bank", logo = bundled("sibl")),
        BankDirectoryEntry("karnataka", "Karnataka Bank", logo = bundled("karb")),
        BankDirectoryEntry("cityunion", "City Union Bank", logo = bundled("ciub")),
        BankDirectoryEntry("dcb", "DCB Bank", listOf("DCB"), bundled("dcbl")),
        BankDirectoryEntry("dhanalakshmi", "Dhanalakshmi Bank", listOf("Dhanlaxmi Bank", "Dhanlaxmi", "Dhanalakshmi"), bundled("dlxb")),
        BankDirectoryEntry("karurvysya", "Karur Vysya Bank", listOf("Karur Vysya", "KVB"), bundled("kvbl")),
        BankDirectoryEntry("tamilnadmercantile", "Tamilnad Mercantile Bank", listOf("Tamilnad Mercantile", "TMB"), bundled("tmbl")),
        BankDirectoryEntry("rbl", "RBL Bank", listOf("RBL", "Ratnakar Bank"), bundled("ratn")),
        BankDirectoryEntry("bandhan", "Bandhan Bank", listOf("Bandhan"), bundled("bdbl")),
        BankDirectoryEntry("csb", "CSB Bank", listOf("CSB", "Catholic Syrian Bank"), bundled("csbk")),
        BankDirectoryEntry("jk", "Jammu & Kashmir Bank", listOf("J&K Bank", "JK Bank"), bundled("jaka")),
        BankDirectoryEntry("nainital", "Nainital Bank", logo = bundled("ntbl")),
        BankDirectoryEntry("au", "AU Small Finance Bank", listOf("AU Small Finance", "AU Bank", "AU SFB"), bundled("aubl")),
        BankDirectoryEntry("ujjivan", "Ujjivan Small Finance Bank", listOf("Ujjivan"), bundled("ujvn")),
        BankDirectoryEntry("esaf", "ESAF Small Finance Bank", listOf("ESAF"), bundled("esmf")),
        BankDirectoryEntry("equitas", "Equitas Small Finance Bank", listOf("Equitas")),
        BankDirectoryEntry("utkarsh", "Utkarsh Small Finance Bank", listOf("Utkarsh")),
        BankDirectoryEntry("airtel", "Airtel Payments Bank", listOf("Airtel"), bundled("airp")),
        BankDirectoryEntry("fino", "Fino Payments Bank", listOf("Fino"), bundled("fino")),
        BankDirectoryEntry("paytm", "Paytm Payments Bank", listOf("Paytm"), bundled("pytm")),
        BankDirectoryEntry("jio", "Jio Payments Bank", listOf("Jio"), bundled("jiop")),
        BankDirectoryEntry("indiapost", "India Post Payments Bank", listOf("India Post", "IPPB")),
        BankDirectoryEntry("nsdl", "NSDL Payments Bank", listOf("NSDL")),
        BankDirectoryEntry("scb", "Standard Chartered Bank", listOf("Standard Chartered", "StanChart", "SCB"), bundled("scbl")),
        BankDirectoryEntry("hsbc", "HSBC"),
        BankDirectoryEntry("citi", "Citibank", listOf("Citi")),
        BankDirectoryEntry("dbs", "DBS Bank India", listOf("DBS Bank", "DBS")),
        BankDirectoryEntry("deutsche", "Deutsche Bank", listOf("Deutsche")),
        BankDirectoryEntry("barclays", "Barclays"),
        BankDirectoryEntry("boa", "Bank of America", listOf("BofA", "BOA")),
        BankDirectoryEntry("amex", "American Express", listOf("Amex")),
    )
}
