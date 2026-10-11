package com.example.normpix.finance.ui

/** One emoji a category can use: the glyph itself, and a short lowercase name for screen readers and search. */
class EmojiChoice(val glyph: String, val label: String)

/** A titled run of related emoji in the picker. */
class EmojiGroup(val title: String, val emojis: List<EmojiChoice>)

/** The emoji offered when a user picks the icon of a category, in display order. */
val emojiCatalog: List<EmojiGroup> = listOf(
    EmojiGroup(
        "Food & drink",
        listOf(
            EmojiChoice("🍔", "hamburger"),
            EmojiChoice("🍕", "pizza"),
            EmojiChoice("🍣", "sushi"),
            EmojiChoice("🌮", "taco"),
            EmojiChoice("🍞", "bread"),
            EmojiChoice("🥗", "salad"),
            EmojiChoice("☕", "coffee"),
            EmojiChoice("🍵", "tea"),
            EmojiChoice("🍻", "beer mugs"),
            EmojiChoice("🍷", "wine glass"),
            EmojiChoice("🍨", "ice cream"),
            EmojiChoice("🍰", "cake")
        )
    ),
    EmojiGroup(
        "Groceries",
        listOf(
            EmojiChoice("🛒", "shopping cart"),
            EmojiChoice("🍎", "apple"),
            EmojiChoice("🍌", "banana"),
            EmojiChoice("🥕", "carrot"),
            EmojiChoice("🥦", "broccoli"),
            EmojiChoice("🥛", "milk"),
            EmojiChoice("🥚", "egg"),
            EmojiChoice("🧀", "cheese"),
            EmojiChoice("🍖", "meat on bone"),
            EmojiChoice("🛍️", "shopping bags")
        )
    ),
    EmojiGroup(
        "Transport",
        listOf(
            EmojiChoice("🚗", "car"),
            EmojiChoice("🚕", "taxi"),
            EmojiChoice("🚌", "bus"),
            EmojiChoice("🚇", "metro"),
            EmojiChoice("🚆", "train"),
            EmojiChoice("🛺", "auto rickshaw"),
            EmojiChoice("🛵", "motor scooter"),
            EmojiChoice("🚲", "bicycle"),
            EmojiChoice("⛽", "fuel pump"),
            EmojiChoice("✈️", "airplane"),
            EmojiChoice("🚢", "ship"),
            EmojiChoice("🅿️", "parking")
        )
    ),
    EmojiGroup(
        "Home & bills",
        listOf(
            EmojiChoice("🏠", "house"),
            EmojiChoice("🔌", "electric plug"),
            EmojiChoice("💡", "light bulb"),
            EmojiChoice("💧", "droplet"),
            EmojiChoice("🔥", "fire"),
            EmojiChoice("🔧", "wrench"),
            EmojiChoice("🔑", "key"),
            EmojiChoice("🛏️", "bed"),
            EmojiChoice("🛋️", "couch and lamp"),
            EmojiChoice("📺", "television"),
            EmojiChoice("☎️", "telephone"),
            EmojiChoice("📡", "satellite antenna")
        )
    ),
    EmojiGroup(
        "Shopping",
        listOf(
            EmojiChoice("👕", "t-shirt"),
            EmojiChoice("👖", "jeans"),
            EmojiChoice("👗", "dress"),
            EmojiChoice("👟", "running shoe"),
            EmojiChoice("👜", "handbag"),
            EmojiChoice("⌚", "watch"),
            EmojiChoice("💎", "gem stone"),
            EmojiChoice("💄", "lipstick"),
            EmojiChoice("🕶️", "sunglasses"),
            EmojiChoice("📦", "package")
        )
    ),
    EmojiGroup(
        "Health & fitness",
        listOf(
            EmojiChoice("💊", "pill"),
            EmojiChoice("🩺", "stethoscope"),
            EmojiChoice("💉", "syringe"),
            EmojiChoice("🏥", "hospital"),
            EmojiChoice("🦷", "tooth"),
            EmojiChoice("🏅", "sports medal"),
            EmojiChoice("🎽", "running shirt"),
            EmojiChoice("⚽", "soccer ball"),
            EmojiChoice("🏀", "basketball"),
            EmojiChoice("🎾", "tennis")
        )
    ),
    EmojiGroup(
        "Fun",
        listOf(
            EmojiChoice("🎬", "clapper board"),
            EmojiChoice("🎮", "video game"),
            EmojiChoice("🎵", "musical note"),
            EmojiChoice("🎧", "headphone"),
            EmojiChoice("🎫", "ticket"),
            EmojiChoice("🎉", "party popper"),
            EmojiChoice("🎨", "artist palette"),
            EmojiChoice("🎤", "microphone"),
            EmojiChoice("🎳", "bowling"),
            EmojiChoice("🎸", "guitar"),
            EmojiChoice("🎞️", "film frames"),
            EmojiChoice("🎲", "dice")
        )
    ),
    EmojiGroup(
        "Money",
        listOf(
            EmojiChoice("💰", "money bag"),
            EmojiChoice("💵", "dollar banknote"),
            EmojiChoice("💳", "credit card"),
            EmojiChoice("💶", "euro banknote"),
            EmojiChoice("🏦", "bank"),
            EmojiChoice("📈", "chart increasing"),
            EmojiChoice("📉", "chart decreasing"),
            EmojiChoice("🧾", "receipt"),
            EmojiChoice("💼", "briefcase"),
            EmojiChoice("🐷", "piggy bank"),
            EmojiChoice("💸", "money with wings"),
            EmojiChoice("🤝", "handshake")
        )
    ),
    EmojiGroup(
        "Family & pets",
        listOf(
            EmojiChoice("👶", "baby"),
            EmojiChoice("🍼", "baby bottle"),
            EmojiChoice("🧸", "teddy bear"),
            EmojiChoice("🐶", "dog face"),
            EmojiChoice("🐱", "cat face"),
            EmojiChoice("🐾", "paw prints"),
            EmojiChoice("🪁", "kite"),
            EmojiChoice("❤️", "heart"),
            EmojiChoice("🎁", "gift"),
            EmojiChoice("🎀", "ribbon")
        )
    ),
    EmojiGroup(
        "Work & learning",
        listOf(
            EmojiChoice("📚", "books"),
            EmojiChoice("🎓", "graduation cap"),
            EmojiChoice("✏️", "pencil"),
            EmojiChoice("💻", "laptop"),
            EmojiChoice("🖥️", "desktop computer"),
            EmojiChoice("📱", "mobile phone"),
            EmojiChoice("🖨️", "printer"),
            EmojiChoice("📝", "memo"),
            EmojiChoice("📎", "paperclip"),
            EmojiChoice("🏫", "school")
        )
    ),
    EmojiGroup(
        "Travel",
        listOf(
            EmojiChoice("🧳", "luggage"),
            EmojiChoice("🗺️", "world map"),
            EmojiChoice("🧭", "compass"),
            EmojiChoice("⛺", "camping tent"),
            EmojiChoice("🏖️", "beach with umbrella"),
            EmojiChoice("⛰️", "mountain"),
            EmojiChoice("🏨", "hotel"),
            EmojiChoice("🌏", "globe asia-australia"),
            EmojiChoice("📍", "map pin"),
            EmojiChoice("📷", "camera")
        )
    ),
    EmojiGroup(
        "Giving & other",
        listOf(
            EmojiChoice("🕯️", "candle"),
            EmojiChoice("🕊️", "dove"),
            EmojiChoice("🌿", "herb"),
            EmojiChoice("🍀", "four leaf clover"),
            EmojiChoice("✨", "sparkles"),
            EmojiChoice("⭐", "star"),
            EmojiChoice("🌈", "rainbow"),
            EmojiChoice("❓", "question mark"),
            EmojiChoice("♻️", "recycling symbol"),
            EmojiChoice("🔒", "locked padlock"),
            EmojiChoice("☔", "umbrella"),
            EmojiChoice("🌙", "crescent moon")
        )
    )
)

/** The catalog's emoji for [query] (matched against names, case-insensitively); the whole catalog for a blank query. */
fun searchEmoji(query: String): List<EmojiChoice> {
    val wanted = query.trim().lowercase()
    val all = emojiCatalog.flatMap { it.emojis }
    return if (wanted.isEmpty()) all else all.filter { wanted in it.label }
}
