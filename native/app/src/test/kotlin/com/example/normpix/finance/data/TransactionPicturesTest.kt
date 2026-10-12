package com.example.normpix.finance.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.media.ExifInterface
import android.net.Uri
import com.example.normpix.finance.domain.FinanceTransaction
import com.example.normpix.finance.domain.FinanceValidationException
import com.example.normpix.finance.domain.TransactionType
import com.example.normpix.finance.domain.isPictureName
import com.example.normpix.finance.ui.PictureEdit
import com.example.normpix.testing.TestEnvironment
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Pictures attached to transactions: staged into a draft, kept when the transaction is saved, and deleted with it, when
 * replaced or removed, when everything is erased, and when nothing names them any longer.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TransactionPicturesTest {
    private val env = TestEnvironment(prefsName = "transaction_pictures_test")
    private val context = env.context
    private val finance = env.container.finance
    private val store = finance.pictures!!
    private val kept = File(context.filesDir, TransactionPictures.KEPT_DIR)
    private val drafts = File(context.cacheDir, TransactionPictures.DRAFTS_DIR)
    private val now = LocalDateTime.of(2026, 10, 11, 9, 30)

    @After
    fun tearDown() {
        env.close()
        kept.deleteRecursively()
        drafts.deleteRecursively()
    }

    private fun picture(width: Int, height: Int, format: Bitmap.CompressFormat = Bitmap.CompressFormat.PNG): ByteArray {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
        return ByteArrayOutputStream().also { bitmap.compress(format, 90, it) }.toByteArray()
    }

    private var served = 0

    /** [bytes] as another app's picture, the way a share or the photo picker hands one over. */
    private fun serve(bytes: ByteArray): Uri {
        val uri = Uri.parse("content://com.example.gallery/picture-${served++}")
        shadowOf(context.contentResolver).registerInputStreamSupplier(uri) { ByteArrayInputStream(bytes) }
        return uri
    }

    private fun stage(bytes: ByteArray = picture(1080, 2400)): File = assertNotNull(runBlocking { store.stage(serve(bytes)) })

    private fun keptNames(): Set<String> = kept.listFiles().orEmpty().map { it.name }.toSet()

    private fun expense(amount: Long = 25_000): FinanceTransaction = FinanceTransaction(
        type = TransactionType.Expense,
        amount = amount,
        accountId = finance.state.value.defaultAccount!!.id!!,
        categoryId = finance.state.value.categories.first { it.name == "Food & dining" }.id,
        occurredAt = now,
        payee = "Swiggy",
        createdAt = now,
        updatedAt = now,
    )

    // ---------------------------------------------------------------- store

    @Test
    fun `a picture is staged as an upright JPEG no larger than the limit, with nothing else from the file`() {
        val photo = File(context.cacheDir, "photo.jpg").apply { writeBytes(picture(4000, 1000, Bitmap.CompressFormat.JPEG)) }
        ExifInterface(photo.absolutePath).apply {
            setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_ROTATE_90.toString())
            setAttribute(ExifInterface.TAG_GPS_LATITUDE, "12/1,58/1,0/1")
            setAttribute(ExifInterface.TAG_GPS_LATITUDE_REF, "N")
            setAttribute(ExifInterface.TAG_MAKE, "Phone maker")
            saveAttributes()
        }
        val draft = stage(photo.readBytes())
        assertEquals(drafts, draft.parentFile)

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(draft.path, bounds)
        assertEquals("image/jpeg", bounds.outMimeType)
        // Upright (turned a quarter), and halved to fit.
        assertEquals(500, bounds.outWidth)
        assertEquals(2000, bounds.outHeight)
        val exif = ExifInterface(draft.path)
        assertNull(exif.getAttribute(ExifInterface.TAG_GPS_LATITUDE))
        assertNull(exif.getAttribute(ExifInterface.TAG_MAKE))
    }

    @Test
    fun `what isn't a picture is not staged`() {
        assertNull(runBlocking { store.stage(serve("Paid ₹250 to Swiggy".toByteArray())) })
        assertTrue(drafts.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun `a kept picture is a copy under a name of its own, and only drafts are discarded`() = runBlocking {
        val draft = stage()
        val name = store.keep(draft)
        assertTrue(isPictureName(name))
        assertTrue(draft.isFile, "the draft stays the form's")
        val file = assertNotNull(store.file(name))
        assertEquals(draft.readBytes().toList(), file.readBytes().toList())

        store.discard(draft)
        assertFalse(draft.exists())
        store.discard(file)
        assertTrue(file.exists(), "a kept picture is not a draft")
        // Never a path, only a name the store gave.
        assertNull(store.file("../databases/normpix_finance.db"))
        assertNull(store.file("pic-1.jpg"))
    }

    @Test
    fun `the sweep deletes pictures nothing names and drafts a day old`() = runBlocking {
        val named = store.keep(stage())
        val stray = store.keep(stage())
        val fresh = stage()
        val stale = stage().apply { setLastModified(System.currentTimeMillis() - 2 * 24 * 60 * 60 * 1000L) }
        store.sweep(setOf(named))
        assertEquals(setOf(named), keptNames())
        assertFalse(store.exists(stray))
        assertTrue(fresh.exists())
        assertFalse(stale.exists())
    }

    // ------------------------------------------------------- with the ledger

    @Test
    fun `saving keeps the picture, replacing deletes the old one, and removing deletes it`() = runBlocking {
        val saved = finance.saveTransaction(expense(), PictureEdit.Attach(stage()))
        val first = assertNotNull(saved.picture)
        assertTrue(store.exists(first))
        assertEquals(first, finance.repository.transactionById(saved.id!!)!!.picture)

        // An edit that leaves the picture alone keeps it.
        val edited = finance.saveTransaction(saved.copy(note = "Dinner"))
        assertEquals(first, edited.picture)
        assertTrue(store.exists(first))

        val replaced = finance.saveTransaction(edited, PictureEdit.Attach(stage()))
        val second = assertNotNull(replaced.picture)
        assertTrue(second != first)
        assertEquals(setOf(second), keptNames())

        val removed = finance.saveTransaction(replaced, PictureEdit.Remove)
        assertNull(removed.picture)
        assertNull(finance.repository.transactionById(saved.id!!)!!.picture)
        assertTrue(keptNames().isEmpty())
    }

    @Test
    fun `a save that fails keeps no picture, and leaves the draft to try again`() = runBlocking {
        val draft = stage()
        assertFailsWith<FinanceValidationException> { finance.saveTransaction(expense(amount = 0), PictureEdit.Attach(draft)) }
        assertTrue(keptNames().isEmpty())
        assertTrue(draft.isFile)
    }

    @Test
    fun `deleting a transaction deletes its picture`() = runBlocking {
        val deleted = finance.saveTransaction(expense(), PictureEdit.Attach(stage()))
        val other = finance.saveTransaction(expense(), PictureEdit.Attach(stage()))
        finance.deleteTransaction(deleted.id!!)
        assertEquals(setOf(other.picture!!), keptNames())
    }

    @Test
    fun `erasing everything erases the pictures`() = runBlocking {
        finance.saveTransaction(expense(), PictureEdit.Attach(stage()))
        stage()
        finance.resetAll()
        assertTrue(keptNames().isEmpty())
        assertTrue(drafts.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun `a restored backup keeps the pictures this phone has and leaves off the rest`() = runBlocking {
        val here = finance.saveTransaction(expense(), PictureEdit.Attach(stage()))
        val gone = finance.saveTransaction(expense(), PictureEdit.Attach(stage()))
        val text = FinanceBackupCodec.encode(finance.repository.snapshot(), now)
        store.delete(gone.picture)
        val snapshot = assertNotNull(FinanceBackupCodec.decode(text).snapshot)
        assertEquals(setOf(here.picture, gone.picture), snapshot.transactions.map { it.picture }.toSet())

        finance.restore(snapshot)
        assertEquals(here.picture, finance.repository.transactionById(here.id!!)!!.picture)
        assertNull(finance.repository.transactionById(gone.id!!)!!.picture)
        assertEquals(setOf(here.picture!!), keptNames())
    }

    @Test
    fun `a backup never brings in a path as a picture`() {
        val saved = runBlocking { finance.saveTransaction(expense()) }
        val withPath = FinanceBackupCodec.encode(
            runBlocking { finance.repository.snapshot() }.let { s ->
                s.copy(transactions = s.transactions.map { if (it.id == saved.id) it.copy(picture = "../../databases/x.db") else it })
            },
            now,
        )
        val read = assertNotNull(FinanceBackupCodec.decode(withPath).snapshot)
        assertNull(read.transactions.single { it.id == saved.id }.picture)
    }

    @Test
    fun `the ledger takes only a picture name the store could have given`() = runBlocking {
        assertFailsWith<FinanceValidationException> { finance.saveTransaction(expense().copy(picture = "../../databases/x.db")) }
        Unit
    }

    @Test
    fun `loading sweeps away pictures nothing names`() = runBlocking {
        val saved = finance.saveTransaction(expense(), PictureEdit.Attach(stage()))
        val stray = store.keep(stage())
        finance.load()
        assertEquals(setOf(saved.picture!!), keptNames())
        assertFalse(store.exists(stray))
    }
}
