package com.example.normpix.wallet

/**
 * Card-number digit-length rules, scoped to Indian banks only - Visa /
 * Mastercard / RuPay cards issued by these banks are all 16 digits, so
 * recognizing the bank lets the form enforce the exact length instead of the
 * generic 12-19 digit range. Left unscoped for everything else, since length
 * genuinely varies more internationally (Amex 15, Diners Club 14, ...) and
 * guessing wrong there would be worse than not validating at all.
 */
object IndianBankCardLength {
    private val indianBanks = listOf(
        "hdfc", "icici", "state bank of india", "sbi", "axis", "kotak", "punjab national bank", "pnb",
        "bank of baroda", "canara", "union bank", "bank of india", "indian bank", "central bank",
        "indian overseas", "uco", "maharashtra", "punjab & sind", "punjab and sind", "idbi", "idfc",
        "federal bank", "south indian", "karnataka bank", "city union", "dcb", "dhanlaxmi", "dhanalakshmi",
        "karur vysya", "tamilnad", "rbl", "bandhan", "csb", "jammu", "j&k", "indusind", "yes bank",
        "au small finance", "au bank", "ujjivan", "esaf", "airtel", "fino", "paytm", "jio payments", "nainital",
    )

    /** The exact digit count expected for [bankName], or null if the bank isn't a recognized Indian bank. */
    fun digitsFor(bankName: String): Int? {
        val normalized = bankName.trim().lowercase()
        return if (indianBanks.any { normalized.contains(it) }) 16 else null
    }
}
