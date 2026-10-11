package com.example.normpix.finance.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import com.example.normpix.finance.domain.FinanceConflictException
import com.example.normpix.finance.domain.FinanceValidationException
import com.example.normpix.finance.domain.MAX_PERSON_NAME
import com.example.normpix.finance.domain.Person
import com.example.normpix.finance.domain.cleanPersonName
import com.example.normpix.ui.components.AppPrimaryTabs
import com.example.normpix.ui.components.AppTab
import com.example.normpix.ui.components.DiscardChangesGuard
import com.example.normpix.ui.components.EditorSheet
import com.example.normpix.ui.components.rememberAppHaptics
import com.example.normpix.ui.theme.AppSpacing
import java.time.LocalDateTime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * Add or edit a person, shown as a bottom sheet: a name, and a picture that is either their initial or an emoji, on a color.
 * A person is only a label for expenses - nothing here is an account, a balance or a debt. Calls [onDone] with the saved
 * person, or null when closed without saving.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PersonFormSheet(existing: Person?, onDone: (Person?) -> Unit, initialName: String = "") {
    val controller = LocalFinance.current
    val haptics = rememberAppHaptics()
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(existing?.name ?: initialName) }
    var emoji by remember { mutableStateOf(existing?.emoji) }
    // New people start on the next color along, so a few added in a row are told apart without choosing.
    val finance = rememberFinanceState()
    var color by remember { mutableIntStateOf(existing?.color ?: (finance.people.size % paletteSize)) }
    var pictureTab by remember { mutableIntStateOf(if (emoji != null) 1 else 0) }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { if (existing == null) runCatching { focus.requestFocus() } }

    fun snapshot() = listOf(name, emoji, color).joinToString("|")
    val initialSnapshot = remember { snapshot() }
    DiscardChangesGuard(dirty = snapshot() != initialSnapshot, saving = saving, isEditing = existing != null, keptMessage = "The person stays as they were.")

    val now = LocalDateTime.now()
    val preview = Person(name = name, emoji = emoji.takeIf { pictureTab == 1 }, color = color, createdAt = now, updatedAt = now)

    fun save() {
        val draft = (existing ?: Person(name = name, createdAt = now, updatedAt = now))
            .copy(name = name, emoji = emoji.takeIf { pictureTab == 1 }, color = color, updatedAt = LocalDateTime.now())
        saving = true
        error = null
        scope.launch {
            try {
                val saved = controller.savePerson(draft)
                haptics.success()
                onDone(saved)
            } catch (e: FinanceValidationException) {
                error = e.message
            } catch (e: FinanceConflictException) {
                error = e.message
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                error = "Couldn't save. Your other data is unchanged."
            } finally {
                saving = false
            }
        }
    }

    EditorSheet(
        title = if (existing == null) "New person" else "Edit person",
        subtitle = "Someone you spend money on. It only labels expenses.",
        saveLabel = if (existing == null) "Save person" else "Save changes",
        onSave = ::save,
        saving = saving,
        titleTrailing = { Box(Modifier.clearAndSetSemantics {}) { PersonAvatar(preview) } },
    ) {
        OutlinedTextField(
            value = name,
            onValueChange = {
                name = it.take(MAX_PERSON_NAME + 10)
                error = null
            },
            modifier = Modifier.fillMaxWidth().focusRequester(focus),
            label = { Text("Name") },
            placeholder = { Text("e.g. Mom, Alex, Roommate") },
            isError = error != null,
            supportingText = {
                Text(error ?: "${cleanPersonName(name).length}/$MAX_PERSON_NAME")
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
            colors = typedFieldColors(),
            shape = TonalFieldShape,
        )
        Spacer(Modifier.height(AppSpacing.lg))
        FormGroupLabel("Picture")
        AppPrimaryTabs(
            tabs = remember { listOf(AppTab("Initial"), AppTab("Emoji")) },
            selectedIndex = pictureTab,
            onSelect = { pictureTab = it },
        )
        Spacer(Modifier.height(AppSpacing.md))
        if (pictureTab == 0) {
            Text(
                "Shows \"${personInitial(name)}\" on their color.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            EmojiPicker(selected = emoji, onSelect = { emoji = it })
        }
        Spacer(Modifier.height(AppSpacing.xl))
        FormGroupLabel("Color")
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.sm),
        ) {
            for (i in 0 until paletteSize) {
                ColorDot(
                    colors = paletteColor(i),
                    label = paletteNames[i],
                    selected = color == i,
                    onClick = {
                        haptics.selection()
                        color = i
                    },
                )
            }
        }
    }
}
