package com.example.normpix.reminders

import android.content.Context
import com.example.normpix.NormPixApplication
import com.example.normpix.settings.AppNotifications
import com.example.normpix.settings.AppPrefsState
import java.time.LocalDateTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * How a reminder that has come due reaches the person, in the hybrid the reminders use:
 *  1. **The notification, always** - the reliable default: it is posted whatever else happens, and stays until the reminder is dealt
 *     with. On a locked phone, and where Android allows and the user wants it, it also raises a full-screen alert.
 *  2. **The floating card, on top** - only while the phone is unlocked, the user turned it on, Android's "Display over other apps"
 *     is allowed, and NormPix itself is not in front ([decideOverlay]). It is never a replacement: when it is not shown the
 *     notification is all there is, and that is by design.
 */
object ReminderDelivery {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** The id of the sample reminder shown by "Try it" in settings; nothing is read from or written to storage for it. */
    const val SAMPLE_ID = -42L

    fun deliver(context: Context, reminder: Reminder) {
        val app = context.applicationContext
        ReminderNotifications.post(app, reminder)
        // Settings > Notifications > Allow notifications off means nothing alerts: the floating card is an alert too.
        if (!AppNotifications.allowed(app)) return
        val container = (app as? NormPixApplication)?.container ?: return
        val decision = decideOverlay(overlayContext(app, container.preferences.state.value, appInFront = container.host.visible))
        if (decision == OverlayDecision.Show) showCard(app, reminder)
    }

    /** Everything [decideOverlay] needs, read from the phone now. */
    fun overlayContext(context: Context, prefs: AppPrefsState, appInFront: Boolean): OverlayContext = OverlayContext(
        featureOn = prefs.reminderOverlayEnabled,
        canDrawOverlays = ReminderAccess.canDrawOverlays(context),
        screenOn = ReminderAccess.screenOn(context),
        locked = ReminderAccess.locked(context),
        appInFront = appInFront,
    )

    private fun showCard(app: Context, reminder: Reminder) {
        val id = reminder.id ?: return
        ReminderOverlay.show(app, reminder) { action -> perform(app, id, action) }
    }

    /** The card shown now, as a sample, so the person can see what it will look like (Settings > Notifications > Floating reminders). */
    fun showSample(context: Context) {
        val app = context.applicationContext
        val now = LocalDateTime.now()
        val sample = Reminder(
            id = SAMPLE_ID,
            title = "Pay electricity bill",
            // A note, so "Try it" shows what a reminder with one looks like: the card grows to hold it.
            notes = "Cash only. Take the old bill and ask for a receipt.",
            category = ReminderCategory.Payments,
            dueAt = now,
            repeatRule = RepeatRule.Once,
            createdAt = now,
            updatedAt = now,
        )
        ReminderOverlay.show(app, sample) { action -> perform(app, SAMPLE_ID, action) }
    }

    /**
     * What a card's button does, for the floating card and the full-screen one alike: Mark as paid and Snooze change the reminder
     * (and take its notification down), Open brings NormPix up on it, Dismiss only puts the card away - the reminder stays due,
     * in its notification and in Plans. The sample reminder touches nothing.
     */
    fun perform(context: Context, id: Long, action: ReminderCardAction) {
        val app = context.applicationContext
        if (id == SAMPLE_ID) return
        when (action) {
            ReminderCardAction.Dismiss -> Unit
            ReminderCardAction.Open -> {
                ReminderNotifications.cancelPosted(app, id)
                runCatching { app.startActivity(ReminderNotifications.openIntent(app, id)) }
            }
            ReminderCardAction.Done -> scope.launch {
                ReminderNotifications.cancelPosted(app, id)
                val controller = app.reminders()
                controller.ensureLoaded()
                // Deleted or finished elsewhere while the card was up: nothing left to do, and nothing to crash over.
                runCatching { controller.completeReminder(id) }
            }
            is ReminderCardAction.Snooze -> scope.launch {
                ReminderNotifications.cancelPosted(app, id)
                val controller = app.reminders()
                controller.ensureLoaded()
                runCatching { controller.snoozeReminder(id, action.by) }
            }
        }
    }
}
