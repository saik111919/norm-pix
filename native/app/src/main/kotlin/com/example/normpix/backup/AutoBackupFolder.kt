package com.example.normpix.backup

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.core.net.toUri
import java.io.FileNotFoundException
import java.io.IOException
import java.io.OutputStream

/**
 * The folder automatic backups are written into, as the user picked it in Android's folder picker (a document tree). Only the
 * files directly in it are seen, and only those this app is asked about are touched: [list] says what is there, [create] makes a
 * new file, [write] fills it and [delete] removes one. Each throws when the folder can't be reached (gone, or NormPix's access to
 * it was taken back): a [SecurityException], [FileNotFoundException] or [IllegalArgumentException] for that, an [IOException] for
 * anything else.
 */
interface BackupFolder {
    /** A file in the folder: [id] is how the folder knows it, [name] what the user sees. */
    class Entry(val id: String, val name: String)

    /** The files directly in the folder (not in folders inside it). */
    fun list(): List<Entry>

    /** A new, empty file called [name] (the folder may change the name to keep it unique: the entry says what it is). */
    fun create(name: String, mimeType: String): Entry

    /** [entry]'s contents to write, from its start. The caller closes it. */
    fun write(entry: Entry): OutputStream

    /** Deletes [entry]. False if it couldn't be. */
    fun delete(entry: Entry): Boolean
}

/** The folders a user can pick for automatic backups, by the text of their tree URI (the only thing kept of one, in preferences). */
interface BackupFolders {
    /** Keeps NormPix's access to the folder [tree] after a restart. Throws [SecurityException] when it can't be kept. */
    fun keep(tree: String)

    /** Gives NormPix's access to the folder [tree] back. Never throws. */
    fun release(tree: String)

    /** The folder's name as a file manager shows it, or a stand-in when it can't be read. */
    fun nameOf(tree: String): String

    /** The folder [tree], to write into; null when NormPix no longer may (its access was taken back) or it isn't a folder. */
    fun open(tree: String): BackupFolder?
}

/** [BackupFolders] through Android's Storage Access Framework: the folders are the system picker's, and so is the access to them. */
class DocumentTreeFolders(context: Context) : BackupFolders {
    private val resolver: ContentResolver = context.applicationContext.contentResolver

    override fun keep(tree: String) {
        resolver.takePersistableUriPermission(tree.toUri(), ACCESS)
    }

    override fun release(tree: String) {
        runCatching { resolver.releasePersistableUriPermission(tree.toUri(), ACCESS) }
    }

    override fun nameOf(tree: String): String = runCatching {
        val uri = tree.toUri()
        val folder = DocumentsContract.buildDocumentUriUsingTree(uri, DocumentsContract.getTreeDocumentId(uri))
        resolver.query(folder, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull()?.takeIf { it.isNotBlank() } ?: FALLBACK_NAME

    override fun open(tree: String): BackupFolder? {
        val uri = runCatching { tree.toUri() }.getOrNull() ?: return null
        val held = resolver.persistedUriPermissions.any { it.uri == uri && it.isReadPermission && it.isWritePermission }
        if (!held) return null
        return runCatching { DocumentTreeFolder(resolver, uri) }.getOrNull()
    }

    companion object {
        /** What NormPix keeps of a picked folder: reading what is in it (to find its own backups) and writing to it. */
        const val ACCESS = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION

        /** Said of a folder whose name can't be read. */
        const val FALLBACK_NAME = "The folder you picked"
    }
}

/** One picked folder ([tree]), through the documents provider that holds it. */
private class DocumentTreeFolder(private val resolver: ContentResolver, private val tree: Uri) : BackupFolder {
    private val rootId: String = DocumentsContract.getTreeDocumentId(tree)
    private val root: Uri = DocumentsContract.buildDocumentUriUsingTree(tree, rootId)

    private fun uriOf(entry: BackupFolder.Entry): Uri = DocumentsContract.buildDocumentUriUsingTree(tree, entry.id)

    override fun list(): List<BackupFolder.Entry> {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, rootId)
        val columns = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
        )
        val cursor = resolver.query(children, columns, null, null, null) ?: throw FileNotFoundException("The folder can't be listed")
        return cursor.use { c ->
            buildList {
                while (c.moveToNext()) {
                    val id = c.getString(0) ?: continue
                    val name = c.getString(1) ?: continue
                    if (c.getString(2) == DocumentsContract.Document.MIME_TYPE_DIR) continue
                    add(BackupFolder.Entry(id, name))
                }
            }
        }
    }

    override fun create(name: String, mimeType: String): BackupFolder.Entry {
        val made = DocumentsContract.createDocument(resolver, root, mimeType, name) ?: throw IOException("The file couldn't be made")
        val id = DocumentsContract.getDocumentId(made)
        // The provider may have changed the name to keep it unique: what it is called is what it says.
        val shown = runCatching {
            resolver.query(made, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }.getOrNull() ?: name
        return BackupFolder.Entry(id, shown)
    }

    override fun write(entry: BackupFolder.Entry): OutputStream {
        val uri = uriOf(entry)
        // "wt" replaces what is there; a provider with no truncate mode takes "w" (a new file is empty either way).
        return runCatching { resolver.openOutputStream(uri, "wt") }.getOrNull()
            ?: resolver.openOutputStream(uri, "w")
            ?: throw FileNotFoundException("The file can't be written")
    }

    override fun delete(entry: BackupFolder.Entry): Boolean = runCatching { DocumentsContract.deleteDocument(resolver, uriOf(entry)) }.getOrDefault(false)
}
