package com.example.normpix.wallet

/**
 * The names of the card products a bank offers ("Regalia Gold", "Magnus"), to suggest when a card is saved: a bank's cards each have
 * a name of their own, and the card face shows the one the user picks (or types - any name is fine, these only save the typing).
 *
 * Only names are listed. The lists are for the six largest issuers, and each name was checked against the issuer's page on CardInsider
 * (2026-10-31) - the names exist there; what a card looks like was not, and nothing here pretends to say (a face is still the bank's).
 * Other banks have no suggestions yet; the field works for them all the same. A name is the user's own text and is stored with the card
 * inside the vault like its other fields.
 */
object CardNames {
    /** The longest card name kept: it has to fit on the face. */
    const val MAX_LENGTH = 28

    private val byBank: Map<String, List<String>> = mapOf(
        "hdfc" to listOf("Regalia Gold", "MoneyBack+", "Diners Club Black Metal", "Infinia Metal", "Tata Neu Infinity", "Marriott Bonvoy"),
        "sbi" to listOf("SimplyCLICK", "SimplySAVE", "Prime", "BPCL Octane", "Tata Neu Infinity", "Flipkart", "Cashback", "Elite", "Aurum"),
        "icici" to listOf("Coral", "Sapphiro", "Rubyx", "Emeralde Private Metal", "HPCL Super Saver", "MakeMyTrip", "Adani One", "Amazon Pay"),
        "axis" to listOf("ACE", "Magnus", "Privilege", "SELECT", "Airtel Axis Bank", "Horizon", "My Zone"),
        "kotak" to listOf("League Platinum", "Zen Signature", "White Reserve", "White", "Mojo Platinum", "Air+", "811 #DreamDifferent"),
        "indusind" to listOf("Legend", "Pinnacle World", "Indulge", "Tiger", "EazyDiner", "Avios Visa Infinite", "Pioneer Heritage", "Nexxt"),
    )

    /** The card names suggested for the bank [bankKey] (a directory id); none for a bank that has no list or for none at all. */
    fun suggestionsFor(bankKey: String?): List<String> = bankKey?.let(byBank::get).orEmpty()

    /** [text] as it is kept: spaces trimmed and collapsed, cut to [MAX_LENGTH]. */
    fun clean(text: String): String = text.trim().replace(Regex("\\s+"), " ").take(MAX_LENGTH).trim()

    /** Every bank that has suggestions, for tests. */
    internal val banksWithNames: Set<String> get() = byBank.keys
}
