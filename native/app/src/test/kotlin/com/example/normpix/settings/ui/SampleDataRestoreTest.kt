package com.example.normpix.settings.ui

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import com.example.normpix.testing.TestEnvironment
import java.util.Locale
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h2400dp")
class SampleDataRestoreTest {
    @get:Rule
    val compose = createComposeRule()

    private lateinit var env: TestEnvironment

    @Before
    fun setUp() {
        Locale.setDefault(Locale.US)
        env = TestEnvironment("sample_data_restore_test")
    }

    @After
    fun tearDown() = env.close()

    @Test
    fun restoreSubtitleExplainsSampleDataWhenEnabled() {
        env.preferences.setDummyDataEnabled(true)
        compose.setContent { env.Content { DataSettingsPage() } }
        compose.waitForIdle()

        compose.onNodeWithText("Sample data is on. Turn it off to restore a backup to your actual data.")
            .performScrollTo()
            .assertExists()

        compose.onNodeWithText("Reset sample data")
            .performScrollTo()
            .assertExists()
    }

    @Test
    fun restoreSubtitleExplainsStandardRestoreWhenSampleDataIsOff() {
        env.preferences.setDummyDataEnabled(false)
        compose.setContent { env.Content { DataSettingsPage() } }
        compose.waitForIdle()

        compose.onNodeWithText("A backup of everything or a full backup (.zip), or one without pictures (.json). It's checked first, and you confirm what is replaced before anything is.")
            .performScrollTo()
            .assertExists()

        compose.onNodeWithText("Delete all finance data")
            .performScrollTo()
            .assertExists()
    }

    @Test
    fun walletImportCardsRowDisabledWhenSampleDataEnabled() {
        env.preferences.setWalletCardsEnabled(true)
        env.preferences.setDummyDataEnabled(true)
        compose.setContent { env.Content { WalletSettingsPage() } }
        compose.waitForIdle()

        compose.onNodeWithText("Import cards")
            .performScrollTo()
            .assertExists()
            .assertIsNotEnabled()
    }
}
