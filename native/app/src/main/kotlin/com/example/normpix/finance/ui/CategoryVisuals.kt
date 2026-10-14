package com.example.normpix.finance.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.CardGiftcard
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.ChildCare
import androidx.compose.material.icons.outlined.Checkroom
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.Flight
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LocalCafe
import androidx.compose.material.icons.outlined.LocalGasStation
import androidx.compose.material.icons.outlined.LocalGroceryStore
import androidx.compose.material.icons.outlined.MedicalServices
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Pets
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.RequestQuote
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material.icons.outlined.Spa
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material.icons.outlined.Subscriptions
import androidx.compose.material.icons.outlined.Train
import androidx.compose.material.icons.outlined.VolunteerActivism
import androidx.compose.material.icons.outlined.Wallet
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material.icons.outlined.WorkOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.normpix.finance.domain.AccountKind
import com.example.normpix.finance.domain.Category
import com.example.normpix.settings.CategoryIconStyle
import com.example.normpix.ui.theme.isDark
import com.materialkolor.PaletteStyle
import com.materialkolor.blend.Blend
import com.materialkolor.dynamicColorScheme

/**
 * A curated icon a category can use. Keys are stable (stored in the database); labels are for the picker and screen readers.
 * [icon] is the Material *outlined* glyph, at one weight: it sits on a tonal disc beside the app's other outlined icons, and
 * a mix of outlined and rounded ones would read as two hands. [emoji] is the same idea in colour; which of the two is drawn
 * is the user's choice ([CategoryIconStyle]), and in 3D the emoji is drawn as a 3D picture. (A category may instead have an
 * emoji of its own; see [categoryArt].) Optional [brandAsset] points to an authentic vector asset stored in the app's offline
 * assets directory (e.g. `brands/netflix.svg`); a brand may also have a logo (see [brandLogo]).
 */
class CategoryIconSpec(
    val icon: ImageVector,
    val label: String,
    val emoji: String,
    val brandAsset: String? = null,
)

private val coreCategoryIcons: List<Pair<String, CategoryIconSpec>> = listOf(
    "restaurant" to CategoryIconSpec(Icons.Outlined.Restaurant, "Dining", "🍽️"),
    "grocery" to CategoryIconSpec(Icons.Outlined.LocalGroceryStore, "Groceries", "🛒"),
    "coffee" to CategoryIconSpec(Icons.Outlined.LocalCafe, "Coffee", "☕"),
    "transport" to CategoryIconSpec(Icons.Outlined.DirectionsCar, "Transport", "🚗"),
    "fuel" to CategoryIconSpec(Icons.Outlined.LocalGasStation, "Fuel", "⛽"),
    "transit" to CategoryIconSpec(Icons.Outlined.Train, "Transit", "🚆"),
    "shopping" to CategoryIconSpec(Icons.Outlined.ShoppingBag, "Shopping", "🛍️"),
    "clothing" to CategoryIconSpec(Icons.Outlined.Checkroom, "Clothing", "👕"),
    "electronics" to CategoryIconSpec(Icons.Outlined.Devices, "Electronics", "💻"),
    "bills" to CategoryIconSpec(Icons.Outlined.ReceiptLong, "Bills", "🧾"),
    "phone" to CategoryIconSpec(Icons.Outlined.Smartphone, "Phone", "📱"),
    "internet" to CategoryIconSpec(Icons.Outlined.Wifi, "Internet", "📶"),
    "subscriptions" to CategoryIconSpec(Icons.Outlined.Subscriptions, "Subscriptions", "📺"),
    "home" to CategoryIconSpec(Icons.Outlined.Home, "Home", "🏠"),
    "health" to CategoryIconSpec(Icons.Outlined.MedicalServices, "Health", "🩺"),
    "fitness" to CategoryIconSpec(Icons.Outlined.FitnessCenter, "Fitness", "🏋️"),
    "personal" to CategoryIconSpec(Icons.Outlined.Spa, "Personal care", "💆"),
    "entertainment" to CategoryIconSpec(Icons.Outlined.Movie, "Entertainment", "🎬"),
    "education" to CategoryIconSpec(Icons.Outlined.School, "Education", "🎓"),
    "travel" to CategoryIconSpec(Icons.Outlined.Flight, "Travel", "✈️"),
    "kids" to CategoryIconSpec(Icons.Outlined.ChildCare, "Kids", "🧸"),
    "pets" to CategoryIconSpec(Icons.Outlined.Pets, "Pets", "🐾"),
    "insurance" to CategoryIconSpec(Icons.Outlined.Shield, "Insurance", "🛡️"),
    "tax" to CategoryIconSpec(Icons.Outlined.AccountBalance, "Taxes", "🏛️"),
    "loan" to CategoryIconSpec(Icons.Outlined.RequestQuote, "Loans", "💸"),
    "charity" to CategoryIconSpec(Icons.Outlined.VolunteerActivism, "Charity", "🎗️"),
    "gift" to CategoryIconSpec(Icons.Outlined.CardGiftcard, "Gifts", "🎁"),
    "salary" to CategoryIconSpec(Icons.Outlined.Payments, "Salary", "💵"),
    "work" to CategoryIconSpec(Icons.Outlined.WorkOutline, "Work", "💼"),
    "business" to CategoryIconSpec(Icons.Outlined.Storefront, "Business", "🏪"),
    "interest" to CategoryIconSpec(Icons.Outlined.Savings, "Interest", "🪙"),
    "investment" to CategoryIconSpec(Icons.AutoMirrored.Outlined.TrendingUp, "Investments", "📈"),
    "refund" to CategoryIconSpec(Icons.AutoMirrored.Outlined.Undo, "Refunds", "↩️"),
    "other" to CategoryIconSpec(Icons.Outlined.Category, "Other", "📦"),
)

/**
 * Every icon a category can be given, in the order the picker shows them: the 35 the app was built around, then the wider set
 * in `CategoryIconExtras` (pizza, taxi, dentist ...), and brands (Netflix, YouTube, Swiggy, Zomato ...).
 * Keys never change meaning, because they are stored.
 */
val categoryIcons: Map<String, CategoryIconSpec> = LinkedHashMap<String, CategoryIconSpec>().apply {
    for ((key, spec) in coreCategoryIcons) put(key, spec)
    // The core keys win a clash, so an extra can never redefine an icon already in use.
    for ((key, spec) in extraCategoryIcons) putIfAbsent(key, spec)
    for ((key, spec) in brandCategoryIcons) putIfAbsent(key, spec)
    for ((key, spec) in brandLogoCategoryIcons) putIfAbsent(key, spec)
}

fun categoryIcon(key: String): ImageVector = (categoryIcons[key] ?: categoryIcons.getValue("other")).icon

/** The emoji a built-in icon stands for in the 3D and Emoji styles; the "Other" one for a key the app no longer knows. */
fun categoryEmoji(key: String): String = (categoryIcons[key] ?: categoryIcons.getValue("other")).emoji

/** How built-in category icons are drawn app-wide; provided from the user's choice in Settings > Appearance. */
val LocalCategoryIconStyle = staticCompositionLocalOf { CategoryIconStyle.ThreeD }

/**
 * Seeds for the category palette. They are harmonized toward the app's
 * primary color and turned into tonal container pairs at runtime, so
 * categories stay distinguishable without clashing with the theme.
 */
private val paletteSeeds = listOf(
    Color(0xFFE4572E), // coral
    Color(0xFF2E7D32), // green
    Color(0xFF1E88E5), // blue
    Color(0xFF8E24AA), // purple
    Color(0xFFF9A825), // amber
    Color(0xFF00897B), // teal
    Color(0xFFD81B60), // pink
    Color(0xFF3949AB), // indigo
)

val paletteNames = listOf("Coral", "Green", "Blue", "Purple", "Amber", "Teal", "Pink", "Indigo")

val paletteSize: Int get() = paletteSeeds.size

/** Tonal colors for one palette entry. */
@Immutable
class PaletteColors(
    /** Strong tone for chart segments and progress. */
    val accent: Color,
    val container: Color,
    val onContainer: Color,
)

/** The category palette resolved for the ambient theme. */
@Composable
fun categoryPalette(): List<PaletteColors> {
    val scheme = MaterialTheme.colorScheme
    val dark = scheme.isDark
    return remember(scheme.primary, dark) {
        paletteSeeds.map { seed ->
            val generated = dynamicColorScheme(
                seedColor = Color(Blend.harmonize(seed.toArgb(), scheme.primary.toArgb())),
                isDark = dark,
                style = PaletteStyle.TonalSpot,
            )
            PaletteColors(generated.primary, generated.primaryContainer, generated.onPrimaryContainer)
        }
    }
}

@Composable
fun paletteColor(index: Int): PaletteColors {
    val palette = categoryPalette()
    return palette[Math.floorMod(index, palette.size)]
}

/**
 * A category's icon on its tonal palette color: a disc that deepens toward one corner and is edged by a hairline of the
 * category's accent, so a row of them reads as a set of distinct, finished tokens rather than flat grey-ish dots. A brand's
 * logo is round already, so it fills the disc; a 3D picture is drawn larger than a flat icon, which would look lost at its size.
 */
@Composable
fun CategoryAvatar(category: Category?, modifier: Modifier = Modifier, size: Dp = 40.dp) {
    val scheme = MaterialTheme.colorScheme
    val colors = category?.let { paletteColor(it.color) }
    AvatarDisc(colors, modifier, size) {
        if (category == null) {
            Icon(Icons.AutoMirrored.Outlined.HelpOutline, contentDescription = null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(size / 2))
        } else {
            val art = rememberCategoryArt(category.icon, category.name)
            val artSize = when (art) {
                is CategoryArt.Logo -> size
                is CategoryArt.Picture -> size * PICTURE_IN_DISC
                else -> size / 2
            }
            CategoryArtView(art, colors?.onContainer ?: scheme.onSurfaceVariant, artSize)
        }
    }
}

/** How much of an avatar's disc a 3D picture spans. */
private const val PICTURE_IN_DISC = 0.62f

/**
 * The round tonal disc behind a category's or a person's picture: [colors] deepening toward one corner and edged by a hairline
 * of the accent (or a plain neutral disc, without colors), so every kind of avatar in the app shares one look.
 */
@Composable
internal fun AvatarDisc(colors: PaletteColors?, modifier: Modifier, size: Dp, content: @Composable () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val fill = if (colors == null) {
        Brush.linearGradient(listOf(scheme.surfaceContainerHighest, scheme.surfaceContainerHighest))
    } else {
        Brush.linearGradient(listOf(colors.container, lerp(colors.container, colors.accent, AVATAR_DEPTH)))
    }
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(fill)
            .then(if (colors == null) Modifier else Modifier.border(1.dp, colors.accent.copy(alpha = AVATAR_RING), CircleShape)),
        contentAlignment = Alignment.Center,
    ) { content() }
}

private val drawable = java.util.concurrent.ConcurrentHashMap<String, Boolean>()

/**
 * Whether this device's emoji font has [emoji]. The app's built-in emoji are all from older Unicode releases, but an older
 * Android still lacks some of them and would draw an empty box; those categories fall back to the outlined icon instead.
 * (An emoji the user chose themselves is shown as given: it came from their own keyboard, so their device has it.)
 */
internal fun deviceCanDraw(emoji: String): Boolean =
    drawable.getOrPut(emoji) { runCatching { android.graphics.Paint().hasGlyph(emoji) }.getOrDefault(true) }

/** How far the avatar's disc deepens toward its accent, and how visible its edge is. */
private const val AVATAR_DEPTH = 0.2f
private const val AVATAR_RING = 0.16f

/**
 * A category's icon at [size], drawn as [categoryArt] says for the user's icon style: a brand's logo or mark, a 3D picture,
 * an emoji, or the app's outlined icon in [tint]. [name] is the category's, for a category called after a brand.
 *
 * Decorative: the category's name is what a screen reader says.
 */
@Composable
fun CategoryGlyph(
    icon: String,
    tint: Color,
    size: Dp,
    modifier: Modifier = Modifier,
    name: String? = null,
) {
    CategoryArtView(rememberCategoryArt(icon, name), tint, size, modifier)
}

/** Avatar for a transfer (no category). */
@Composable
fun TransferAvatar(modifier: Modifier = Modifier, size: Dp = 40.dp) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier.size(size).background(scheme.secondaryContainer, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Outlined.SwapHoriz,
            contentDescription = null,
            tint = scheme.onSecondaryContainer,
            modifier = Modifier.size(size / 2),
        )
    }
}

val AccountKind.icon: ImageVector
    get() = when (this) {
        AccountKind.Cash -> Icons.Outlined.Payments
        AccountKind.Bank -> Icons.Outlined.AccountBalance
        AccountKind.Card -> Icons.Outlined.CreditCard
        AccountKind.Wallet -> Icons.Outlined.AccountBalanceWallet
        AccountKind.Savings -> Icons.Outlined.Savings
        AccountKind.Other -> Icons.Outlined.Wallet
    }

val AccountKind.label: String
    get() = when (this) {
        AccountKind.Cash -> "Cash"
        AccountKind.Bank -> "Bank account"
        AccountKind.Card -> "Card"
        AccountKind.Wallet -> "UPI / e-wallet"
        AccountKind.Savings -> "Savings"
        AccountKind.Other -> "Other"
    }
