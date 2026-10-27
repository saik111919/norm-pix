package com.example.normpix.wallet

import androidx.compose.runtime.Immutable

/**
 * What kind of institution a bank is, as the Settings > Banks page groups them. The groups follow how India's banks are
 * usually told apart (public sector, private, small finance, payments, co-operative, regional rural, foreign) plus the banks
 * that have merged into another but still print on older cards and statements.
 */
enum class BankCategory(val label: String, val blurb: String) {
    PublicSector("Public sector banks", "Government-owned banks"),
    Private("Private sector banks", "Privately owned banks"),
    SmallFinance("Small finance banks", "Banks for small borrowers and savers"),
    Payments("Payments banks", "Savings and payments, no lending"),
    CoOperative("Co-operative banks", "Urban and state co-operative banks"),
    RegionalRural("Regional rural banks", "Gramin and grameena banks"),
    Foreign("Foreign banks", "Banks from abroad with branches or cards in India"),
    Earlier("Earlier names", "Banks that merged into another and may still be on old cards"),
}

/** A single bank the user can pick from the Add/Edit card bank dropdown. */
class BankDirectoryEntry(
    /** Stable lookup key. */
    val id: String,
    val name: String,
    val category: BankCategory,
    /** Other names people use for the bank ("SBI", "J&K Bank"); matched by [BankLogoResolver] alongside [name]. */
    val aliases: List<String> = emptyList(),
    /** The bank's bundled logo artwork; null when none is on file (a generic bank icon stands in). */
    val logo: BankLogoArt? = null,
    /** A line the Banks page says under the name ("Now part of Canara Bank"); null when the name says it all. */
    val note: String? = null,
) {
    /** The icon best suited to a small, roughly-square UI slot. */
    val compactIcon: String? get() = logo?.pick(compact = true, onDark = false)?.path
}

/**
 * A bank's bundled logo artwork: the full [wordmark] (mark and name) and the
 * compact, roughly square [symbol]; either may be missing, and each stands in
 * for the other. The `OnDark` versions are reversed-out artwork made for dark
 * backgrounds; without one, the regular full-color artwork goes on dark
 * surfaces over a light backing. An artwork file is an SVG, or a round full-color
 * picture (`.webp`, see [BankLogoFile.isPicture]) for the banks that have only that.
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
data class BankLogoFile(val path: String, val needsLightBacking: Boolean) {
    /** A round, full-color picture (square, raster) rather than an SVG. */
    val isPicture: Boolean get() = path.endsWith(".webp")
}

/**
 * Local, offline directory of banks offered in the card bank picker: India's
 * public-sector, private, small-finance, payments, co-operative and regional rural
 * banks, the foreign banks that issue or service cards in India, and the banks that
 * have since merged into another. Logos are the bundled `assets/banks/` set and the
 * round `assets/brandlogos/` pictures - no network lookup. Banks without a logo are
 * still listed so they stay searchable and recognizable; they show a generic bank icon.
 *
 * Adding a bank, or a logo for one, is a change to this list only: every
 * screen that shows a bank's logo finds it through [BankLogoResolver].
 *
 * A bank's [BankDirectoryEntry.id] is what accounts and cards keep: never change or reuse one. New banks are only ever added.
 */
object BankDirectory {
    private const val DIR = "banks"

    /** The bundled `<code>.svg` wordmark and `<code>_symbol.svg` symbol; codes are the bank's IFSC prefix. */
    private fun bundled(code: String) = BankLogoArt(wordmark = "$DIR/$code.svg", symbol = "$DIR/${code}_symbol.svg")

    /** A round full-color picture from the bundled brand set (`assets/brandlogos/<key>.webp`), which serves as both slots. */
    private fun picture(key: String) = BankLogoArt(symbol = "brandlogos/$key.webp")

    private val publicSector = listOf(
        BankDirectoryEntry("sbi", "State Bank of India", BankCategory.PublicSector, listOf("SBI", "State Bank"), bundled("sbin")),
        BankDirectoryEntry("pnb", "Punjab National Bank", BankCategory.PublicSector, listOf("PNB"), bundled("punb")),
        BankDirectoryEntry("bob", "Bank of Baroda", BankCategory.PublicSector, listOf("BOB", "BOBCARD"), bundled("barb")),
        BankDirectoryEntry("canara", "Canara Bank", BankCategory.PublicSector, listOf("Canara"), bundled("cnrb")),
        BankDirectoryEntry("union", "Union Bank of India", BankCategory.PublicSector, listOf("Union Bank"), bundled("ubin")),
        BankDirectoryEntry("boi", "Bank of India", BankCategory.PublicSector, listOf("BOI"), bundled("bkid")),
        BankDirectoryEntry("indian", "Indian Bank", BankCategory.PublicSector, logo = bundled("idib")),
        BankDirectoryEntry("central", "Central Bank of India", BankCategory.PublicSector, listOf("Central Bank"), bundled("cbin")),
        BankDirectoryEntry("iob", "Indian Overseas Bank", BankCategory.PublicSector, listOf("IOB"), bundled("ioba")),
        BankDirectoryEntry("uco", "UCO Bank", BankCategory.PublicSector, listOf("UCO"), bundled("ucba")),
        BankDirectoryEntry("bom", "Bank of Maharashtra", BankCategory.PublicSector, listOf("Mahabank"), bundled("mahb")),
        BankDirectoryEntry("psb", "Punjab & Sind Bank", BankCategory.PublicSector, listOf("Punjab Sind Bank"), bundled("psib")),
    )

    private val privateSector = listOf(
        BankDirectoryEntry("idbi", "IDBI Bank", BankCategory.Private, listOf("IDBI"), bundled("ibkl")),
        BankDirectoryEntry("hdfc", "HDFC Bank", BankCategory.Private, listOf("HDFC"), bundled("hdfc")),
        BankDirectoryEntry("icici", "ICICI Bank", BankCategory.Private, listOf("ICICI"), bundled("icic")),
        BankDirectoryEntry("axis", "Axis Bank", BankCategory.Private, listOf("Axis"), bundled("utib")),
        BankDirectoryEntry("kotak", "Kotak Mahindra Bank", BankCategory.Private, listOf("Kotak"), bundled("kkbk")),
        BankDirectoryEntry("indusind", "IndusInd Bank", BankCategory.Private, listOf("IndusInd", "Indus Ind"), bundled("indb")),
        BankDirectoryEntry("yes", "Yes Bank", BankCategory.Private, listOf("YES"), bundled("yesb")),
        BankDirectoryEntry("idfcfirst", "IDFC First Bank", BankCategory.Private, listOf("IDFC First", "IDFC"), bundled("idfb")),
        BankDirectoryEntry("federal", "Federal Bank", BankCategory.Private, listOf("Federal"), bundled("fdrl")),
        BankDirectoryEntry("southindian", "South Indian Bank", BankCategory.Private, logo = bundled("sibl")),
        BankDirectoryEntry("karnataka", "Karnataka Bank", BankCategory.Private, logo = bundled("karb")),
        BankDirectoryEntry("cityunion", "City Union Bank", BankCategory.Private, logo = bundled("ciub")),
        BankDirectoryEntry("dcb", "DCB Bank", BankCategory.Private, listOf("DCB"), bundled("dcbl")),
        BankDirectoryEntry("dhanalakshmi", "Dhanalakshmi Bank", BankCategory.Private, listOf("Dhanlaxmi Bank", "Dhanlaxmi", "Dhanalakshmi"), bundled("dlxb")),
        BankDirectoryEntry("karurvysya", "Karur Vysya Bank", BankCategory.Private, listOf("Karur Vysya", "KVB"), bundled("kvbl")),
        BankDirectoryEntry("tamilnadmercantile", "Tamilnad Mercantile Bank", BankCategory.Private, listOf("Tamilnad Mercantile", "TMB"), bundled("tmbl")),
        BankDirectoryEntry("rbl", "RBL Bank", BankCategory.Private, listOf("RBL", "Ratnakar Bank"), bundled("ratn")),
        BankDirectoryEntry("bandhan", "Bandhan Bank", BankCategory.Private, listOf("Bandhan"), bundled("bdbl")),
        BankDirectoryEntry("csb", "CSB Bank", BankCategory.Private, listOf("CSB", "Catholic Syrian Bank"), bundled("csbk")),
        BankDirectoryEntry("jk", "Jammu & Kashmir Bank", BankCategory.Private, listOf("J&K Bank", "JK Bank"), bundled("jaka")),
        BankDirectoryEntry("nainital", "Nainital Bank", BankCategory.Private, logo = bundled("ntbl")),
    )

    private val smallFinance = listOf(
        BankDirectoryEntry("au", "AU Small Finance Bank", BankCategory.SmallFinance, listOf("AU Small Finance", "AU Bank", "AU SFB"), bundled("aubl")),
        BankDirectoryEntry("ujjivan", "Ujjivan Small Finance Bank", BankCategory.SmallFinance, listOf("Ujjivan"), bundled("ujvn")),
        BankDirectoryEntry("esaf", "ESAF Small Finance Bank", BankCategory.SmallFinance, listOf("ESAF"), bundled("esmf")),
        BankDirectoryEntry("equitas", "Equitas Small Finance Bank", BankCategory.SmallFinance, listOf("Equitas"), picture("equitasbank")),
        BankDirectoryEntry("utkarsh", "Utkarsh Small Finance Bank", BankCategory.SmallFinance, listOf("Utkarsh"), picture("utkarshbank")),
        BankDirectoryEntry("capital", "Capital Small Finance Bank", BankCategory.SmallFinance, listOf("Capital SFB")),
        BankDirectoryEntry("jana", "Jana Small Finance Bank", BankCategory.SmallFinance, listOf("Jana SFB")),
        BankDirectoryEntry("northeast", "North East Small Finance Bank", BankCategory.SmallFinance, listOf("NESFB", "North East SFB")),
        BankDirectoryEntry("shivalik", "Shivalik Small Finance Bank", BankCategory.SmallFinance, listOf("Shivalik SFB")),
        BankDirectoryEntry("suryoday", "Suryoday Small Finance Bank", BankCategory.SmallFinance, listOf("Suryoday SFB", "Suryoday")),
        BankDirectoryEntry("unity", "Unity Small Finance Bank", BankCategory.SmallFinance, listOf("Unity SFB")),
    )

    private val payments = listOf(
        BankDirectoryEntry("airtel", "Airtel Payments Bank", BankCategory.Payments, listOf("Airtel"), bundled("airp")),
        BankDirectoryEntry("fino", "Fino Payments Bank", BankCategory.Payments, listOf("Fino"), bundled("fino")),
        BankDirectoryEntry("paytm", "Paytm Payments Bank", BankCategory.Payments, listOf("Paytm"), bundled("pytm")),
        BankDirectoryEntry("jio", "Jio Payments Bank", BankCategory.Payments, listOf("Jio"), bundled("jiop")),
        BankDirectoryEntry("indiapost", "India Post Payments Bank", BankCategory.Payments, listOf("India Post", "IPPB"), picture("ippb")),
        BankDirectoryEntry("nsdl", "NSDL Payments Bank", BankCategory.Payments, listOf("NSDL"), picture("nsdlpaymentsbank")),
    )

    private val coOperative = listOf(
        BankDirectoryEntry("saraswat", "Saraswat Co-operative Bank", BankCategory.CoOperative, listOf("Saraswat Bank", "Saraswat"), picture("saraswatbank")),
        BankDirectoryEntry("cosmos", "Cosmos Co-operative Bank", BankCategory.CoOperative, listOf("Cosmos Bank")),
        BankDirectoryEntry("keralabank", "Kerala Bank", BankCategory.CoOperative, listOf("Kerala State Co-operative Bank"), picture("kerelabank")),
        BankDirectoryEntry("abhyudaya", "Abhyudaya Co-operative Bank", BankCategory.CoOperative, listOf("Abhyudaya Bank")),
        BankDirectoryEntry("svc", "SVC Co-operative Bank", BankCategory.CoOperative, listOf("SVC Bank", "Shamrao Vithal Co-operative Bank")),
        BankDirectoryEntry("tjsb", "TJSB Sahakari Bank", BankCategory.CoOperative, listOf("TJSB Bank", "TJSB")),
        BankDirectoryEntry("bharatcoop", "Bharat Co-operative Bank", BankCategory.CoOperative, listOf("Bharat Co-op Bank")),
        BankDirectoryEntry("nkgsb", "NKGSB Co-operative Bank", BankCategory.CoOperative, listOf("NKGSB Bank", "NKGSB")),
        BankDirectoryEntry("kalupur", "Kalupur Commercial Co-operative Bank", BankCategory.CoOperative, listOf("Kalupur Bank")),
        BankDirectoryEntry("mehsana", "Mehsana Urban Co-operative Bank", BankCategory.CoOperative, listOf("Mehsana Urban Bank")),
        BankDirectoryEntry("janatasahakari", "Janata Sahakari Bank", BankCategory.CoOperative, listOf("Janata Sahakari Bank Pune")),
        BankDirectoryEntry("nutannagarik", "Nutan Nagarik Sahakari Bank", BankCategory.CoOperative, listOf("Nutan Nagarik Bank")),
        BankDirectoryEntry("apnasahakari", "Apna Sahakari Bank", BankCategory.CoOperative),
        BankDirectoryEntry("rajkotnagarik", "Rajkot Nagarik Sahakari Bank", BankCategory.CoOperative, listOf("RNSB")),
        BankDirectoryEntry("suratpeoples", "Surat People's Co-operative Bank", BankCategory.CoOperative, listOf("Surat Peoples Co-operative Bank")),
        BankDirectoryEntry("zoroastrian", "Zoroastrian Co-operative Bank", BankCategory.CoOperative, listOf("Zoroastrian Bank")),
        BankDirectoryEntry("mscbank", "Maharashtra State Co-operative Bank", BankCategory.CoOperative, listOf("MSC Bank")),
    )

    private val regionalRural = listOf(
        BankDirectoryEntry("apgvb", "Andhra Pradesh Grameena Vikas Bank", BankCategory.RegionalRural, listOf("APGVB")),
        BankDirectoryEntry("apgb", "Andhra Pragathi Grameena Bank", BankCategory.RegionalRural),
        BankDirectoryEntry("saptagiri", "Saptagiri Grameena Bank", BankCategory.RegionalRural),
        BankDirectoryEntry("telanganagb", "Telangana Grameena Bank", BankCategory.RegionalRural),
        BankDirectoryEntry("karnatakagramin", "Karnataka Gramin Bank", BankCategory.RegionalRural, listOf("Karnataka Grameena Bank")),
        BankDirectoryEntry("pragathikrishna", "Pragathi Krishna Gramin Bank", BankCategory.RegionalRural),
        BankDirectoryEntry("keralagramin", "Kerala Gramin Bank", BankCategory.RegionalRural, logo = picture("keralagraminbank")),
        BankDirectoryEntry("tamilnadugrama", "Tamil Nadu Grama Bank", BankCategory.RegionalRural),
        BankDirectoryEntry("puduvaibharathiar", "Puduvai Bharathiar Grama Bank", BankCategory.RegionalRural),
        BankDirectoryEntry("maharashtragramin", "Maharashtra Gramin Bank", BankCategory.RegionalRural),
        BankDirectoryEntry("barodaup", "Baroda UP Bank", BankCategory.RegionalRural),
        BankDirectoryEntry("barodagujarat", "Baroda Gujarat Gramin Bank", BankCategory.RegionalRural),
        BankDirectoryEntry("barodarajasthan", "Baroda Rajasthan Kshetriya Gramin Bank", BankCategory.RegionalRural),
        BankDirectoryEntry("rajasthanmarudhara", "Rajasthan Marudhara Gramin Bank", BankCategory.RegionalRural),
        BankDirectoryEntry("uttarbihar", "Uttar Bihar Gramin Bank", BankCategory.RegionalRural),
        BankDirectoryEntry("dakshinbihar", "Dakshin Bihar Gramin Bank", BankCategory.RegionalRural),
        BankDirectoryEntry("jharkhandrajya", "Jharkhand Rajya Gramin Bank", BankCategory.RegionalRural),
        BankDirectoryEntry("mpgramin", "Madhya Pradesh Gramin Bank", BankCategory.RegionalRural),
        BankDirectoryEntry("madhyanchal", "Madhyanchal Gramin Bank", BankCategory.RegionalRural),
        BankDirectoryEntry("chhattisgarhgramin", "Chhattisgarh Rajya Gramin Bank", BankCategory.RegionalRural),
        BankDirectoryEntry("prathamaup", "Prathama UP Gramin Bank", BankCategory.RegionalRural),
        BankDirectoryEntry("aryavart", "Aryavart Bank", BankCategory.RegionalRural),
        BankDirectoryEntry("uttarakhandgramin", "Uttarakhand Gramin Bank", BankCategory.RegionalRural),
        BankDirectoryEntry("himachalgramin", "Himachal Pradesh Gramin Bank", BankCategory.RegionalRural),
        BankDirectoryEntry("punjabgramin", "Punjab Gramin Bank", BankCategory.RegionalRural),
        BankDirectoryEntry("sarvaharyana", "Sarva Haryana Gramin Bank", BankCategory.RegionalRural),
        BankDirectoryEntry("jkgrameen", "J&K Grameen Bank", BankCategory.RegionalRural, listOf("Jammu and Kashmir Grameen Bank")),
        BankDirectoryEntry("ellaquai", "Ellaquai Dehati Bank", BankCategory.RegionalRural),
        BankDirectoryEntry("odishagramya", "Odisha Gramya Bank", BankCategory.RegionalRural),
        BankDirectoryEntry("utkalgrameen", "Utkal Grameen Bank", BankCategory.RegionalRural),
        BankDirectoryEntry("paschimbanga", "Paschim Banga Gramin Bank", BankCategory.RegionalRural),
        BankDirectoryEntry("bangiyagramin", "Bangiya Gramin Vikash Bank", BankCategory.RegionalRural),
        BankDirectoryEntry("assamgramin", "Assam Gramin Vikash Bank", BankCategory.RegionalRural),
        BankDirectoryEntry("tripuragramin", "Tripura Gramin Bank", BankCategory.RegionalRural),
        BankDirectoryEntry("meghalayarural", "Meghalaya Rural Bank", BankCategory.RegionalRural),
        BankDirectoryEntry("arunachalrural", "Arunachal Pradesh Rural Bank", BankCategory.RegionalRural),
        BankDirectoryEntry("manipurrural", "Manipur Rural Bank", BankCategory.RegionalRural),
        BankDirectoryEntry("mizoramrural", "Mizoram Rural Bank", BankCategory.RegionalRural),
        BankDirectoryEntry("nagalandrural", "Nagaland Rural Bank", BankCategory.RegionalRural),
    )

    private val foreign = listOf(
        BankDirectoryEntry("scb", "Standard Chartered Bank", BankCategory.Foreign, listOf("Standard Chartered", "StanChart", "SCB"), bundled("scbl")),
        BankDirectoryEntry("hsbc", "HSBC", BankCategory.Foreign, logo = picture("hsbcbank")),
        BankDirectoryEntry("citi", "Citibank", BankCategory.Foreign, listOf("Citi"), picture("citibank")),
        BankDirectoryEntry("dbs", "DBS Bank India", BankCategory.Foreign, listOf("DBS Bank", "DBS"), picture("dbsbank")),
        BankDirectoryEntry("deutsche", "Deutsche Bank", BankCategory.Foreign, listOf("Deutsche")),
        BankDirectoryEntry("barclays", "Barclays", BankCategory.Foreign),
        BankDirectoryEntry("boa", "Bank of America", BankCategory.Foreign, listOf("BofA", "BOA")),
        BankDirectoryEntry("amex", "American Express", BankCategory.Foreign, listOf("Amex"), picture("amex")),
        BankDirectoryEntry("jpmorgan", "JPMorgan Chase Bank", BankCategory.Foreign, listOf("JPMorgan Chase", "JP Morgan", "Chase Bank"), picture("chasebank")),
        BankDirectoryEntry("sbmindia", "SBM Bank India", BankCategory.Foreign, listOf("SBM Bank")),
        BankDirectoryEntry("emiratesnbd", "Emirates NBD", BankCategory.Foreign, listOf("Emirates NBD Bank"), picture("emiratesnbd")),
        BankDirectoryEntry("fab", "First Abu Dhabi Bank", BankCategory.Foreign, listOf("FAB Bank"), picture("fabbank")),
        BankDirectoryEntry("adcb", "Abu Dhabi Commercial Bank", BankCategory.Foreign, listOf("ADCB"), picture("adcb")),
        BankDirectoryEntry("mashreq", "Mashreq Bank", BankCategory.Foreign, listOf("Mashreq"), picture("mashreqbank")),
        BankDirectoryEntry("dohabank", "Doha Bank", BankCategory.Foreign),
        BankDirectoryEntry("qnb", "Qatar National Bank", BankCategory.Foreign, listOf("QNB")),
        BankDirectoryEntry("bbk", "Bank of Bahrain and Kuwait", BankCategory.Foreign, listOf("BBK")),
        BankDirectoryEntry("ceylon", "Bank of Ceylon", BankCategory.Foreign),
        BankDirectoryEntry("sonali", "Sonali Bank", BankCategory.Foreign),
        BankDirectoryEntry("bankofchina", "Bank of China", BankCategory.Foreign),
        BankDirectoryEntry("anz", "ANZ", BankCategory.Foreign, listOf("Australia and New Zealand Banking Group")),
        BankDirectoryEntry("scotiabank", "Scotiabank", BankCategory.Foreign, listOf("Bank of Nova Scotia")),
        BankDirectoryEntry("bnp", "BNP Paribas", BankCategory.Foreign),
        BankDirectoryEntry("creditagricole", "Credit Agricole", BankCategory.Foreign),
        BankDirectoryEntry("socgen", "Societe Generale", BankCategory.Foreign),
        BankDirectoryEntry("mizuho", "Mizuho Bank", BankCategory.Foreign),
        BankDirectoryEntry("mufg", "MUFG Bank", BankCategory.Foreign, listOf("Bank of Tokyo-Mitsubishi UFJ")),
        BankDirectoryEntry("smbc", "Sumitomo Mitsui Banking Corporation", BankCategory.Foreign, listOf("SMBC")),
        BankDirectoryEntry("kebhana", "KEB Hana Bank", BankCategory.Foreign, listOf("Hana Bank")),
        BankDirectoryEntry("shinhan", "Shinhan Bank", BankCategory.Foreign),
        BankDirectoryEntry("woori", "Woori Bank", BankCategory.Foreign),
        BankDirectoryEntry("ubs", "UBS", BankCategory.Foreign),
        BankDirectoryEntry("uob", "United Overseas Bank", BankCategory.Foreign, listOf("UOB"), picture("uob")),
        BankDirectoryEntry("bangkokbank", "Bangkok Bank", BankCategory.Foreign, logo = picture("bangkokbank")),
        BankDirectoryEntry("krungthai", "Krung Thai Bank", BankCategory.Foreign, logo = picture("krungthai")),
    )

    private val earlier = listOf(
        BankDirectoryEntry("syndicate", "Syndicate Bank", BankCategory.Earlier, note = "Now part of Canara Bank"),
        BankDirectoryEntry("allahabad", "Allahabad Bank", BankCategory.Earlier, note = "Now part of Indian Bank"),
        BankDirectoryEntry("andhra", "Andhra Bank", BankCategory.Earlier, note = "Now part of Union Bank of India"),
        BankDirectoryEntry("corporation", "Corporation Bank", BankCategory.Earlier, note = "Now part of Union Bank of India"),
        BankDirectoryEntry("obc", "Oriental Bank of Commerce", BankCategory.Earlier, note = "Now part of Punjab National Bank"),
        BankDirectoryEntry("united", "United Bank of India", BankCategory.Earlier, note = "Now part of Punjab National Bank"),
        BankDirectoryEntry("dena", "Dena Bank", BankCategory.Earlier, note = "Now part of Bank of Baroda"),
        BankDirectoryEntry("vijaya", "Vijaya Bank", BankCategory.Earlier, note = "Now part of Bank of Baroda"),
        BankDirectoryEntry("lakshmivilas", "Lakshmi Vilas Bank", BankCategory.Earlier, listOf("LVB"), note = "Now part of DBS Bank India"),
        BankDirectoryEntry("sbhyderabad", "State Bank of Hyderabad", BankCategory.Earlier, note = "Now part of State Bank of India"),
        BankDirectoryEntry("sbmysore", "State Bank of Mysore", BankCategory.Earlier, note = "Now part of State Bank of India"),
        BankDirectoryEntry("sbtravancore", "State Bank of Travancore", BankCategory.Earlier, note = "Now part of State Bank of India"),
        BankDirectoryEntry("sbpatiala", "State Bank of Patiala", BankCategory.Earlier, note = "Now part of State Bank of India"),
        BankDirectoryEntry("sbbikaner", "State Bank of Bikaner and Jaipur", BankCategory.Earlier, note = "Now part of State Bank of India"),
        BankDirectoryEntry("bharatiyamahila", "Bharatiya Mahila Bank", BankCategory.Earlier, note = "Now part of State Bank of India"),
        BankDirectoryEntry("fincare", "Fincare Small Finance Bank", BankCategory.Earlier, note = "Now part of AU Small Finance Bank"),
        BankDirectoryEntry("ingvysya", "ING Vysya Bank", BankCategory.Earlier, note = "Now part of Kotak Mahindra Bank"),
    )

    /** In the order a picker lists them: the big banks first, the long tail of small and earlier ones after. */
    val entries: List<BankDirectoryEntry> =
        publicSector + privateSector + smallFinance + payments + foreign + coOperative + regionalRural + earlier
}
