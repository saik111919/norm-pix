package com.example.normpix.finance.share

import com.example.normpix.finance.domain.Category
import com.example.normpix.finance.domain.CategoryType
import com.example.normpix.finance.domain.TransactionType

/**
 * Which category a payment to a well-known Indian merchant usually belongs in - Swiggy is food delivery, Blinkit is
 * groceries, BESCOM is electricity - for a payee read from a shared message or picture. A plain table on the device, by
 * the names of the app's own categories: the user's own categories are matched by name (a sub-category when they have
 * it, else its parent), so a renamed or deleted category is simply not suggested. The user's own history for the payee
 * comes first (see `FinanceRepository.lastCategoryForPayee`); this is only for a payee they haven't filed yet.
 *
 * Add a merchant by adding its name to a rule. More specific rules come first ("swiggy instamart" before "swiggy").
 */
object MerchantCategories {
    private class Rule(val parent: String, val sub: String?, vararg val names: String)

    private val rules = listOf(
        // Groceries before food: the quick-commerce arms of food apps sell groceries.
        Rule("Groceries", null, "instamart", "blinkit", "zepto", "bigbasket", "bb now", "jiomart", "dmart", "avenue supermarts",
            "reliance fresh", "reliance smart", "more retail", "more supermarket", "spencers", "ratnadeep", "natures basket", "star bazaar",
            "kirana", "supermarket", "flipkart minutes", "amazon fresh", "metro cash"),
        Rule("Groceries", "Dairy & eggs", "country delight", "milkbasket", "akshayakalpa", "milk"),
        Rule("Groceries", "Meat & fish", "licious", "freshtohome", "fresh to home"),
        Rule("Subscriptions", "Netflix", "netflix"),
        Rule("Subscriptions", "Spotify", "spotify"),
        Rule("Subscriptions", "YouTube Music", "youtube music"),
        Rule("Subscriptions", "YouTube", "youtube", "google youtube"),
        Rule("Subscriptions", "Amazon Prime", "amazon prime", "prime video", "primevideo"),
        Rule("Subscriptions", "Disney+ Hotstar", "hotstar", "disney"),
        Rule("Subscriptions", "JioCinema", "jiocinema", "jio cinema", "jiohotstar"),
        Rule("Subscriptions", "SonyLIV", "sonyliv", "sony liv"),
        Rule("Subscriptions", "Zee5", "zee5"),
        Rule("Subscriptions", "ChatGPT", "openai", "chatgpt"),
        Rule("Subscriptions", "Claude", "anthropic", "claude"),
        Rule("Subscriptions", "Google One", "google one"),
        Rule("Subscriptions", "Microsoft 365", "microsoft"),
        Rule("Subscriptions", "Audible", "audible"),
        Rule("Food & dining", "Delivery apps", "swiggy", "zomato", "eatsure", "box8", "faasos", "rebel foods"),
        Rule("Food & dining", "Pizza", "dominos", "domino s", "pizza hut", "la pinoz", "pizza"),
        Rule("Food & dining", "Fast food", "mcdonald", "mcdonalds", "kfc", "burger king", "subway", "wow momo", "taco bell"),
        Rule("Food & dining", "Coffee & tea", "starbucks", "cafe coffee day", "ccd", "chaayos", "third wave", "blue tokai", "chai point"),
        Rule("Food & dining", "Desserts", "baskin robbins", "naturals ice cream", "kwality walls"),
        Rule("Food & dining", "Restaurants", "barbeque nation", "haldiram", "restaurant", "hotel saravana", "biryani", "dhaba", "cafe"),
        // Before transport's "metro".
        Rule("Shopping", "Footwear", "bata", "metro shoes", "puma", "nike", "adidas"),
        Rule("Transport", "Cabs & rideshare", "uber", "ola", "olacabs", "ola cabs", "rapido", "blusmart", "namma yatri", "meru"),
        Rule("Transport", "Trains", "irctc", "indian railways"),
        Rule("Transport", "Bus & metro", "redbus", "abhibus", "dmrc", "bmrcl", "metro", "bmtc", "best bus", "ksrtc", "tsrtc", "apsrtc"),
        Rule("Transport", "Tolls", "fastag", "nhai", "toll"),
        Rule("Transport", "Petrol & diesel", "indian oil", "iocl", "hpcl", "hindustan petroleum", "bpcl", "bharat petroleum", "shell",
            "nayara", "jio bp", "petrol", "fuel", "filling station"),
        Rule("Transport", "EV charging", "statiq", "tata power ez charge", "ather grid"),
        Rule("Transport", "Parking", "parking"),
        Rule("Travel", "Flights", "indigo", "interglobe", "air india", "akasa", "spicejet", "vistara"),
        Rule("Travel", "Hotels", "oyo", "treebo", "fabhotels", "airbnb", "taj hotels", "marriott"),
        Rule("Travel", "Car rental", "zoomcar", "revv"),
        Rule("Travel", null, "makemytrip", "goibibo", "cleartrip", "yatra", "ixigo", "easemytrip"),
        Rule("Bills & utilities", "DTH / cable", "tata play", "tatasky", "tata sky", "dish tv", "d2h", "sun direct", "airtel dth"),
        Rule("Bills & utilities", "Broadband", "act fibernet", "hathway", "excitel", "jiofiber", "jio fiber", "airtel xstream", "you broadband"),
        Rule("Bills & utilities", "Mobile recharge", "airtel", "jio", "reliance jio", "vodafone", "vodafone idea", "bsnl", "mtnl"),
        Rule("Bills & utilities", "Electricity", "bescom", "tneb", "tangedco", "msedcl", "mahavitaran", "tata power", "adani electricity",
            "bses", "cesc", "torrent power", "kseb", "apspdcl", "tsspdcl", "uppcl", "pspcl", "wbsedcl", "electricity"),
        Rule("Bills & utilities", "Gas / cylinder", "indane", "hp gas", "bharat gas", "bharatgas", "mahanagar gas", "igl", "gail gas", "lpg"),
        Rule("Bills & utilities", "Water", "bwssb", "water board", "jal board"),
        Rule("Bills & utilities", "Society maintenance", "mygate", "nobrokerhood", "adda", "maintenance"),
        Rule("Bills & utilities", "Insurance premium", "lic", "life insurance", "insurance", "policybazaar", "acko", "digit insurance"),
        Rule("Rent & housing", "Rent", "nobroker", "rent", "house rent"),
        Rule("Rent & housing", "Furniture", "ikea", "pepperfry", "urban ladder", "wakefit"),
        Rule("Rent & housing", "Repairs", "urban company", "urbanclap"),
        Rule("Health", "Pharmacy", "apollo pharmacy", "pharmeasy", "netmeds", "tata 1mg", "1mg", "medplus", "pharmacy", "medical", "chemist"),
        Rule("Health", "Lab tests", "thyrocare", "dr lal", "lal pathlabs", "metropolis", "healthians", "redcliffe"),
        Rule("Health", "Doctor", "practo", "clinic", "doctor"),
        Rule("Health", "Hospital", "apollo hospitals", "fortis", "manipal hospital", "max healthcare", "narayana health", "hospital"),
        Rule("Health", "Glasses", "lenskart", "titan eye"),
        Rule("Entertainment", "Movies", "bookmyshow", "pvr", "inox", "cinepolis", "district"),
        Rule("Entertainment", "Games", "dream11", "mpl", "my11circle", "steam", "playstation", "xbox"),
        Rule("Entertainment", "Concerts & events", "paytm insider", "insider", "skillbox"),
        Rule("Education", "Online courses", "byjus", "byju", "unacademy", "vedantu", "physics wallah", "coursera", "udemy", "upgrad", "scaler"),
        Rule("Education", "School fees", "school", "college", "university"),
        Rule("Shopping", "Clothing", "myntra", "ajio", "max fashion", "westside", "pantaloons", "zudio", "zara", "uniqlo", "trends"),
        Rule("Shopping", "Cosmetics", "nykaa", "purplle", "sugar cosmetics"),
        Rule("Shopping", "Electronics", "croma", "reliance digital", "vijay sales", "apple store"),
        Rule("Shopping", "Jewelry", "tanishq", "caratlane", "kalyan jewellers", "malabar gold"),
        Rule("Shopping", "Online orders", "amazon", "flipkart", "meesho", "snapdeal", "tata cliq", "shopsy"),
    )

    /** Every category a rule files into, by name: (top-level, sub-category or null). */
    internal val targets: List<Pair<String, String?>> get() = rules.map { it.parent to it.sub }

    /** The words of [name], lower case, as one spaced string: "SWIGGY.IN@AXISBANK" -> " swiggy in axisbank ". */
    private fun words(name: String): String =
        " " + name.lowercase().replace(Regex("""[^\p{L}\p{N}]+"""), " ").trim() + " "

    /**
     * The id of the user's category for a [type] to [payee], or null when the payee is no merchant this table knows, the
     * user has no such category, or it isn't an expense (income from a merchant - a refund - is filed by the user).
     */
    fun categoryFor(payee: String?, type: TransactionType?, categories: List<Category>): Long? {
        if (payee.isNullOrBlank() || type != TransactionType.Expense) return null
        val name = words(payee)
        val squashed = name.replace(" ", "")
        val rule = rules.firstOrNull { rule ->
            rule.names.any { known ->
                // Whole words, or for a long distinctive name also run together ("swiggyinstamart", "bigbasketnow").
                name.contains(" $known ") || (known.length >= 6 && !known.contains(' ') && squashed.contains(known))
            }
        } ?: return null
        val usable = categories.filter { it.type == CategoryType.Expense && !it.archived }
        val parent = usable.firstOrNull { it.parentId == null && it.name.equals(rule.parent, ignoreCase = true) } ?: return null
        val sub = rule.sub?.let { wanted -> usable.firstOrNull { it.parentId == parent.id && it.name.equals(wanted, ignoreCase = true) } }
        return (sub ?: parent).id
    }
}
