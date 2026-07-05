package com.ritesh.cashiro.data.preferences

import android.content.Context
import android.os.Build
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import com.ritesh.cashiro.data.webhook.WebhookScheduledTime
import com.ritesh.cashiro.data.webhook.WebhookSettings
import com.ritesh.cashiro.data.webhook.WebhookSyncMode
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import com.ritesh.cashiro.data.model.CustomCurrency

private val Context.dataStore: DataStore<Preferences> by
        preferencesDataStore(name = "user_preferences")

@Singleton
class UserPreferencesRepository
@Inject
constructor(@ApplicationContext private val context: Context) {
    private object PreferencesKeys {
        val DARK_THEME_ENABLED = booleanPreferencesKey("dark_theme_enabled")
        val DYNAMIC_COLOR_ENABLED = booleanPreferencesKey("dynamic_color_enabled")
        val HAS_SKIPPED_SMS_PERMISSION = booleanPreferencesKey("has_skipped_sms_permission")
        val DEVELOPER_MODE_ENABLED = booleanPreferencesKey("developer_mode_enabled")
        val SYSTEM_PROMPT = stringPreferencesKey("system_prompt")
        val HAS_SHOWN_SCAN_TUTORIAL = booleanPreferencesKey("has_shown_scan_tutorial")
        val ACTIVE_DOWNLOAD_ID = longPreferencesKey("active_download_id")
        val SMS_SCAN_MONTHS = intPreferencesKey("sms_scan_months")
        val SMS_SCAN_ALL_TIME = booleanPreferencesKey("sms_scan_all_time")
        val LAST_SCAN_TIMESTAMP = longPreferencesKey("last_scan_timestamp")
        val LAST_SCAN_PERIOD = intPreferencesKey("last_scan_period")
        val BASE_CURRENCY = stringPreferencesKey("base_currency")

        // Currency Settings preferences
        val UNIFIED_CURRENCY_ENABLED = booleanPreferencesKey("unified_currency_enabled")
        val UNIFIED_CURRENCY_CODE = stringPreferencesKey("unified_currency_code")
        val DEFAULT_CURRENCY_ENABLED = booleanPreferencesKey("default_currency_enabled")
        val DEFAULT_CURRENCY_CODE = stringPreferencesKey("default_currency_code")
        val CUSTOM_CURRENCIES_JSON = stringPreferencesKey("custom_currencies_json")

        // Theme preferences
        val IS_AMOLED_MODE = booleanPreferencesKey("is_amoled_mode")
        val THEME_STYLE = stringPreferencesKey("theme_style")
        val ACCENT_COLOR = stringPreferencesKey("accent_color")

        // App Lock preferences
        val APP_LOCK_ENABLED = booleanPreferencesKey("app_lock_enabled")
        val APP_LOCK_TIMEOUT_MINUTES = intPreferencesKey("app_lock_timeout_minutes")
        val LAST_AUTH_TIMESTAMP = longPreferencesKey("last_auth_timestamp")

        // In-App Review preferences
        val FIRST_LAUNCH_TIME = longPreferencesKey("first_launch_time")
        val HAS_SHOWN_REVIEW_PROMPT = booleanPreferencesKey("has_shown_review_prompt")
        val LAST_REVIEW_PROMPT_TIME = longPreferencesKey("last_review_prompt_time")

        // Profile preferences
        val USER_NAME = stringPreferencesKey("user_name")
        val PROFILE_IMAGE_URI = stringPreferencesKey("profile_image_uri")
        val PROFILE_BACKGROUND_COLOR = intPreferencesKey("profile_background_color")
        val BANNER_IMAGE_URI = stringPreferencesKey("banner_image_uri")
        
        // Notification preferences
        val SCAN_NEW_TRANSACTIONS_ENABLED = booleanPreferencesKey("scan_new_transactions_enabled")
        val SCAN_NEW_TRANSACTIONS_ALERT_TIME = longPreferencesKey("scan_new_transactions_alert_time")
        val UPCOMING_NOTIFICATIONS_ENABLED = booleanPreferencesKey("upcoming_notifications_enabled")
        val DISABLED_SUBSCRIPTION_NOTIFICATION_IDS = androidx.datastore.preferences.core.stringSetPreferencesKey("disabled_subscription_notification_ids")
        val TEST_NOTIFICATION_ALERTS_ENABLED = booleanPreferencesKey("test_notification_alerts_enabled")
        val SHOW_BANNER_IMAGE = booleanPreferencesKey("show_banner_image")
        val NAVIGATION_BAR_STYLE = stringPreferencesKey("navigation_bar_style")
        val APP_FONT = stringPreferencesKey("app_font")
        
        // Home Widget Preferences
        val HOME_WIDGETS_ORDER = stringPreferencesKey("home_widgets_order")
        val HIDDEN_HOME_WIDGETS = androidx.datastore.preferences.core.stringSetPreferencesKey("hidden_home_widgets")
        val HIDE_NAVIGATION_LABELS = booleanPreferencesKey("hide_navigation_labels")
        val HIDE_PILL_INDICATOR = booleanPreferencesKey("hide_pill_indicator")
        val BLUR_EFFECTS = booleanPreferencesKey("blur_effects")
        val IS_SAMPLE_DATA_SEEDED = booleanPreferencesKey("is_sample_data_seeded")
        val APP_ICON = stringPreferencesKey("app_icon")
        val WEBHOOK_SYNC_MODE = stringPreferencesKey("webhook_sync_mode")
        val WEBHOOK_INTERVAL_HOURS = intPreferencesKey("webhook_interval_hours")
        val WEBHOOK_SCHEDULE_HOUR = intPreferencesKey("webhook_schedule_hour")
        val WEBHOOK_SCHEDULE_MINUTE = intPreferencesKey("webhook_schedule_minute")
        val WEBHOOK_SCHEDULED_TIMES_JSON = stringPreferencesKey("webhook_scheduled_times_json")
        // Snapshot of WebhookScheduledTime.id values that were last successfully armed with
        // AlarmManager. Read on the next applyScheduling() so deleted times can have their
        // PendingIntents cancelled even though they're no longer present in the active settings.
        val WEBHOOK_LAST_SCHEDULED_IDS = androidx.datastore.preferences.core.stringSetPreferencesKey("webhook_last_scheduled_ids")
        val LAST_CHAT_SESSION_ID = stringPreferencesKey("last_chat_session_id")
        val WEBHOOK_MODE_ENABLED = booleanPreferencesKey("webhook_mode_enabled")
        val TOKEN_INFO_ENABLED = booleanPreferencesKey("token_info_enabled")
    }

    val userPreferences: Flow<UserPreferences> =
        context.dataStore.data.map { preferences ->
            UserPreferences(
                isDarkThemeEnabled = preferences[PreferencesKeys.DARK_THEME_ENABLED],
                isDynamicColorEnabled = preferences[PreferencesKeys.DYNAMIC_COLOR_ENABLED]
                    ?: true,
                hasSkippedSmsPermission =
                    preferences[PreferencesKeys.HAS_SKIPPED_SMS_PERMISSION] ?: false,
                isDeveloperModeEnabled = preferences[PreferencesKeys.DEVELOPER_MODE_ENABLED]
                    ?: false,
                isWebhookModeEnabled = preferences[PreferencesKeys.WEBHOOK_MODE_ENABLED]
                    ?: false,
                isTokenInfoEnabled = preferences[PreferencesKeys.TOKEN_INFO_ENABLED]
                    ?: false,
                hasShownScanTutorial = preferences[PreferencesKeys.HAS_SHOWN_SCAN_TUTORIAL]
                    ?: false,
                smsScanMonths = preferences[PreferencesKeys.SMS_SCAN_MONTHS]
                    ?: 3,
                smsScanAllTime = preferences[PreferencesKeys.SMS_SCAN_ALL_TIME]
                    ?: true,
                baseCurrency = preferences[PreferencesKeys.BASE_CURRENCY]
                    ?: "INR",
                isAmoledMode = preferences[PreferencesKeys.IS_AMOLED_MODE] ?: false,
                userName = preferences[PreferencesKeys.USER_NAME] ?: "User",
                profileImageUri = preferences[PreferencesKeys.PROFILE_IMAGE_URI],
                profileBackgroundColor =
                    preferences[PreferencesKeys.PROFILE_BACKGROUND_COLOR] ?: 0,
                bannerImageUri = preferences[PreferencesKeys.BANNER_IMAGE_URI],
                showBannerImage = preferences[PreferencesKeys.SHOW_BANNER_IMAGE] ?: false,
                navigationBarStyle = try {
                    NavigationBarStyle.valueOf(
                        preferences[PreferencesKeys.NAVIGATION_BAR_STYLE] ?: NavigationBarStyle.FLOATING.name
                    )
                } catch (e: Exception) {
                    NavigationBarStyle.FLOATING
                },
                appFont = try {
                    AppFont.valueOf(
                        preferences[PreferencesKeys.APP_FONT] ?: AppFont.SYSTEM.name
                    )
                } catch (e: Exception) {
                    AppFont.SYSTEM

                },
                themeStyle = try {
                    ThemeStyle.valueOf(
                        preferences[PreferencesKeys.THEME_STYLE] ?: ThemeStyle.DYNAMIC.name
                    )
                } catch (e: Exception) {
                    ThemeStyle.DYNAMIC
                },
                accentColor = try {
                    AccentColor.valueOf(
                        preferences[PreferencesKeys.ACCENT_COLOR] ?: AccentColor.BLUE.name
                    )
                } catch (e: Exception) {
                    AccentColor.BLUE
                },
                hideNavigationLabels = preferences[PreferencesKeys.HIDE_NAVIGATION_LABELS] ?: false,
                hidePillIndicator = preferences[PreferencesKeys.HIDE_PILL_INDICATOR] ?: false,
                blurEffects = preferences[PreferencesKeys.BLUR_EFFECTS] ?: (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S),
                isSampleDataSeeded = preferences[PreferencesKeys.IS_SAMPLE_DATA_SEEDED] ?: false,
                appIcon = try {
                    AppIcon.valueOf(
                        preferences[PreferencesKeys.APP_ICON] ?: AppIcon.ORIGINAL.name
                    )
                } catch (e: Exception) {
                    AppIcon.ORIGINAL
                },
                unifiedCurrencyEnabled = preferences[PreferencesKeys.UNIFIED_CURRENCY_ENABLED] ?: false,
                unifiedCurrencyCode = preferences[PreferencesKeys.UNIFIED_CURRENCY_CODE],
                defaultCurrencyEnabled = preferences[PreferencesKeys.DEFAULT_CURRENCY_ENABLED] ?: false,
                defaultCurrencyCode = preferences[PreferencesKeys.DEFAULT_CURRENCY_CODE]
            )
        }

    val baseCurrency: Flow<String> =
        context.dataStore.data.map { preferences ->
            preferences[PreferencesKeys.BASE_CURRENCY] ?: "INR"
        }

    // Currency Settings flows
    val unifiedCurrencyEnabled: Flow<Boolean> =
        context.dataStore.data.map { preferences ->
            preferences[PreferencesKeys.UNIFIED_CURRENCY_ENABLED] ?: false
        }

    val unifiedCurrencyCode: Flow<String?> =
        context.dataStore.data.map { preferences ->
            preferences[PreferencesKeys.UNIFIED_CURRENCY_CODE]
        }

    val defaultCurrencyEnabled: Flow<Boolean> =
        context.dataStore.data.map { preferences ->
            preferences[PreferencesKeys.DEFAULT_CURRENCY_ENABLED] ?: false
        }

    val defaultCurrencyCode: Flow<String?> =
        context.dataStore.data.map { preferences ->
            preferences[PreferencesKeys.DEFAULT_CURRENCY_CODE]
        }

    val customCurrencies: Flow<List<CustomCurrency>> =
        context.dataStore.data.map { preferences ->
            val json = preferences[PreferencesKeys.CUSTOM_CURRENCIES_JSON]
            if (json != null) {
                runCatching {
                    Json.decodeFromString<List<CustomCurrency>>(json)
                }.getOrElse { emptyList() }
            } else {
                emptyList()
            }
        }

    suspend fun addCustomCurrency(currency: CustomCurrency) {
        context.dataStore.edit { preferences ->
            val json = preferences[PreferencesKeys.CUSTOM_CURRENCIES_JSON]
            val currentList = if (json != null) {
                runCatching { Json.decodeFromString<List<CustomCurrency>>(json) }.getOrElse { emptyList() }
            } else {
                emptyList()
            }
            val newList = currentList.filterNot { it.code.equals(currency.code, ignoreCase = true) } + currency
            preferences[PreferencesKeys.CUSTOM_CURRENCIES_JSON] = Json.encodeToString(newList)
        }
    }

    val webhookSettings: Flow<WebhookSettings> =
        context.dataStore.data.map { preferences ->
            val storedMode = preferences[PreferencesKeys.WEBHOOK_SYNC_MODE]
            val syncMode = WebhookSyncMode.entries.firstOrNull { it.name == storedMode }
                ?: WebhookSyncMode.INTERVAL

            val timesJson = preferences[PreferencesKeys.WEBHOOK_SCHEDULED_TIMES_JSON]
            val scheduledTimes: List<WebhookScheduledTime> = if (timesJson != null) {
                runCatching {
                    Json.decodeFromString<List<WebhookScheduledTime>>(timesJson)
                }.getOrElse { defaultScheduledTimes() }
            } else {
                // Migrate from legacy single hour/minute keys
                val legacyHour = preferences[PreferencesKeys.WEBHOOK_SCHEDULE_HOUR]
                val legacyMinute = preferences[PreferencesKeys.WEBHOOK_SCHEDULE_MINUTE]
                if (legacyHour != null) {
                    listOf(WebhookScheduledTime(hour = legacyHour, minute = legacyMinute ?: 0))
                } else {
                    defaultScheduledTimes()
                }
            }

            WebhookSettings(
                syncMode = syncMode,
                intervalHours = preferences[PreferencesKeys.WEBHOOK_INTERVAL_HOURS] ?: 6,
                scheduledTimes = scheduledTimes
            )
        }

    private fun defaultScheduledTimes(): List<WebhookScheduledTime> = listOf(
        WebhookScheduledTime(hour = 8, minute = 0),
        WebhookScheduledTime(hour = 21, minute = 0)
    )

    suspend fun updateBaseCurrency(currency: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.BASE_CURRENCY] = currency
        }
    }

    suspend fun setUnifiedCurrencyEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.UNIFIED_CURRENCY_ENABLED] = enabled
        }
    }

    suspend fun setUnifiedCurrencyCode(code: String?) {
        context.dataStore.edit { preferences ->
            if (code == null) {
                preferences.remove(PreferencesKeys.UNIFIED_CURRENCY_CODE)
            } else {
                preferences[PreferencesKeys.UNIFIED_CURRENCY_CODE] = code
            }
        }
    }

    suspend fun setDefaultCurrencyEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.DEFAULT_CURRENCY_ENABLED] = enabled
        }
    }

    suspend fun setDefaultCurrencyCode(code: String?) {
        context.dataStore.edit { preferences ->
            if (code == null) {
                preferences.remove(PreferencesKeys.DEFAULT_CURRENCY_CODE)
            } else {
                preferences[PreferencesKeys.DEFAULT_CURRENCY_CODE] = code
            }
        }
    }

    suspend fun updateWebhookSettings(settings: WebhookSettings) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.WEBHOOK_SYNC_MODE] = settings.syncMode.name
            preferences[PreferencesKeys.WEBHOOK_INTERVAL_HOURS] = settings.intervalHours
            preferences[PreferencesKeys.WEBHOOK_SCHEDULED_TIMES_JSON] =
                Json.encodeToString(settings.scheduledTimes)
        }
    }

    val webhookLastScheduledIds: Flow<Set<String>> =
        context.dataStore.data.map { it[PreferencesKeys.WEBHOOK_LAST_SCHEDULED_IDS].orEmpty() }

    suspend fun setWebhookLastScheduledIds(ids: Set<String>) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.WEBHOOK_LAST_SCHEDULED_IDS] = ids
        }
    }

    val isDeveloperModeEnabled: Flow<Boolean> =
        context.dataStore.data.map { preferences ->
            preferences[PreferencesKeys.DEVELOPER_MODE_ENABLED] ?: false
        }

    suspend fun updateDarkThemeEnabled(enabled: Boolean?) {
        context.dataStore.edit { preferences ->
            if (enabled == null) {
                preferences.remove(PreferencesKeys.DARK_THEME_ENABLED)
            } else {
                preferences[PreferencesKeys.DARK_THEME_ENABLED] = enabled
            }
        }
    }

    suspend fun updateDynamicColorEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.DYNAMIC_COLOR_ENABLED] = enabled
        }
    }

    suspend fun updateAmoledMode(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.IS_AMOLED_MODE] = enabled
        }
    }

    suspend fun updateThemeStyle(style: ThemeStyle) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.THEME_STYLE] = style.name
        }
    }

    suspend fun updateAccentColor(color: AccentColor) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.ACCENT_COLOR] = color.name
        }
    }

    suspend fun updateSkippedSmsPermission(skipped: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.HAS_SKIPPED_SMS_PERMISSION] = skipped
        }
    }

    suspend fun setDeveloperModeEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.DEVELOPER_MODE_ENABLED] = enabled
        }
    }

    val isWebhookModeEnabled: Flow<Boolean> =
        context.dataStore.data.map { preferences ->
            preferences[PreferencesKeys.WEBHOOK_MODE_ENABLED] ?: false
        }

    suspend fun setWebhookModeEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.WEBHOOK_MODE_ENABLED] = enabled
        }
    }

    val isTokenInfoEnabled: Flow<Boolean> =
        context.dataStore.data.map { preferences ->
            preferences[PreferencesKeys.TOKEN_INFO_ENABLED] ?: false
        }

    suspend fun setTokenInfoEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.TOKEN_INFO_ENABLED] = enabled
        }
    }

    suspend fun updateSystemPrompt(prompt: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.SYSTEM_PROMPT] = prompt
        }
    }

    fun getSystemPrompt(): Flow<String?> =
            context.dataStore.data.map { preferences -> preferences[PreferencesKeys.SYSTEM_PROMPT] }

    suspend fun saveLastChatSessionId(sessionId: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.LAST_CHAT_SESSION_ID] = sessionId
        }
    }

    suspend fun getLastChatSessionId(): String? {
        return context.dataStore.data.first()[PreferencesKeys.LAST_CHAT_SESSION_ID]
    }

    suspend fun markScanTutorialShown() {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.HAS_SHOWN_SCAN_TUTORIAL] = true
        }
    }

    suspend fun saveActiveDownloadId(id: Long) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.ACTIVE_DOWNLOAD_ID] = id
        }
    }

    suspend fun getActiveDownloadId(): Long? {
        return context.dataStore
                .data
                .map { preferences -> preferences[PreferencesKeys.ACTIVE_DOWNLOAD_ID] }
                .first()
    }

    suspend fun clearActiveDownloadId() {
        context.dataStore.edit { preferences ->
            preferences.remove(PreferencesKeys.ACTIVE_DOWNLOAD_ID)
        }
    }

    val smsScanMonths: Flow<Int> =
            context.dataStore.data.map { preferences ->
                preferences[PreferencesKeys.SMS_SCAN_MONTHS] ?: 3 // Default to 3 months
            }

    suspend fun updateSmsScanMonths(months: Int) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.SMS_SCAN_MONTHS] = months
        }
    }

    suspend fun getSmsScanMonths(): Int {
        return context.dataStore
                .data
                .map { preferences -> preferences[PreferencesKeys.SMS_SCAN_MONTHS] ?: 3 }
                .first()
    }

    val smsScanAllTime: Flow<Boolean> =
            context.dataStore.data.map { preferences ->
                preferences[PreferencesKeys.SMS_SCAN_ALL_TIME] ?: true
            }

    suspend fun updateSmsScanAllTime(allTime: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.SMS_SCAN_ALL_TIME] = allTime
        }
    }

    suspend fun getSmsScanAllTime(): Boolean {
        return context.dataStore
                .data
                .map { preferences -> preferences[PreferencesKeys.SMS_SCAN_ALL_TIME] ?: true }
                .first()
    }

    suspend fun setLastScanTimestamp(timestamp: Long) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.LAST_SCAN_TIMESTAMP] = timestamp
        }
    }

    suspend fun setLastScanPeriod(period: Int) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.LAST_SCAN_PERIOD] = period
        }
    }

    suspend fun setFirstLaunchTime(timestamp: Long) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.FIRST_LAUNCH_TIME] = timestamp
        }
    }

    suspend fun hasShownReviewPrompt(): Boolean {
        return context.dataStore
                .data
                .map { preferences ->
                    preferences[PreferencesKeys.HAS_SHOWN_REVIEW_PROMPT] ?: false
                }
                .first()
    }

    suspend fun markReviewPromptShown() {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.HAS_SHOWN_REVIEW_PROMPT] = true
            preferences[PreferencesKeys.LAST_REVIEW_PROMPT_TIME] = System.currentTimeMillis()
        }
    }

    // Flow methods for backup/restore
    fun getLastScanTimestamp(): Flow<Long?> =
            context.dataStore.data.map { preferences ->
                preferences[PreferencesKeys.LAST_SCAN_TIMESTAMP]
            }

    fun getLastScanPeriod(): Flow<Int?> =
            context.dataStore.data.map { preferences ->
                preferences[PreferencesKeys.LAST_SCAN_PERIOD]
            }

    fun getFirstLaunchTime(): Flow<Long?> =
            context.dataStore.data.map { preferences ->
                preferences[PreferencesKeys.FIRST_LAUNCH_TIME]
            }

    fun getHasShownReviewPrompt(): Flow<Boolean> =
            context.dataStore.data.map { preferences ->
                preferences[PreferencesKeys.HAS_SHOWN_REVIEW_PROMPT] ?: false
            }

    fun getLastReviewPromptTime(): Flow<Long?> =
            context.dataStore.data.map { preferences ->
                preferences[PreferencesKeys.LAST_REVIEW_PROMPT_TIME]
            }

    // Update methods for import
    suspend fun updateDarkTheme(enabled: Boolean?) {
        updateDarkThemeEnabled(enabled)
    }

    suspend fun updateDynamicColor(enabled: Boolean) {
        updateDynamicColorEnabled(enabled)
    }

    suspend fun updateHasSkippedSmsPermission(skipped: Boolean) {
        updateSkippedSmsPermission(skipped)
    }

    suspend fun updateDeveloperMode(enabled: Boolean) {
        setDeveloperModeEnabled(enabled)
    }

    suspend fun updateLastScanTimestamp(timestamp: Long) {
        setLastScanTimestamp(timestamp)
    }

    suspend fun updateLastScanPeriod(period: Int) {
        setLastScanPeriod(period)
    }

    suspend fun updateFirstLaunchTime(timestamp: Long) {
        setFirstLaunchTime(timestamp)
    }

    suspend fun updateHasShownScanTutorial(shown: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.HAS_SHOWN_SCAN_TUTORIAL] = shown
        }
    }

    suspend fun updateHasShownReviewPrompt(shown: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.HAS_SHOWN_REVIEW_PROMPT] = shown
        }
    }

    suspend fun updateLastReviewPromptTime(timestamp: Long) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.LAST_REVIEW_PROMPT_TIME] = timestamp
        }
    }

    // App Lock methods
    val isAppLockEnabled: Flow<Boolean> =
            context.dataStore.data.map { preferences ->
                preferences[PreferencesKeys.APP_LOCK_ENABLED] ?: false
            }

    suspend fun setAppLockEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.APP_LOCK_ENABLED] = enabled
        }
    }

    /**
     * Atomically updates both app lock enabled state and authentication timestamp. This prevents
     * race conditions where the flow sees enabled=true but timestamp=0.
     */
    suspend fun setAppLockEnabledWithTimestamp(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.APP_LOCK_ENABLED] = enabled
            if (enabled) {
                preferences[PreferencesKeys.LAST_AUTH_TIMESTAMP] = System.currentTimeMillis()
            }
        }
    }

    val appLockTimeoutMinutes: Flow<Int> =
            context.dataStore.data.map { preferences ->
                preferences[PreferencesKeys.APP_LOCK_TIMEOUT_MINUTES] ?: 1 // Default to 1 minute
            }

    suspend fun setAppLockTimeoutMinutes(minutes: Int) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.APP_LOCK_TIMEOUT_MINUTES] = minutes
        }
    }

    /**
     * Atomically updates timeout and authentication timestamp. This prevents immediate lock when
     * changing timeout by resetting the auth time.
     */
    suspend fun setAppLockTimeoutWithTimestamp(minutes: Int) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.APP_LOCK_TIMEOUT_MINUTES] = minutes
            preferences[PreferencesKeys.LAST_AUTH_TIMESTAMP] = System.currentTimeMillis()
        }
    }

    suspend fun getAppLockTimeoutMinutes(): Int {
        return context.dataStore
                .data
                .map { preferences -> preferences[PreferencesKeys.APP_LOCK_TIMEOUT_MINUTES] ?: 1 }
                .first()
    }

    suspend fun setLastAuthTimestamp(timestamp: Long) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.LAST_AUTH_TIMESTAMP] = timestamp
        }
    }

    suspend fun getLastAuthTimestamp(): Long {
        return context.dataStore
                .data
                .map { preferences -> preferences[PreferencesKeys.LAST_AUTH_TIMESTAMP] ?: 0L }
                .first()
    }

    fun getLastAuthTimestampFlow(): Flow<Long> =
            context.dataStore.data.map { preferences ->
                preferences[PreferencesKeys.LAST_AUTH_TIMESTAMP] ?: 0L
            }

    suspend fun updateUserName(name: String) {
        context.dataStore.edit { preferences -> preferences[PreferencesKeys.USER_NAME] = name }
    }

    suspend fun updateProfileImageUri(uri: String?) {
        context.dataStore.edit { preferences ->
            if (uri == null) {
                preferences.remove(PreferencesKeys.PROFILE_IMAGE_URI)
            } else {
                preferences[PreferencesKeys.PROFILE_IMAGE_URI] = uri
            }
        }
    }

    suspend fun updateProfileBackgroundColor(color: Int) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.PROFILE_BACKGROUND_COLOR] = color
        }
    }

    suspend fun updateBannerImageUri(uri: String?) {
        context.dataStore.edit { preferences ->
            if (uri == null) {
                preferences.remove(PreferencesKeys.BANNER_IMAGE_URI)
            } else {
                preferences[PreferencesKeys.BANNER_IMAGE_URI] = uri
            }
        }
    }

    suspend fun updateShowBannerImage(show: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.SHOW_BANNER_IMAGE] = show
        }
    }

    // Notification Preferences
    val scanNewTransactionsEnabled: Flow<Boolean> =
        context.dataStore.data.map { preferences ->
            preferences[PreferencesKeys.SCAN_NEW_TRANSACTIONS_ENABLED] ?: true
        }

    suspend fun setScanNewTransactionsEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.SCAN_NEW_TRANSACTIONS_ENABLED] = enabled
        }
    }

    val scanNewTransactionsAlertTime: Flow<Long> =
        context.dataStore.data.map { preferences ->
            preferences[PreferencesKeys.SCAN_NEW_TRANSACTIONS_ALERT_TIME] ?: 1200L // Default 20:00 (1200 minutes)
        }

    suspend fun setScanNewTransactionsAlertTime(minutes: Long) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.SCAN_NEW_TRANSACTIONS_ALERT_TIME] = minutes
        }
    }

    val upcomingNotificationsEnabled: Flow<Boolean> =
        context.dataStore.data.map { preferences ->
            preferences[PreferencesKeys.UPCOMING_NOTIFICATIONS_ENABLED] ?: true
        }

    suspend fun setUpcomingNotificationsEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.UPCOMING_NOTIFICATIONS_ENABLED] = enabled
        }
    }

    val disabledSubscriptionNotificationIds: Flow<Set<String>> =
        context.dataStore.data.map { preferences ->
            preferences[PreferencesKeys.DISABLED_SUBSCRIPTION_NOTIFICATION_IDS] ?: emptySet()
        }

    suspend fun toggleSubscriptionNotification(id: Long, enabled: Boolean) {
        context.dataStore.edit { preferences ->
            val current = preferences[PreferencesKeys.DISABLED_SUBSCRIPTION_NOTIFICATION_IDS] ?: emptySet()
            if (enabled) {
                preferences[PreferencesKeys.DISABLED_SUBSCRIPTION_NOTIFICATION_IDS] = current - id.toString()
            } else {
                preferences[PreferencesKeys.DISABLED_SUBSCRIPTION_NOTIFICATION_IDS] = current + id.toString()
            }
        }
    }

    val isTestNotificationAlertsEnabled: Flow<Boolean> =
        context.dataStore.data.map { preferences ->
            preferences[PreferencesKeys.TEST_NOTIFICATION_ALERTS_ENABLED] ?: false
        }

    suspend fun setTestNotificationAlertsEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.TEST_NOTIFICATION_ALERTS_ENABLED] = enabled
        }
    }

    suspend fun updateNavigationBarStyle(style: NavigationBarStyle) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.NAVIGATION_BAR_STYLE] = style.name
        }
    }

    suspend fun updateAppFont(font: AppFont) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.APP_FONT] = font.name
        }
    }

    // Home Widget Preferences
    val homeWidgetsOrder: Flow<List<HomeWidget>> =
        context.dataStore.data.map { preferences ->
            val orderString = preferences[PreferencesKeys.HOME_WIDGETS_ORDER]
            if (orderString != null) {
                orderString.split(",")
                    .mapNotNull { HomeWidget.fromName(it) }
            } else {
                emptyList() // Empty list means use default order logic in ViewModel
            }
        }

    val hiddenHomeWidgets: Flow<Set<HomeWidget>> =
        context.dataStore.data.map { preferences ->
            preferences[PreferencesKeys.HIDDEN_HOME_WIDGETS]
                ?.mapNotNull { HomeWidget.fromName(it) }
                ?.toSet()
                ?: emptySet()
        }

    suspend fun updateHomeWidgetsOrder(order: List<HomeWidget>) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.HOME_WIDGETS_ORDER] = order.joinToString(",") { it.name }
        }
    }

    suspend fun updateHiddenHomeWidgets(hidden: Set<HomeWidget>) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.HIDDEN_HOME_WIDGETS] = hidden.map { it.name }.toSet()
        }
    }

    suspend fun updateHideNavigationLabels(hide: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.HIDE_NAVIGATION_LABELS] = hide
        }
    }

    suspend fun updateHidePillIndicator(hide: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.HIDE_PILL_INDICATOR] = hide
        }
    }

    suspend fun updateBlurEffects(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.BLUR_EFFECTS] = enabled
        }
    }

    val isSampleDataSeeded: Flow<Boolean> =
        context.dataStore.data.map { preferences ->
            preferences[PreferencesKeys.IS_SAMPLE_DATA_SEEDED] ?: false
        }

    suspend fun setSampleDataSeeded(seeded: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.IS_SAMPLE_DATA_SEEDED] = seeded
        }
    }

    suspend fun updateAppIcon(icon: AppIcon) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.APP_ICON] = icon.name
        }
    }
}

data class UserPreferences(
        val isDarkThemeEnabled: Boolean? = null, // null means follow system
        val isDynamicColorEnabled: Boolean = true, // Default to dynamic colors
        val hasSkippedSmsPermission: Boolean = false,
        val isDeveloperModeEnabled: Boolean = false,
        val isWebhookModeEnabled: Boolean = false,
        val isTokenInfoEnabled: Boolean = false,
        val hasShownScanTutorial: Boolean = false,
        val smsScanMonths: Int = 3, // Default to 3 months
        val smsScanAllTime: Boolean = true,
        val baseCurrency: String = "INR", // Default to INR
        val isAmoledMode: Boolean = false,
        val userName: String = "User",
        val profileImageUri: String? = null,
        val profileBackgroundColor: Int = 0,
        val bannerImageUri: String? = null,
        val showBannerImage: Boolean = false,
        val navigationBarStyle: NavigationBarStyle = NavigationBarStyle.FLOATING,
        val appFont: AppFont = AppFont.SYSTEM,
        val themeStyle: ThemeStyle = ThemeStyle.DYNAMIC,
        val accentColor: AccentColor = AccentColor.BLUE,
        val hideNavigationLabels: Boolean = false,
        val hidePillIndicator: Boolean = false,
        val blurEffects: Boolean = true,
        val isSampleDataSeeded: Boolean = false,
        val appIcon: AppIcon = AppIcon.ORIGINAL,
        // Currency Settings preferences
        val unifiedCurrencyEnabled: Boolean = false,
        val unifiedCurrencyCode: String? = null,
        val defaultCurrencyEnabled: Boolean = false,
        val defaultCurrencyCode: String? = null
)
