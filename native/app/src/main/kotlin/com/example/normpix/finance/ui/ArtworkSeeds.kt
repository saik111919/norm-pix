package com.example.normpix.finance.ui

import android.content.ContentResolver
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.example.normpix.finance.domain.Category
import com.example.normpix.finance.share.decodePicture
import com.example.normpix.ui.theme.ArtworkColors
import com.example.normpix.wallet.AccountCardDesign
import com.example.normpix.wallet.BankBrandTheme
import java.io.File
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/*
 * Where each form's artwork colour comes from (see ui/components/ArtworkTheme.kt for what is done with it). One rule: a picture is
 * read for its colour; everything the app draws itself - a card's face, a category's or a person's tone - is not read from its
 * pixels but uses the colour its own design already defines.
 */

/** The colour a card's (or an account's) face says: the main - first - colour of its theme's gradient. */
internal fun BankBrandTheme.artworkSeed(): Color = gradientColors.first()

/**
 * What a form that records a payment wears (2026-11-08, owner: "colors based on categories, otherwise fallbacks"): the chosen
 * **category's** colour first; with no category (yet, or a transfer) the attached **picture's**, then the **account's** card's,
 * then nothing - the app's own colours.
 */
internal fun artworkSeedOf(category: Category?, picture: Color?, account: Color?): Color? =
    category?.let { paletteSeed(it.color) } ?: picture ?: account

/** The colour of the card [accountId] has in the Wallet (its bank's, or its kind's); null for no account. */
@Composable
internal fun accountArtworkSeed(accountId: Long?): Color? {
    val finance = rememberFinanceState()
    return remember(accountId, finance.activeAccounts) {
        finance.activeAccounts.firstOrNull { it.id == accountId }
            ?.let { AccountCardDesign.themeFor(finance.catalog.byKey(it.bankKey)?.name, it.kind).artworkSeed() }
    }
}

/** The longest side a picture is read at for its colour: a colour needs a thumbnail's pixels, not a photo's. */
private const val SEED_SIDE = 128

/**
 * What pictures said, kept for the session so that reopening a form does not read them again: by where the picture is, when it was
 * last written and how long it is (so a replaced file is read again). A picture with no colour (a grey one) is remembered too.
 */
internal object PictureSeeds {
    private const val LIMIT = 48
    private val known = object : LinkedHashMap<String, Known>(LIMIT, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Known>?) = size > LIMIT
    }

    internal class Known(val seed: Color?)

    fun keyOf(file: File): String = "${file.path}|${file.lastModified()}|${file.length()}"

    @Synchronized
    fun cached(key: String): Known? = known[key]

    @Synchronized
    fun remember(key: String, seed: Color?) {
        known[key] = Known(seed)
    }

    /** Reads [file]'s colour: null when it can't be read or has none. Blocking: call it off the main thread. */
    fun read(resolver: ContentResolver, file: File): Color? = try {
        val bitmap = decodePicture(resolver, Uri.fromFile(file), SEED_SIDE) ?: return null
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        bitmap.recycle()
        ArtworkColors.seedFromPixels(pixels)
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    } catch (_: OutOfMemoryError) {
        null
    }
}

/**
 * The colour of [file], a picture a form shows: null while it is read (off the main thread) the first time and when there is
 * no picture, the previous picture's colour while the next is read (never a drop to the app's colours and back), and at once for
 * one read before. Never recomputed by a recomposition.
 */
@Composable
fun rememberPictureSeed(file: File?): Color? {
    val resolver = LocalContext.current.contentResolver
    val key = file?.let(PictureSeeds::keyOf)
    val seed by produceState(initialValue = key?.let { PictureSeeds.cached(it)?.seed }, key) {
        if (file == null || key == null) {
            value = null
            return@produceState
        }
        PictureSeeds.cached(key)?.let {
            value = it.seed
            return@produceState
        }
        val found = withContext(Dispatchers.Default) { PictureSeeds.read(resolver, file) }
        PictureSeeds.remember(key, found)
        value = found
    }
    return seed
}
