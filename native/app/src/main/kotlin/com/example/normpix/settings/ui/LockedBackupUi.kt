package com.example.normpix.settings.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import com.example.normpix.backup.LockedFile
import com.example.normpix.finance.ui.ChoiceOption
import com.example.normpix.finance.ui.ConnectedChoiceRow
import com.example.normpix.ui.components.AppOverlays
import com.example.normpix.ui.components.EditorSheet
import com.example.normpix.ui.theme.AppSpacing
import java.io.BufferedInputStream
import java.io.InputStream
import java.io.OutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * How a backup about to be saved is saved: locked with [password] (see [LockedFile]) or, with no password, as it is. Each lock
 * locks one file.
 */
internal class BackupLock private constructor(val password: String?, private val locker: LockedFile.Locker?) {
    val locked: Boolean get() = locker != null

    /** The name the backup [name] is saved under. */
    fun fileName(name: String) = if (locker != null) LockedFile.fileName(name) else name

    /** The type the backup is saved as: [plain] when it isn't locked. */
    fun mimeType(plain: String) = if (locker != null) LockedFile.MIME_TYPE else plain

    /** [bytes] as they are saved. */
    fun seal(bytes: ByteArray): ByteArray = locker?.lock(bytes) ?: bytes

    /** Has [write] write the backup into [out], locked on the way when it is to be. */
    fun <T> write(out: OutputStream, write: (OutputStream) -> T): T = if (locker == null) write(out) else locker.lock(out).use(write)

    /** Said after "Backup saved" of a locked one. */
    val said: String get() = if (locker != null) ", locked with your password" else ""

    companion object {
        val None = BackupLock(null, null)

        fun with(password: String) = BackupLock(password, LockedFile.locker(password.toCharArray()))
    }
}

/** What the lock sheet came to: a [password] to lock with, or none to save the backup as it is. */
internal class LockChoice(val password: String?)

/**
 * Asks whether to lock the backup [what] describes, and with what password; for a lock, draws its key under a loading sheet (it takes
 * a moment, on purpose). Null if the user closed a sheet.
 */
internal suspend fun AppOverlays.askBackupLock(what: String): BackupLock? {
    val choice = sheet<LockChoice> { dismiss -> BackupLockSheet(what, onDone = dismiss) } ?: return null
    val password = choice.password ?: return BackupLock.None
    return loading("Locking the backup") { withContext(Dispatchers.Default) { BackupLock.with(password) } }
}

/** A backup as it is to be read: [stream], unlocked if the file was locked, when [password] is what opened it. */
internal class BackupSource(val stream: InputStream, val password: String?) {
    /**
     * Reads to the end of a locked file, so its last block is checked too - a file cut short or added to says so here (throwing
     * [LockedFile.DamagedException]) before anything read from it is used.
     */
    fun finish() {
        if (password == null) return
        val sink = ByteArray(8 * 1024)
        while (stream.read(sink) >= 0) Unit
    }
}

/** Unlocking came to [stream], or nothing when the password didn't open the file. */
private class Unlocked(val stream: InputStream?)

/**
 * [input], a backup file read from its start, as a backup to read: as it is, or - when it is locked - unlocked with the password the
 * user enters, asked again with a word on why until it opens or they give up. Null when they gave up, or when the file can't be
 * opened, which is said.
 */
internal suspend fun AppOverlays.openBackup(input: InputStream): BackupSource? {
    val source = if (input.markSupported()) input else BufferedInputStream(input)
    val head = withContext(Dispatchers.IO) {
        source.mark(LockedFile.HEAD_BYTES)
        val head = ByteArray(LockedFile.HEAD_BYTES)
        var got = 0
        while (got < head.size) {
            val read = source.read(head, got, head.size - got)
            if (read < 0) break
            got += read
        }
        source.reset()
        head.copyOf(got)
    }
    if (!LockedFile.isLocked(head)) return BackupSource(source, null)
    val locked = when (val opening = withContext(Dispatchers.IO) { LockedFile.open(source) }) {
        is LockedFile.Opening.Invalid -> {
            cantUse(listOf(opening.reason))
            return null
        }
        is LockedFile.Opening.Locked -> opening
    }
    var error: String? = null
    while (true) {
        val shownError = error
        val password = sheet<String> { dismiss -> BackupPasswordSheet(error = shownError, onDone = dismiss) } ?: return null
        val unlocked = loading("Opening the backup") {
            withContext(Dispatchers.Default) { Unlocked(locked.unlock(password.toCharArray())) }
        } ?: return null
        val stream = unlocked.stream
        if (stream != null) return BackupSource(stream, password)
        error = "That password doesn't open this backup. Try again."
    }
}

private const val MISMATCH = "The two passwords don't match"

private val lockOptions = listOf(
    ChoiceOption(true, "Lock it", Icons.Outlined.Lock),
    ChoiceOption(false, "Don't lock", Icons.Outlined.LockOpen),
)

/**
 * Asks whether to lock a backup ([what] it holds) with a password - at least [LockedFile.MIN_PASSWORD] characters, typed twice -
 * and says that a forgotten one can't be recovered; or to save it as it is. Locking is chosen to begin with. Calls [onDone] with
 * the choice, or null if closed.
 */
@Composable
internal fun BackupLockSheet(what: String, onDone: (LockChoice?) -> Unit) {
    var locking by remember { mutableStateOf(true) }
    var password by remember { mutableStateOf("") }
    var again by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }
    var problem by remember { mutableStateOf<String?>(null) }

    fun submit() {
        if (!locking) {
            onDone(LockChoice(null))
            return
        }
        problem = when {
            password.isEmpty() -> "Enter a password"
            password.length < LockedFile.MIN_PASSWORD -> "Use at least ${LockedFile.MIN_PASSWORD} characters"
            again != password -> MISMATCH
            else -> null
        }
        if (problem == null) onDone(LockChoice(password))
    }

    val transformation = if (visible) VisualTransformation.None else PasswordVisualTransformation()
    EditorSheet(
        title = "Lock the backup?",
        subtitle = what,
        saveLabel = if (locking) "Lock and save" else "Save",
        onSave = ::submit,
        errorText = problem,
    ) {
        ConnectedChoiceRow(
            options = lockOptions,
            selected = locking,
            onSelect = {
                locking = it
                problem = null
            },
        )
        Spacer(Modifier.height(AppSpacing.lg))
        if (locking) {
            OutlinedTextField(
                value = password,
                onValueChange = {
                    password = it
                    problem = null
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Password") },
                singleLine = true,
                visualTransformation = transformation,
                trailingIcon = { PasswordEye(visible) { visible = !visible } },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false, imeAction = ImeAction.Next),
                // The field the problem is in is the one marked: the first for a missing or short password, the second for a mismatch.
                isError = problem != null && problem != MISMATCH,
            )
            Spacer(Modifier.height(AppSpacing.md))
            OutlinedTextField(
                value = again,
                onValueChange = {
                    again = it
                    problem = null
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Password again") },
                singleLine = true,
                visualTransformation = transformation,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false, imeAction = ImeAction.Done),
                isError = problem == MISMATCH,
            )
            Spacer(Modifier.height(AppSpacing.md))
            Text(
                "Everything in the file is locked: it can't be read or restored without this password. NormPix can't recover a " +
                    "forgotten password, so keep it somewhere safe.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Text(
                "The file is saved as it is: anyone who gets hold of it can read everything in it.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Asks for the password a backup was locked with. [error] says why it is asked again. Calls [onDone] with what was typed, or null
 * if closed.
 */
@Composable
internal fun BackupPasswordSheet(error: String?, onDone: (String?) -> Unit) {
    var password by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }
    var problem by remember { mutableStateOf(error) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    fun submit() {
        if (password.isEmpty()) problem = "Enter the password" else onDone(password)
    }

    EditorSheet(
        title = "Open the locked backup",
        subtitle = "Enter the password it was locked with",
        saveLabel = "Open",
        onSave = ::submit,
        errorText = problem,
    ) {
        OutlinedTextField(
            value = password,
            onValueChange = {
                password = it
                problem = null
            },
            modifier = Modifier.fillMaxWidth().focusRequester(focus),
            label = { Text("Password") },
            singleLine = true,
            visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = { PasswordEye(visible) { visible = !visible } },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false, imeAction = ImeAction.Done),
            isError = problem != null,
        )
    }
}

@Composable
private fun PasswordEye(visible: Boolean, onToggle: () -> Unit) {
    IconButton(onClick = onToggle) {
        Icon(
            if (visible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
            contentDescription = if (visible) "Hide password" else "Show password",
        )
    }
}
