package com.example.normpix.settings

import android.content.Context
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.example.normpix.navigation.SettingsPage
import com.example.normpix.settings.ui.ProfilePage
import com.example.normpix.settings.ui.SettingsCatalog
import com.example.normpix.settings.ui.SettingsScreen
import com.example.normpix.settings.ui.SettingsSpecial
import com.example.normpix.settings.ui.capped
import com.example.normpix.testing.TestEnvironment
import com.example.normpix.ui.components.profileInitial
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The Profile page: a name and a picture, where the account button leads, and the way on to Settings. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h2400dp")
class ProfilePageTest {
    @get:Rule
    val compose = createComposeRule()

    private lateinit var env: TestEnvironment

    @Before
    fun setUp() {
        Locale.setDefault(Locale.US)
        env = TestEnvironment("profile_page")
    }

    @After
    fun tearDown() = env.close()

    private fun show() {
        compose.setContent { env.Content { ProfilePage() } }
        compose.waitForIdle()
    }

    private val state get() = env.preferences.state.value

    @Test
    fun aNewProfileIsEmptyAndTheAccountButtonStillOpensSettings() {
        show()
        assertEquals("", state.profileName)
        assertEquals("", state.profileEmoji)
        assertEquals(AccountButtonTarget.Settings, state.accountButtonTarget)
        compose.onNodeWithText("Your name").assertExists()
        compose.onNode(hasText("Settings") and isSelectable()).assertIsSelected()
    }

    @Test
    fun theNameIsSavedAsItIsTyped_andAnInnerSpaceSurvivesTheTrimWhileTyping() {
        show()
        val field = compose.onNode(hasSetTextAction())
        field.performTextInput("Ada ")
        compose.waitForIdle()
        assertEquals("Ada", state.profileName, "saved trimmed")
        field.performTextInput("Lovelace")
        compose.waitForIdle()
        assertEquals("Ada Lovelace", state.profileName, "the space typed on the way to the second word was not lost")
        // A restart reads it back.
        assertEquals("Ada Lovelace", AppPreferences(env.prefsStore).state.value.profileName)
        // The page says what it will wear: the first letter of the name.
        compose.onNodeWithText("Shows \"A\", the first letter of your name.").assertExists()
    }

    @Test
    fun aLongNameStopsAtTheLimit() {
        show()
        compose.onNode(hasSetTextAction()).performTextInput("n".repeat(MAX_PROFILE_NAME + 15))
        compose.waitForIdle()
        assertEquals(MAX_PROFILE_NAME, state.profileName.length)
        compose.onNodeWithText("$MAX_PROFILE_NAME/$MAX_PROFILE_NAME · Stays on this device").assertExists()
    }

    @Test
    fun anEmojiIsThePicture_untilTheInitialIsChosenAgain_andThenItIsStillThereToComeBackTo() {
        show()
        compose.onNodeWithText("Emoji").performClick()
        compose.waitForIdle()
        // The picker's own field takes an emoji typed or pasted, and chooses it as it lands.
        compose.onNode(hasSetTextAction() and hasText("Search or paste an emoji")).performTextInput("🍕")
        compose.waitForIdle()
        assertEquals("🍕", state.profileEmoji)

        compose.onNodeWithText("Initial").performClick()
        compose.waitForIdle()
        assertEquals("", state.profileEmoji, "on the initial, the picture is the initial")

        compose.onNodeWithText("Emoji").performClick()
        compose.waitForIdle()
        assertEquals("🍕", state.profileEmoji, "and the emoji is where it was left")
    }

    @Test
    fun theAccountButtonCanBePointedAtTheProfile_andIsRemembered() {
        show()
        compose.onNode(hasText("Profile") and isSelectable()).performScrollTo().performClick()
        compose.waitForIdle()
        assertEquals(AccountButtonTarget.Profile, state.accountButtonTarget)
        compose.onNode(hasText("Profile") and isSelectable()).assertIsSelected()
        assertEquals(AccountButtonTarget.Profile, AppPreferences(env.prefsStore).state.value.accountButtonTarget)
    }

    @Test
    fun settingsIsOneTapOn() {
        show()
        compose.onNode(hasText("Settings") and hasClickAction() and !isSelectable()).performScrollTo().performClick()
        compose.waitForIdle()
        assertEquals(listOf<SettingsPage?>(null), env.navigator.settingsPages)
    }
}

/** The Profile row on Settings: who it shows, and where it leads. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h2400dp")
class ProfileOnSettingsTest {
    @get:Rule
    val compose = createComposeRule()

    private lateinit var env: TestEnvironment

    @Before
    fun setUp() {
        Locale.setDefault(Locale.US)
        env = TestEnvironment("profile_on_settings")
    }

    @After
    fun tearDown() = env.close()

    @Test
    fun withoutAProfileTheRowInvitesOne_andOpensTheProfile() {
        compose.setContent { env.Content { SettingsScreen() } }
        compose.waitForIdle()
        compose.onNodeWithText("Set up your profile").performClick()
        compose.waitForIdle()
        assertEquals(1, env.navigator.profileOpened)
    }

    @Test
    fun withAProfileTheRowIsTheName_andSaysWhenTheAccountButtonOpensTheProfile() {
        env.preferences.setProfile("Ada", "")
        env.preferences.setAccountButtonTarget(AccountButtonTarget.Profile)
        compose.setContent { env.Content { SettingsScreen() } }
        compose.waitForIdle()
        compose.onNodeWithText("Ada").assertExists()
        compose.onNodeWithText("Your name and picture · Account button opens Profile").assertExists()
        compose.onNodeWithText("Set up your profile").assertDoesNotExist()
    }

    @Test
    fun searchFindsTheProfileAndTheAccountButton() {
        for (query in listOf("profile", "avatar", "emoji", "account button", "name")) {
            assertTrue(SettingsCatalog.search(query).any { it.special == SettingsSpecial.Profile }, query)
        }
        assertEquals(SettingsSpecial.Profile, SettingsCatalog.search("account button").single { it.title == "Account button" }.special)
    }
}

class ProfileTextTest {
    @Test
    fun theInitialIsTheFirstLetterOrDigit_andNothingWhenThereIsNone() {
        assertEquals("A", profileInitial("ada"))
        assertEquals("É", profileInitial("  éric"))
        assertEquals("7", profileInitial("7 days"))
        assertNull(profileInitial(""))
        assertNull(profileInitial("   "))
        assertNull(profileInitial("🙂 smile"), "an emoji in the name is not a letter")
    }

    @Test
    fun aNameIsNeverCutThroughACharacterThatTakesTwoUnits() {
        assertEquals("short", capped("short"))
        val long = "a".repeat(MAX_PROFILE_NAME - 1) + "😀"
        assertEquals("a".repeat(MAX_PROFILE_NAME - 1), capped(long), "the emoji is dropped whole, not left as half of itself")
        assertEquals(MAX_PROFILE_NAME, capped("b".repeat(MAX_PROFILE_NAME + 5)).length)
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ProfileStorageTest {
    private lateinit var store: android.content.SharedPreferences
    private lateinit var prefs: AppPreferences

    @Before
    fun setUp() {
        store = ApplicationProvider.getApplicationContext<Context>().getSharedPreferences("profile_storage", Context.MODE_PRIVATE)
        store.edit().clear().commit()
        prefs = AppPreferences(store)
    }

    @Test
    fun theNameIsTrimmedAndCapped_andOnlyARealSingleEmojiIsKept() {
        prefs.setProfile("   " + "x".repeat(MAX_PROFILE_NAME + 20), "🍕")
        assertEquals("x".repeat(MAX_PROFILE_NAME), prefs.state.value.profileName)
        assertEquals("🍕", prefs.state.value.profileEmoji)

        prefs.setProfile("Ada", "not an emoji")
        assertEquals("", prefs.state.value.profileEmoji, "text is not a picture")
        prefs.setProfile("Ada", "🍕🍔")
        assertEquals("", prefs.state.value.profileEmoji, "two emoji are not one")
    }

    @Test
    fun theProfileSurvivesARestart() {
        prefs.setProfile("Ada", "🍕")
        prefs.setAccountButtonTarget(AccountButtonTarget.Profile)
        val again = AppPreferences(store).state.value
        assertEquals("Ada", again.profileName)
        assertEquals("🍕", again.profileEmoji)
        assertEquals(AccountButtonTarget.Profile, again.accountButtonTarget)
    }

    @Test
    fun resettingSettingsKeepsWhoTheUserIs_butPointsTheAccountButtonBackAtSettings() {
        prefs.setProfile("Ada", "🍕")
        prefs.setAccountButtonTarget(AccountButtonTarget.Profile)
        prefs.resetToDefaults()
        val after = AppPreferences(store).state.value
        assertEquals("Ada", after.profileName)
        assertEquals("🍕", after.profileEmoji)
        assertEquals(AccountButtonTarget.Settings, after.accountButtonTarget)
        assertEquals("Ada", prefs.state.value.profileName)
    }
}
