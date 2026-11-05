package com.example.normpix.reminders

/** An app on this phone that can be opened from the launcher: what the "excluded apps" list is made of. */
data class InstalledApp(val packageName: String, val label: String)

/**
 * Which apps the floating reminder card is kept away from at first. Turning the feature on pre-picks the banking, payment and
 * private-looking apps found on this phone, so the card never lands over one by default; the user changes the list freely.
 *
 * This is a guess from names, made on the device and never sent anywhere: an app is suggested when its name or package holds a
 * word that money and sign-in apps carry, or it is one of a few well-known payment apps. A name that is wrong or missing only means
 * that app is not pre-picked - it can be added by hand, and the card never appears over an app that is in the list.
 */
object ExcludedApps {
    /** Words in an app's name or package that mark banking, payments, investing, or a place codes and passwords live. */
    private val WORDS = listOf(
        "bank", "upi", "bhim", "wallet", "finance", "money", "payment", "paytm", "phonepe", "gpay", "googlepay", "paisa",
        "paypal", "invest", "demat", "broker", "zerodha", "groww", "insurance", "loan", "credit", "mutual",
        "authenticator", "password", "passkey", "vault", "keepass", "bitwarden", "lastpass", "1password",
    )

    /** Well-known payment apps by package name. */
    private val KNOWN = setOf(
        "com.google.android.apps.nbu.paisa.user", // Google Pay
        "com.phonepe.app", // PhonePe
        "net.one97.paytm", // Paytm
        "in.org.npci.upiapp", // BHIM
        "com.dreamplug.androidapp", // CRED
    )

    /** Short words that mean something only as a whole word of a name ("Pay", "CRED", "OTP"), not inside others ("Spay", "Incredible"). */
    private val WHOLE_WORDS = Regex("(^|[^a-z])(pay|cred|otp)([^a-z]|$)")

    fun suggested(apps: List<InstalledApp>): Set<String> = apps.filter { isSensitive(it) }.map { it.packageName }.toSet()

    fun isSensitive(app: InstalledApp): Boolean {
        if (app.packageName in KNOWN) return true
        val label = app.label.lowercase()
        val pkg = app.packageName.lowercase()
        return WORDS.any { it in label || it in pkg } || WHOLE_WORDS.containsMatchIn(label)
    }
}
