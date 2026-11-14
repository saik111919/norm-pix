package com.example.normpix.updates

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** What Android calls an APK, and what the document picker is asked to show. */
const val APK_MIME_TYPE = "application/vnd.android.package-archive"

/**
 * NormPix's own release names, as CI writes them (`NormPix-1.5.12.apk`, per CPU `NormPix-1.5.12-arm64-v8a.apk` or `-universal`,
 * a prerelease `NormPix-1.5.12-build.42.apk`), with the suffix a browser adds to a second download of the same file
 * (`NormPix-1.5.12 (1).apk`, `NormPix-1.5.12-1.apk`). Only the ".apk" may be in any case.
 */
private val NORMPIX_APK = Regex(
    """NormPix-\d+(?:\.\d+)*(?:-(?:arm64-v8a|armeabi-v7a|x86_64|universal))?(?:-build\.\d+)?(?: \(\d+\)|-\d+)?(?i:\.apk)""",
)

/** Whether [name] looks like a NormPix release APK, as saved by a browser or the GitHub app. */
fun isNormPixApkName(name: String): Boolean = NORMPIX_APK.matches(name)

/**
 * A file the user picked in the document picker to be deleted. [size] is null when the provider did not say; [canDelete] is
 * null when it did not say whether the file can be deleted (it is tried all the same), false when it said it cannot.
 */
data class PickedApk(val uri: Uri, val name: String, val size: Long?, val canDelete: Boolean?) {
    /** Whether the file is named like a NormPix release; any other is still deletable, as the user picked it. */
    val looksLikeNormPix: Boolean get() = isNormPixApkName(name)
}

/**
 * The files of APKs the user hands over for deleting. NormPix cannot list another app's files in Downloads without the
 * all-files permission it does not ask for, so it only ever touches what the user picks in the system's own picker.
 */
interface ApkFileStore {
    /** The name, size and deletability of each of [uris], in the order given. */
    suspend fun describe(uris: List<Uri>): List<PickedApk>

    /** Deletes [file]; false if it could not be (it stays where it is). Never throws. */
    suspend fun delete(file: PickedApk): Boolean
}

/** [ApkFileStore] over the system's content resolver: the picked files are documents, deleted through their provider. */
class AndroidApkFileStore(context: Context) : ApkFileStore {
    private val context = context.applicationContext

    override suspend fun describe(uris: List<Uri>): List<PickedApk> = withContext(Dispatchers.IO) {
        uris.map { uri ->
            val (name, size) = nameAndSize(uri)
            PickedApk(uri, name ?: UNNAMED, size, canDelete = supportsDelete(uri))
        }
    }

    override suspend fun delete(file: PickedApk): Boolean = withContext(Dispatchers.IO) {
        try {
            DocumentsContract.deleteDocument(context.contentResolver, file.uri)
        } catch (e: Exception) {
            // Any failure leaves the file where it was; what went wrong is not said, as it would name the file.
            false
        }
    }

    private fun nameAndSize(uri: Uri): Pair<String?, Long?> = try {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            if (!cursor.moveToFirst()) return@use null to null
            val name = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME).takeIf { it >= 0 && !cursor.isNull(it) }?.let(cursor::getString)
            val size = cursor.getColumnIndex(OpenableColumns.SIZE).takeIf { it >= 0 && !cursor.isNull(it) }?.let(cursor::getLong)
            name to size
        } ?: (null to null)
    } catch (e: Exception) {
        null to null
    }

    /** True or false as the document says; null when it does not say (not a document, or no flags column). */
    private fun supportsDelete(uri: Uri): Boolean? = try {
        val flags = context.contentResolver.query(uri, arrayOf(DocumentsContract.Document.COLUMN_FLAGS), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getInt(0) else null
        }
        flags?.let { it and DocumentsContract.Document.FLAG_SUPPORTS_DELETE != 0 }
    } catch (e: Exception) {
        null
    }

    private companion object {
        const val UNNAMED = "Unnamed file"
    }
}
