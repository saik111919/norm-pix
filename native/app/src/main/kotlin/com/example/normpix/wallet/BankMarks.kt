package com.example.normpix.wallet

import java.util.Locale

/**
 * The monogram a bank wears where it has no logo of its own: a few letters in the badge, so the banks without artwork can be told
 * apart at a glance instead of all being the same generic bank icon. Nothing here is a logo or a brand colour - only letters in
 * the app's own colours - so no trademark is drawn and nothing has to be fetched.
 *
 * Each built-in bank without artwork has its mark listed in [LISTED]: the abbreviation it is generally written with where there is
 * one ("TGB", "ICBC", "SBBJ"), otherwise the initials of its distinctive words. The list was first proposed by DeepSeek V4.1 Flash
 * and then checked here: every mark is 1 to 4 capital letters, no two are the same, and those too short to tell apart (a lone "UB",
 * "MP", "HP") were replaced by the conventional longer form. A bank the user adds, or one not listed, gets [initials] of its name.
 */
object BankMarks {
    /** The longest mark: more letters than this would not stay legible in a badge. */
    const val MAX_LENGTH = 4

    private val LISTED: Map<String, String> = mapOf(
        // Small finance banks
        "capital" to "CAP", "jana" to "JAN", "northeast" to "NESF", "shivalik" to "SHI", "suryoday" to "SUR", "unity" to "UNIT",
        // Co-operative banks
        "cosmos" to "COS", "abhyudaya" to "ABH", "svc" to "SVC", "tjsb" to "TJSB", "bharatcoop" to "BHA", "nkgsb" to "NKGS",
        "kalupur" to "KAL", "mehsana" to "MEH", "janatasahakari" to "JSB", "nutannagarik" to "NNSB", "apnasahakari" to "APN",
        "rajkotnagarik" to "RNSB", "suratpeoples" to "SPCB", "zoroastrian" to "ZOR", "mscbank" to "MSC",
        // Regional rural banks
        "apgvb" to "APG", "apgb" to "APGB", "saptagiri" to "SAP", "telanganagb" to "TGB", "karnatakagramin" to "KGB",
        "pragathikrishna" to "PKGB", "tamilnadugrama" to "TNGB", "puduvaibharathiar" to "PUD", "maharashtragramin" to "MGB",
        "barodaup" to "BUP", "barodagujarat" to "BGGB", "barodarajasthan" to "BRK", "rajasthanmarudhara" to "RMGB",
        "uttarbihar" to "UBGB", "dakshinbihar" to "DBGB", "jharkhandrajya" to "JRGB", "mpgramin" to "MPGB", "madhyanchal" to "MAD",
        "chhattisgarhgramin" to "CRGB", "prathamaup" to "PUGB", "aryavart" to "ARY", "uttarakhandgramin" to "UTT",
        "himachalgramin" to "HPGB", "punjabgramin" to "PGB", "sarvaharyana" to "SHGB", "jkgrameen" to "JKGB", "ellaquai" to "EDB",
        "odishagramya" to "OGB", "utkalgrameen" to "UGB", "paschimbanga" to "PBGB", "bangiyagramin" to "BGVB",
        "assamgramin" to "AGVB", "tripuragramin" to "TRI", "meghalayarural" to "MEG", "arunachalrural" to "ARU",
        "manipurrural" to "MAN", "mizoramrural" to "MIZ", "nagalandrural" to "NAG",
        // Foreign banks
        "deutsche" to "DEU", "barclays" to "BAR", "boa" to "BOFA", "sbmindia" to "SBM", "dohabank" to "DOH", "qnb" to "QNB",
        "bbk" to "BBK", "ceylon" to "CEY", "sonali" to "SON", "bankofchina" to "BOC", "anz" to "ANZ", "scotiabank" to "SCO",
        "bnp" to "BNP", "creditagricole" to "CA", "socgen" to "SG", "mizuho" to "MIZU", "mufg" to "MUFG", "smbc" to "SMBC",
        "kebhana" to "HAN", "shinhan" to "SHIN", "woori" to "WOO", "ubs" to "UBS", "icbc" to "ICBC", "maybank" to "MAY",
        "rabobank" to "RAB", "creditsuisse" to "CS",
        // Digital banks and apps
        "fimoney" to "FI", "unicards" to "UNI", "niyo" to "NIYO", "freo" to "FREO",
        // Earlier names
        "syndicate" to "SYN", "allahabad" to "ALL", "andhra" to "AND", "corporation" to "COR", "obc" to "OBC", "united" to "UTBI",
        "dena" to "DEN", "vijaya" to "VIJ", "lakshmivilas" to "LVB", "sbhyderabad" to "SBH", "sbmysore" to "SBMY",
        "sbtravancore" to "SBT", "sbpatiala" to "SBP", "sbbikaner" to "SBBJ", "bharatiyamahila" to "BMB", "fincare" to "FIN",
        "ingvysya" to "ING",
    )

    /** Every listed mark with the bank it is for, so a test can check them all. */
    internal val listed: Map<String, String> get() = LISTED

    /** The mark of the bank [id] named [name]: the listed one, or else the [initials] of the name. */
    fun of(id: String, name: String): String = LISTED[id] ?: initials(name)

    private val Generic = setOf(
        "bank", "banks", "banking", "co", "operative", "cooperative", "sahakari", "ltd", "limited", "pvt", "private", "of", "and", "the",
        "small", "finance", "sfb", "gramin", "grameena", "grameen", "grama", "gramya", "rural", "corporation",
    )

    /**
     * The first letters of the words that tell [name] apart from other banks, in capitals, up to [MAX_LENGTH]: "Federal Credit
     * Union" is "FCU"; a one-word name gives its first two or three letters ("Sonali" is "SON"). A name of nothing but common words
     * ("Gramin Bank") falls back to its own first letters rather than being empty.
     */
    fun initials(name: String): String {
        val words = name.split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotEmpty() }
        val distinctive = words.filter { it.lowercase(Locale.ROOT) !in Generic }.ifEmpty { words }
        if (distinctive.isEmpty()) return "?"
        val letters = if (distinctive.size == 1) {
            // One word: an all-capitals name is already an abbreviation ("UBS"), anything else gives its start.
            val word = distinctive.single()
            if (word.length <= MAX_LENGTH && word.all { it.isUpperCase() || it.isDigit() }) word else word.take(if (word.length >= 3) 3 else word.length)
        } else {
            distinctive.take(MAX_LENGTH).joinToString("") { it.first().toString() }
        }
        return letters.uppercase(Locale.ROOT).take(MAX_LENGTH)
    }
}
