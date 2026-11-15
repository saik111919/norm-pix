package com.example.normpix.settings.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.EventRepeat
import androidx.compose.material.icons.outlined.FolderZip
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.SettingsBackupRestore
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import com.example.normpix.AppContainer
import com.example.normpix.backup.AutoBackupNames
import com.example.normpix.backup.AutoBackupOutcome
import com.example.normpix.backup.AutoBackupProblem
import com.example.normpix.backup.AutoBackupService
import com.example.normpix.backup.LockedFile
import com.example.normpix.backup.SealedSecretException
import com.example.normpix.navigation.LocalAppContainer
import com.example.normpix.settings.AppPrefsState
import com.example.normpix.settings.AutoBackupEvery
import com.example.normpix.ui.components.AppHaptics
import com.example.normpix.ui.components.AppOverlays
import com.example.normpix.ui.components.AppSnackbars
import com.example.normpix.ui.components.EditorSheet
import com.example.normpix.ui.components.Icon
import com.example.normpix.ui.components.ListRow
import com.example.normpix.ui.components.ListSection
import com.example.normpix.ui.components.OptionSheet
import com.example.normpix.ui.components.OptionSheetItem
import com.example.normpix.ui.components.SnackbarKind
import com.example.normpix.ui.components.SwitchRow
import com.example.normpix.ui.theme.AppSpacing
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.CompletableDeferred

/** Asks the user for a folder (Android's folder picker): the picked folder's tree URI as text, or null if they backed out. */
fun interface FolderPicker {
    suspend fun pick(): String?
}

/** [FolderPicker] through the system's folder picker (`ACTION_OPEN_DOCUMENT_TREE`), for the current activity. */
@Composable
fun rememberFolderPicker(): FolderPicker {
    val lock = LocalAppContainer.current.lock
    val pending = remember { arrayOfNulls<CompletableDeferred<Uri?>>(1) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri -> pending[0]?.complete(uri) }
    return remember(lock, launcher) {
        FolderPicker {
            val result = CompletableDeferred<Uri?>()
            pending[0]?.complete(null)
            pending[0] = result
            // The system picker isn't "leaving" NormPix: don't re-lock while it's open.
            lock.expectExternalActivity()
            try {
                launcher.launch(null)
                result.await()?.toString()
            } finally {
                if (pending[0] === result) pending[0] = null
            }
        }
    }
}

private val autoBackupWhen = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm")

// What the Data page's rows are busy with (its `run` ids) while an automatic backup step is under way.
private const val AUTO_SWITCH = "auto"
private const val AUTO_FOLDER = "auto-folder"
private const val AUTO_EVERY = "auto-every"
private const val AUTO_CARDS = "auto-cards"
private const val AUTO_PASSWORD = "auto-password"
private const val AUTO_NOW = "auto-now"

/** What to do about [this], in a sentence, from Settings > Data & backup > Automatic backup. */
internal val AutoBackupProblem.fix: String
    get() = when (this) {
        AutoBackupProblem.FolderGone -> "Tap Folder to choose it again."
        AutoBackupProblem.PasswordMissing -> "Tap Change password to set a new one."
        AutoBackupProblem.OutOfSpace -> "Make some room there, or choose another folder."
        AutoBackupProblem.CardsLeftOut -> "They go in again once they can be read."
        AutoBackupProblem.SampleData -> "Nothing was written."
        AutoBackupProblem.Failed -> "It's tried again next time, or tap Back up now."
    }

/** The line under "Automatic backup" that says how the last one went: [headline], [supporting], and whether it is a [problem]. */
internal data class AutoBackupStatus(val headline: String, val supporting: String, val problem: Boolean)

/** How the last automatic backup went, as Settings says it (times in [zone]). */
internal fun autoBackupStatus(prefs: AppPrefsState, zone: ZoneId = ZoneId.systemDefault()): AutoBackupStatus {
    fun at(millis: Long) = autoBackupWhen.format(Instant.ofEpochMilli(millis).atZone(zone))
    val problem = prefs.autoBackupProblem
    if (problem != null && problem != AutoBackupProblem.CardsLeftOut) {
        val whenText = if (prefs.autoBackupProblemAt > 0) "${at(prefs.autoBackupProblemAt)}: " else ""
        return AutoBackupStatus(problem.title, "$whenText${problem.message} ${problem.fix}", problem = true)
    }
    if (prefs.autoBackupLastAt <= 0) return AutoBackupStatus("Last automatic backup", "None yet", problem = false)
    val made = "${at(prefs.autoBackupLastAt)} · ${prefs.autoBackupLastFile}"
    return if (problem == AutoBackupProblem.CardsLeftOut) {
        AutoBackupStatus("Last automatic backup", "$made. Saved cards were left out: they couldn't be read.", problem = true)
    } else {
        AutoBackupStatus("Last automatic backup", made, problem = false)
    }
}

/**
 * Settings > Data & backup > Automatic backup, step by step: turning it on (what it does, the folder, the password twice), and
 * off; the folder, how often, saved cards, the password; and a backup now. Everything that writes goes through [service], which
 * reads the user's own data whatever the screens show; the password is only ever handed on as characters, zeroed once sealed.
 */
internal class AutoBackupFlows(
    private val service: AutoBackupService,
    private val container: AppContainer,
    private val overlays: AppOverlays,
    private val snackbars: AppSnackbars,
    private val picker: FolderPicker,
    private val haptics: AppHaptics,
) {
    private val prefs get() = container.preferences.state.value

    /** Explains, asks for a folder and a password, turns automatic backups on and makes the first one. Nothing changes if the user backs out. */
    suspend fun turnOn() {
        overlays.sheet<Unit> { dismiss -> AutoBackupIntroSheet(onDone = dismiss) } ?: return
        val tree = picker.pick() ?: return
        val name = service.folderName(tree)
        val password = overlays.sheet<CharArray> { dismiss -> AutoBackupPasswordSheet(forChange = false, folderName = name, onDone = dismiss) } ?: return
        val outcome = try {
            overlays.loading("Turning on automatic backup", "Keeping the password on this phone, then making the first backup.", cancellable = false) {
                service.turnOn(tree, name, password)
                service.backUpNow()
            }
        } catch (_: SecurityException) {
            overlays.alert("Can't use that folder", "NormPix wasn't allowed to keep using it, so automatic backup is still off. Choose another folder.", icon = Icons.Outlined.ErrorOutline)
            return
        } catch (_: SealedSecretException) {
            overlays.alert("Automatic backup is still off", "The password couldn't be kept on this phone, so nothing was turned on.", icon = Icons.Outlined.ErrorOutline)
            return
        } finally {
            password.fill('\u0000')
        }
        when (outcome) {
            is AutoBackupOutcome.Saved -> {
                haptics.success()
                snackbars.show("Automatic backup is on. The first backup is in $name.")
            }
            is AutoBackupOutcome.Failed -> overlays.alert(
                "Automatic backup is on",
                "But the first backup didn't work. ${outcome.problem.message} ${outcome.problem.fix}",
                icon = Icons.Outlined.ErrorOutline,
            )
            AutoBackupOutcome.Off, null -> Unit
        }
    }

    /** Asks, then turns automatic backups off: the password is deleted and the folder let go. The backups already made stay. */
    suspend fun turnOff() {
        val folder = prefs.autoBackupFolderName.ifEmpty { "the folder" }
        val sure = overlays.confirm(
            title = "Turn off automatic backup?",
            message = "NormPix stops making backups and forgets their password and folder. The backups already in $folder stay " +
                "there, and you'll still need that password to restore one.",
            confirmLabel = "Turn off",
            destructive = true,
            icon = Icons.Outlined.SettingsBackupRestore,
        )
        if (!sure) return
        overlays.loading("Turning off automatic backup", cancellable = false) { service.turnOff() }
        snackbars.show("Automatic backup is off. The backups already made stay in $folder.")
    }

    /** Asks for another folder; automatic backups go there from the next one. */
    suspend fun changeFolder() {
        val tree = picker.pick() ?: return
        val name = service.folderName(tree)
        try {
            service.changeFolder(tree, name)
        } catch (_: SecurityException) {
            snackbars.show("NormPix couldn't keep using that folder. Choose another.", SnackbarKind.Error)
            return
        }
        haptics.success()
        snackbars.show("Automatic backups now go to $name")
    }

    /** Daily, weekly or monthly. */
    suspend fun chooseEvery() {
        val current = prefs.autoBackupEvery
        val chosen = overlays.sheet<AutoBackupEvery> { dismiss ->
            OptionSheet(
                title = "How often",
                subtitle = "Android picks a quiet moment, when the battery and storage aren't low",
                options = AutoBackupEvery.entries.map { OptionSheetItem(it, it.label, it.description) },
                selected = setOf(current),
                onSelect = { dismiss(it) },
            )
        } ?: return
        if (chosen != current) {
            haptics.selection()
            container.preferences.setAutoBackupEvery(chosen)
        }
    }

    /**
     * Puts the saved cards into automatic backups ([include]) - after saying what that means, and unlocking while Wallet lock is
     * on, as backing them up by hand does - or leaves them out.
     */
    suspend fun setCards(include: Boolean) {
        if (!include) {
            haptics.selection()
            container.preferences.setAutoBackupCards(false)
            return
        }
        val sure = overlays.confirm(
            title = "Include saved cards?",
            message = "Every automatic backup will then hold your saved cards with their full numbers, expiry and CVV where one is " +
                "kept, locked with the automatic backup password.",
            confirmLabel = "Include",
            cancelLabel = "Leave out",
            icon = Icons.Outlined.CreditCard,
        )
        if (!sure) return
        if (prefs.walletLockEnabled && !container.lock.authenticate("Unlock to include your cards")) {
            snackbars.show("Couldn't unlock, so saved cards stay left out.", SnackbarKind.Error)
            return
        }
        haptics.success()
        container.preferences.setAutoBackupCards(true)
    }

    /** A new password, typed twice, for the backups from now on (unlocking first while Wallet lock is on and cards go in). */
    suspend fun changePassword() {
        if (prefs.walletLockEnabled && prefs.autoBackupCards && !container.lock.authenticate("Unlock to change the backup password")) {
            snackbars.show("Couldn't unlock, so the password stays as it was.", SnackbarKind.Error)
            return
        }
        val password = overlays.sheet<CharArray> { dismiss -> AutoBackupPasswordSheet(forChange = true, folderName = prefs.autoBackupFolderName, onDone = dismiss) } ?: return
        try {
            overlays.loading("Changing the password", cancellable = false) { service.changePassword(password) }
        } catch (_: SealedSecretException) {
            overlays.alert("The password couldn't be changed", "It couldn't be kept on this phone. Try again.", icon = Icons.Outlined.ErrorOutline)
            return
        } finally {
            password.fill('\u0000')
        }
        haptics.success()
        snackbars.show("Password changed. The next backup is locked with it.")
    }

    /** Makes an automatic backup now, the way the schedule does, and says how it went. */
    suspend fun backUpNow() {
        val folder = prefs.autoBackupFolderName.ifEmpty { "the folder" }
        val outcome = overlays.loading("Backing up", "Locking it with the automatic backup password and saving it to $folder.", cancellable = false) {
            service.backUpNow()
        } ?: return
        when (outcome) {
            is AutoBackupOutcome.Saved -> {
                haptics.success()
                if (outcome.cardsLeftOut) {
                    snackbars.show("Backup saved: ${outcome.fileName}. Saved cards were left out: they couldn't be read.", SnackbarKind.Warning)
                } else {
                    snackbars.show("Backup saved: ${outcome.fileName}")
                }
            }
            is AutoBackupOutcome.Failed -> overlays.alert(outcome.problem.title, "${outcome.problem.message} ${outcome.problem.fix}", icon = Icons.Outlined.ErrorOutline)
            AutoBackupOutcome.Off -> Unit
        }
    }
}

/**
 * The Automatic backup group of Settings > Data & backup: the switch and, while it is on, how the last backup went, the folder, how
 * often, saved cards, the password and a backup now. [busy] is the page's operation under way (rows wait for it); [run] starts one.
 */
@Composable
internal fun AutoBackupSection(prefs: AppPrefsState, flows: AutoBackupFlows, busy: String?, run: (String, suspend () -> Unit) -> Unit) {
    val on = prefs.autoBackupEnabled && prefs.autoBackupFolder.isNotEmpty()
    val idle = busy == null
    val cloud = "Nothing is sent anywhere by NormPix; if you pick a folder of a cloud app (Google Drive, say), that app may upload the locked file."
    ListSection(
        title = "Automatic backup",
        footer = when {
            !on -> "A backup of everything, as above, made by NormPix on its own and saved, locked, into a folder you pick. $cloud"
            else -> "Each backup holds your data, pictures, settings and reminders, locked with the automatic backup password; the " +
                "newest ${AutoBackupNames.KEEP} are kept and older ones are deleted. Restore one with " +
                "Restore from backup and that password." +
                (if (prefs.dummyDataEnabled) " It is always your own data, never the sample set." else "") + " $cloud"
        },
    ) {
        item {
            SettingsSwitchRow(
                headline = "Back up automatically",
                supporting = if (on) "${prefs.autoBackupEvery.label}, into ${prefs.autoBackupFolderName}" else "A locked backup of everything, made by itself",
                icon = Icons.Outlined.SettingsBackupRestore,
                hue = 5,
                emoji = SettingsRowEmoji.LOCK_AND_KEY,
                checked = on,
                onCheckedChange = { want -> if (idle) run(AUTO_SWITCH) { if (want) flows.turnOn() else flows.turnOff() } },
            )
        }
        if (!on) return@ListSection
        item {
            val status = autoBackupStatus(prefs)
            val scheme = MaterialTheme.colorScheme
            ListRow(
                headline = status.headline,
                supporting = status.supporting,
                supportingMaxLines = 4,
                headlineColor = if (status.problem) scheme.error else Color.Unspecified,
                leading = {
                    if (status.problem) {
                        SettingsLeadingBadge(Icons.Outlined.ErrorOutline, SettingsRowEmoji.CLOCK, background = scheme.errorContainer, foreground = scheme.onErrorContainer)
                    } else {
                        SettingsLeadingBadge(Icons.Outlined.History, SettingsRowEmoji.CLOCK)
                    }
                },
            )
        }
        item {
            ListRow(
                headline = "Folder",
                supporting = prefs.autoBackupFolderName,
                enabled = idle,
                onClick = { run(AUTO_FOLDER) { flows.changeFolder() } },
                leading = { SettingsLeadingIcon(Icons.Outlined.FolderZip, SettingsRowEmoji.FILE_BOX, busy = busy == AUTO_FOLDER) },
            )
        }
        item {
            ListRow(
                headline = "How often",
                supporting = prefs.autoBackupEvery.label,
                enabled = idle,
                onClick = { run(AUTO_EVERY) { flows.chooseEvery() } },
                leading = { SettingsLeadingIcon(Icons.Outlined.EventRepeat, SettingsRowEmoji.CALENDAR) },
            )
        }
        // With Store cards off there are none to include (Settings > Wallet & Cards).
        if (prefs.walletCardsEnabled) {
            item {
                SwitchRow(
                    headline = "Include saved cards",
                    supporting = if (prefs.autoBackupCards) "Locked with the automatic backup password" else "Left out of automatic backups",
                    checked = prefs.autoBackupCards,
                    enabled = idle,
                    onCheckedChange = { include -> run(AUTO_CARDS) { flows.setCards(include) } },
                    leading = { SettingsLeadingIcon(Icons.Outlined.CreditCard, SettingsRowEmoji.CARD) },
                )
            }
        }
        item {
            ListRow(
                headline = "Change password",
                supporting = "For the backups from now on. Backups already made keep theirs.",
                enabled = idle,
                onClick = { run(AUTO_PASSWORD) { flows.changePassword() } },
                leading = { SettingsLeadingIcon(Icons.Outlined.Key, SettingsRowEmoji.KEY) },
            )
        }
        item {
            ListRow(
                headline = "Back up now",
                supporting = "Makes one now, as the schedule would",
                enabled = idle,
                onClick = { run(AUTO_NOW) { flows.backUpNow() } },
                leading = { SettingsLeadingIcon(Icons.Outlined.Backup, SettingsRowEmoji.BACKUP, busy = busy == AUTO_NOW) },
            )
        }
    }
}

/** The first step of turning automatic backup on: what it does, and that its password can't be recovered. [onDone] goes on (Unit) or not (null). */
@Composable
internal fun AutoBackupIntroSheet(onDone: (Unit?) -> Unit) {
    EditorSheet(
        title = "Automatic backup",
        subtitle = "A locked backup of everything, made by itself",
        saveLabel = "Choose a folder",
        onSave = { onDone(Unit) },
    ) {
        Text(
            "NormPix will save a locked backup of everything by itself, weekly to begin with, into a folder you pick. You'll need " +
                "the password you set next to restore one, and NormPix can't recover it if it's forgotten.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(AppSpacing.md))
        Text(
            "Saved cards are left out unless you include them afterwards.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private const val AUTO_MISMATCH = "The two passwords don't match"

/**
 * Asks for the automatic backup's password - at least [LockedFile.MIN_PASSWORD] characters, typed twice - to turn it on (backups
 * go to [folderName]) or, [forChange], in place of the one kept. Calls [onDone] with it as characters (the caller zeroes them once
 * sealed), or null if closed.
 */
@Composable
internal fun AutoBackupPasswordSheet(forChange: Boolean, folderName: String, onDone: (CharArray?) -> Unit) {
    var password by remember { mutableStateOf("") }
    var again by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }
    var problem by remember { mutableStateOf<String?>(null) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    fun submit() {
        problem = when {
            password.isEmpty() -> "Enter a password"
            password.length < LockedFile.MIN_PASSWORD -> "Use at least ${LockedFile.MIN_PASSWORD} characters"
            again != password -> AUTO_MISMATCH
            else -> null
        }
        if (problem == null) onDone(password.toCharArray())
    }

    val transformation = if (visible) VisualTransformation.None else PasswordVisualTransformation()
    EditorSheet(
        title = if (forChange) "Change the backup password" else "Set the backup password",
        subtitle = if (forChange) "For the automatic backups from now on" else "Backups go to ${folderName.ifEmpty { "the folder you picked" }}",
        saveLabel = if (forChange) "Change password" else "Turn on",
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
            visualTransformation = transformation,
            trailingIcon = {
                IconButton(onClick = { visible = !visible }) {
                    Icon(
                        if (visible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                        contentDescription = if (visible) "Hide password" else "Show password",
                    )
                }
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false, imeAction = ImeAction.Next),
            isError = problem != null && problem != AUTO_MISMATCH,
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
            isError = problem == AUTO_MISMATCH,
        )
        Spacer(Modifier.height(AppSpacing.md))
        Text(
            (if (forChange) "Backups already made stay locked with the password they were made with. " else "") +
                "It's kept on this phone only, locked by Android's keystore, so NormPix can make backups while you're away. " +
                "NormPix can't recover a forgotten password, so keep it somewhere safe.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
