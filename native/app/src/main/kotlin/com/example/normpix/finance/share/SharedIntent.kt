package com.example.normpix.finance.share

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import androidx.core.content.IntentCompat

/*
 * What an `ACTION_SEND` share carries, read straight from its intent. The quick add overlay (`QuickAddActivity`) is what opens it as
 * a new-transaction draft; before 2026-11-16 the text waited in an inbox in memory for the main screen, and this file was its
 * `SharedTextInbox`.
 */

/**
 * The text an [Intent.ACTION_SEND] share carries (its subject first, when the text doesn't already contain it), or null
 * when it carries none. Never throws: an extra of the wrong type or one that can't be unpacked is just no text.
 */
fun sharedTextOf(intent: Intent?): String? {
    if (intent?.action != Intent.ACTION_SEND) return null
    return try {
        val text = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
        val subject = intent.getCharSequenceExtra(Intent.EXTRA_SUBJECT)?.toString()?.takeIf { it.isNotBlank() && text?.contains(it) != true }
        listOfNotNull(subject, text).joinToString("\n").ifBlank { null }
    } catch (_: RuntimeException) {
        null
    }
}

/**
 * The picture an image share carries, or null. Only a `content://` picture from another app is taken: never a file path,
 * and never one of NormPix's own files ([ownPackage]'s providers) - another app must not be able to have NormPix read
 * its private files back into a form. Never throws.
 */
fun sharedPictureOf(intent: Intent?, ownPackage: String): Uri? {
    if (intent?.action != Intent.ACTION_SEND || intent.type?.startsWith("image/") != true) return null
    return try {
        val uri = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
            ?: intent.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.uri
        uri?.takeIf { it.scheme == ContentResolver.SCHEME_CONTENT && !isOwnAuthority(it.authority, ownPackage) }
    } catch (_: RuntimeException) {
        null
    }
}

/**
 * Where [ShareTransactionActivity] puts a shared picture it was not allowed to pass the right to read on with (the sharing app gave
 * none to pass on). Not `EXTRA_STREAM`: Android adds the read grant to every `ACTION_SEND` that carries one, and would refuse it
 * again. The overlay is still told which picture it was, tries it, and says it couldn't read it.
 */
const val EXTRA_PICTURE_WITHOUT_GRANT = "com.example.normpix.share.PICTURE_WITHOUT_GRANT"

/** The picture a share handed over to the overlay carries - with the right to read it, or without ([EXTRA_PICTURE_WITHOUT_GRANT]) - on [sharedPictureOf]'s terms. */
fun handedOverPictureOf(intent: Intent?, ownPackage: String): Uri? {
    sharedPictureOf(intent, ownPackage)?.let { return it }
    if (intent?.action != Intent.ACTION_SEND || intent.type?.startsWith("image/") != true) return null
    return try {
        IntentCompat.getParcelableExtra(intent, EXTRA_PICTURE_WITHOUT_GRANT, Uri::class.java)
            ?.takeIf { it.scheme == ContentResolver.SCHEME_CONTENT && !isOwnAuthority(it.authority, ownPackage) }
    } catch (_: RuntimeException) {
        null
    }
}

/**
 * Whether a `content://` address with this [authority] is one of [ownPackage]'s providers (or has no authority to tell by).
 *
 * Android reads an authority as `user@name`: a share from a work profile arrives as `10@com.bank.files`, and the part before the
 * `@` is the user it belongs to, not the provider. So `0@<own provider>` is NormPix's own files as much as `<own provider>` is,
 * and it is the name after the last `@` - what Android itself looks the provider up by - that is compared.
 */
internal fun isOwnAuthority(authority: String?, ownPackage: String): Boolean =
    authority == null || authority.substringAfterLast('@').startsWith(ownPackage, ignoreCase = true)
