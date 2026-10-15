package com.example.normpix.finance.data

/** A ready-made sub-category: its name, and its icon - an icon key of the category icon set, or "emoji:<one emoji>". */
data class SuggestedSubCategory(val name: String, val icon: String)

/**
 * Ready-made sub-categories for the seeded top-level categories, keyed by the exact name each one has in `defaultCategories`.
 * An icon is a key of the category icon set (`categoryIcons`, plus `extraCategoryIcons` and `brandCategoryIcons`) where one fits, else "emoji:<emoji>".
 * Names are unique across the whole map and never repeat a top-level name, so adding them can't double anything up.
 */
object CategorySuggestions {
    /**
     * The top-level categories added to `defaultCategories` after the first release: a database filled before them is
     * given them once, with their sub-categories here (FinanceDatabase version 11). A family added later goes here too, with
     * a database version that gives it to the phones that already have NormPix - seeding alone only reaches new installs.
     */
    val addedLater: Set<String> = setOf(
        "Personal care", "Fitness", "Events", "Kids", "Pets", "Insurance", "Loans & EMI", "Taxes", "Investments",
        "Family support", "Donations",
    )

    /** Of [addedLater], the names the first release seeded as sub-categories (under Groceries and Other). */
    val formerSubCategories: Set<String> = setOf("Personal care", "Donations")

    val byParent: Map<String, List<SuggestedSubCategory>> = linkedMapOf(
        "Food & dining" to listOf(
            SuggestedSubCategory("Restaurants", "restaurant"),
            SuggestedSubCategory("Coffee & tea", "coffee"),
            SuggestedSubCategory("Fast food", "fast_food"),
            SuggestedSubCategory("Takeaway", "takeaway"),
            SuggestedSubCategory("Delivery apps", "delivery"),
            SuggestedSubCategory("Street food", "emoji:🌮"),
            SuggestedSubCategory("Desserts", "dessert"),
            SuggestedSubCategory("Pizza", "pizza"),
            SuggestedSubCategory("Wine & beer", "wine"),
            SuggestedSubCategory("Breakfast", "emoji:🥞"),
        ),
        "Groceries" to listOf(
            SuggestedSubCategory("Fruits & vegetables", "fruit_veg"),
            SuggestedSubCategory("Dairy & eggs", "dairy"),
            SuggestedSubCategory("Staples & grains", "emoji:🌾"),
            SuggestedSubCategory("Meat & fish", "meat"),
            SuggestedSubCategory("Packaged snacks", "snacks"),
            SuggestedSubCategory("Beverages", "emoji:🧃"),
            SuggestedSubCategory("Bread & bakery", "bakery"),
            SuggestedSubCategory("Spices & condiments", "emoji:🧂"),
            SuggestedSubCategory("Household supplies", "cleaning"),
            SuggestedSubCategory("Toiletries", "personal"),
        ),
        "Transport" to listOf(
            SuggestedSubCategory("Petrol & diesel", "fuel"),
            SuggestedSubCategory("Bus & metro", "bus"),
            SuggestedSubCategory("Trains", "train"),
            SuggestedSubCategory("Cabs & rideshare", "taxi"),
            SuggestedSubCategory("Auto rickshaw", "emoji:🛺"),
            SuggestedSubCategory("Parking", "parking"),
            SuggestedSubCategory("Tolls", "toll"),
            SuggestedSubCategory("Bike & scooter", "scooter"),
            SuggestedSubCategory("Car repair", "car_repair"),
            SuggestedSubCategory("Car wash", "car_wash"),
            SuggestedSubCategory("EV charging", "ev_charging"),
        ),
        "Shopping" to listOf(
            SuggestedSubCategory("Clothing", "clothing"),
            SuggestedSubCategory("Footwear", "emoji:👟"),
            SuggestedSubCategory("Electronics", "electronics"),
            SuggestedSubCategory("Gadgets", "gadgets"),
            SuggestedSubCategory("Bags & accessories", "shopping_bags"),
            SuggestedSubCategory("Jewelry", "jewelry"),
            SuggestedSubCategory("Cosmetics", "cosmetics"),
            SuggestedSubCategory("Stationery", "stationery"),
            SuggestedSubCategory("Gifts for others", "gift"),
            SuggestedSubCategory("Home decor", "home"),
            SuggestedSubCategory("Online orders", "shopping"),
        ),
        "Bills & utilities" to listOf(
            SuggestedSubCategory("Electricity", "electricity"),
            SuggestedSubCategory("Water", "water"),
            SuggestedSubCategory("Gas / cylinder", "gas"),
            SuggestedSubCategory("Mobile recharge", "phone"),
            SuggestedSubCategory("Broadband", "internet"),
            SuggestedSubCategory("DTH / cable", "streaming"),
            SuggestedSubCategory("Society maintenance", "maintenance"),
            SuggestedSubCategory("Trash & garbage", "cleaning"),
            SuggestedSubCategory("Solar & green power", "electricity"),
        ),
        "Rent & housing" to listOf(
            SuggestedSubCategory("Rent", "rent"),
            SuggestedSubCategory("Furniture", "furniture"),
            SuggestedSubCategory("Repairs", "repairs"),
            SuggestedSubCategory("Cleaning & maid", "cleaning"),
            SuggestedSubCategory("Laundry", "laundry"),
            SuggestedSubCategory("Appliances", "appliances"),
            SuggestedSubCategory("Security & CCTV", "security"),
            SuggestedSubCategory("Plumbing", "plumbing"),
            SuggestedSubCategory("Garden", "garden"),
        ),
        "Health" to listOf(
            SuggestedSubCategory("Doctor", "doctor"),
            SuggestedSubCategory("Dentist", "dentist"),
            SuggestedSubCategory("Pharmacy", "pharmacy"),
            SuggestedSubCategory("Hospital", "hospital"),
            SuggestedSubCategory("Lab tests", "emoji:🧪"),
            SuggestedSubCategory("Eye care", "eye_care"),
            SuggestedSubCategory("Glasses", "vision"),
            SuggestedSubCategory("Therapy", "therapy"),
        ),
        "Personal care" to listOf(
            SuggestedSubCategory("Salon & haircut", "haircut"),
            SuggestedSubCategory("Spa & massage", "spa"),
            SuggestedSubCategory("Beauty", "beauty"),
            SuggestedSubCategory("Skincare", "skincare"),
            SuggestedSubCategory("Grooming", "emoji:🪒"),
        ),
        "Fitness" to listOf(
            SuggestedSubCategory("Gym membership", "fitness"),
            SuggestedSubCategory("Yoga & classes", "emoji:🧘"),
            SuggestedSubCategory("Sports gear", "sports"),
            SuggestedSubCategory("Swimming", "emoji:🏊"),
            SuggestedSubCategory("Fitness apps", "emoji:⌚"),
        ),
        "Entertainment" to listOf(
            SuggestedSubCategory("Movies", "movies"),
            SuggestedSubCategory("Music", "music"),
            SuggestedSubCategory("Games", "games"),
            SuggestedSubCategory("Streaming", "streaming"),
            SuggestedSubCategory("Concerts & events", "concerts"),
            SuggestedSubCategory("Sports", "sports"),
            SuggestedSubCategory("Books", "books"),
            SuggestedSubCategory("Hobbies", "hobbies"),
            SuggestedSubCategory("Party", "party"),
            SuggestedSubCategory("Camping", "camping"),
            SuggestedSubCategory("Art & crafts", "art"),
            SuggestedSubCategory("Theater & shows", "movies"),
            SuggestedSubCategory("Amusement parks", "tickets"),
            SuggestedSubCategory("Nightlife & clubs", "bar"),
        ),
        "Events" to listOf(
            SuggestedSubCategory("Weddings", "wedding"),
            SuggestedSubCategory("Birthdays", "emoji:🎂"),
            SuggestedSubCategory("Festivals", "emoji:🪔"),
            SuggestedSubCategory("Functions & ceremonies", "emoji:🎊"),
        ),
        "Education" to listOf(
            SuggestedSubCategory("School fees", "school_fees"),
            SuggestedSubCategory("Tuition", "education"),
            SuggestedSubCategory("Coaching classes", "education"),
            SuggestedSubCategory("Online courses", "software"),
            SuggestedSubCategory("Textbooks", "books"),
            SuggestedSubCategory("School supplies", "stationery"),
            SuggestedSubCategory("School uniform", "clothing"),
            SuggestedSubCategory("Exam fees", "emoji:📝"),
            SuggestedSubCategory("Hostel & PG", "hotel"),
            SuggestedSubCategory("Certifications", "emoji:🎓"),
        ),
        "Kids" to listOf(
            SuggestedSubCategory("Baby supplies", "baby"),
            SuggestedSubCategory("Toys", "toys"),
            SuggestedSubCategory("Childcare", "childcare"),
            SuggestedSubCategory("Kids' activities", "emoji:🤸"),
            SuggestedSubCategory("Pocket money", "emoji:👛"),
        ),
        "Pets" to listOf(
            SuggestedSubCategory("Pet food", "emoji:🦴"),
            SuggestedSubCategory("Vet", "vet"),
            SuggestedSubCategory("Pet grooming", "emoji:🐩"),
            SuggestedSubCategory("Pet supplies", "pets"),
        ),
        "Travel" to listOf(
            SuggestedSubCategory("Flights", "flight"),
            SuggestedSubCategory("Hotels", "hotel"),
            SuggestedSubCategory("Visa & passport", "visa"),
            SuggestedSubCategory("Luggage", "luggage"),
            SuggestedSubCategory("Tour packages", "emoji:🧳"),
            SuggestedSubCategory("Forex & cash", "cash"),
            SuggestedSubCategory("Souvenirs", "gift"),
            SuggestedSubCategory("Car rental", "emoji:🚗"),
            SuggestedSubCategory("Sightseeing", "emoji:🗽"),
        ),
        "Subscriptions" to listOf(
            // OTT & Video Streaming Platforms
            SuggestedSubCategory("Netflix", "netflix"),
            SuggestedSubCategory("YouTube", "youtube"),
            SuggestedSubCategory("Amazon Prime", "amazonprime"),
            SuggestedSubCategory("Disney+ Hotstar", "disneyplus"),
            SuggestedSubCategory("Apple TV+", "appletv"),
            SuggestedSubCategory("JioCinema", "jiocinema"),
            SuggestedSubCategory("SonyLIV", "sonyliv"),
            SuggestedSubCategory("Zee5", "zee5"),
            SuggestedSubCategory("Hulu", "hulu"),
            SuggestedSubCategory("Max", "max"),
            SuggestedSubCategory("Twitch", "twitch"),
            SuggestedSubCategory("Crunchyroll", "crunchyroll"),

            // Music & Audio Streaming
            SuggestedSubCategory("Spotify", "spotify"),
            SuggestedSubCategory("Apple Music", "applemusic"),
            SuggestedSubCategory("YouTube Music", "youtubemusic"),
            SuggestedSubCategory("Audible", "audible"),
            SuggestedSubCategory("Podcasts", "podcasts"),

            // AI & Smart Services
            SuggestedSubCategory("ChatGPT", "chatgpt"),
            SuggestedSubCategory("Claude", "claude"),
            SuggestedSubCategory("Google Gemini", "gemini"),
            SuggestedSubCategory("Perplexity", "perplexity"),
            SuggestedSubCategory("Midjourney", "midjourney"),
            SuggestedSubCategory("GitHub Copilot", "githubcopilot"),

            // Cloud & Digital Services
            SuggestedSubCategory("Google", "google"),
            SuggestedSubCategory("Google One", "googleone"),
            SuggestedSubCategory("Google Workspace", "googleworkspace"),
            SuggestedSubCategory("Microsoft 365", "microsoft365"),
            SuggestedSubCategory("iCloud", "icloud"),
            SuggestedSubCategory("Dropbox", "dropbox"),
            SuggestedSubCategory("AWS", "aws"),
            SuggestedSubCategory("Domain & hosting", "hosting"),
            SuggestedSubCategory("VPN & security", "vpn"),

            // Productivity & Creative
            SuggestedSubCategory("Notion", "notion"),
            SuggestedSubCategory("Figma", "figma"),
            SuggestedSubCategory("Canva", "canva"),
            SuggestedSubCategory("Adobe", "adobe"),
            SuggestedSubCategory("Slack", "slack"),
            SuggestedSubCategory("Zoom", "zoom"),
            SuggestedSubCategory("LinkedIn Premium", "linkedin"),

            // Gaming Subscriptions
            SuggestedSubCategory("PlayStation Plus", "playstation"),
            SuggestedSubCategory("Xbox Game Pass", "xbox"),
            SuggestedSubCategory("Nintendo Switch Online", "nintendo"),
            SuggestedSubCategory("Discord Nitro", "discord"),

            // Reading, Fitness & Lifestyle
            SuggestedSubCategory("News & magazines", "books"),
            SuggestedSubCategory("Medium", "medium"),
        ),
        "Insurance" to listOf(
            SuggestedSubCategory("Life insurance", "insurance"),
            SuggestedSubCategory("Health insurance", "emoji:❤️‍🩹"),
            SuggestedSubCategory("Vehicle insurance", "transport"),
            SuggestedSubCategory("Travel insurance", "flight"),
            SuggestedSubCategory("Home insurance", "emoji:🏡"),
        ),
        "Loans & EMI" to listOf(
            SuggestedSubCategory("Home loan EMI", "home"),
            SuggestedSubCategory("Car loan EMI", "emoji:🚙"),
            SuggestedSubCategory("Personal loan EMI", "loan"),
            SuggestedSubCategory("Student loan", "education"),
            SuggestedSubCategory("Credit card EMI", "credit_card"),
            SuggestedSubCategory("Gold loan", "gold"),
        ),
        "Taxes" to listOf(
            SuggestedSubCategory("Income tax", "tax"),
            SuggestedSubCategory("Property tax", "emoji:🏘️"),
            SuggestedSubCategory("GST", "bills"),
            SuggestedSubCategory("Tax filing", "legal"),
        ),
        "Investments" to listOf(
            SuggestedSubCategory("Mutual funds", "mutual_funds"),
            SuggestedSubCategory("Stocks", "stocks"),
            SuggestedSubCategory("Gold", "gold"),
            SuggestedSubCategory("Crypto", "crypto"),
            SuggestedSubCategory("FD & RD", "emoji:🏦"),
            SuggestedSubCategory("PPF & NPS", "savings"),
        ),
        "Family support" to listOf(
            SuggestedSubCategory("Parents", "emoji:👵"),
            SuggestedSubCategory("Money sent home", "home"),
            SuggestedSubCategory("Relatives", "family"),
            SuggestedSubCategory("Allowances", "cash"),
        ),
        "Donations" to listOf(
            SuggestedSubCategory("Charity", "charity"),
            SuggestedSubCategory("Temple & worship", "temple"),
            SuggestedSubCategory("Crowdfunding", "emoji:🤝"),
            SuggestedSubCategory("Offerings", "donation"),
        ),
        "Other" to listOf(
            SuggestedSubCategory("Legal fees", "legal"),
            SuggestedSubCategory("Fines & penalties", "fines"),
            SuggestedSubCategory("Membership", "membership"),
        ),
        "Salary" to listOf(
            SuggestedSubCategory("Monthly salary", "salary"),
            SuggestedSubCategory("Bonus", "bonus"),
            SuggestedSubCategory("Overtime", "emoji:⏰"),
            SuggestedSubCategory("Reimbursements", "refund"),
        ),
        "Freelance" to listOf(
            SuggestedSubCategory("Client projects", "work"),
            SuggestedSubCategory("Consulting", "freelance"),
            SuggestedSubCategory("Design work", "art"),
            SuggestedSubCategory("Tutoring", "education"),
            SuggestedSubCategory("Software development", "software"),
            SuggestedSubCategory("Content & writing", "stationery"),
        ),
        "Business" to listOf(
            SuggestedSubCategory("Sales", "business"),
            SuggestedSubCategory("Services", "work"),
            SuggestedSubCategory("Rental income", "rental_income"),
            SuggestedSubCategory("Side hustle", "side_hustle"),
        ),
        "Interest" to listOf(
            SuggestedSubCategory("Savings account", "savings"),
            SuggestedSubCategory("Fixed deposits", "emoji:🏦"),
            SuggestedSubCategory("Bonds", "emoji:📜"),
        ),
        "Gifts" to listOf(
            SuggestedSubCategory("Cash gifts", "cash"),
            SuggestedSubCategory("Festival gifts", "gift"),
            SuggestedSubCategory("Wedding gifts", "wedding"),
        ),
        "Other income" to listOf(
            SuggestedSubCategory("Dividends", "dividend"),
            SuggestedSubCategory("Refunds", "refund"),
            SuggestedSubCategory("Pension", "pension"),
            SuggestedSubCategory("Prizes & cashback", "bonus"),
        ),
    )
}
