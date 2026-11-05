package com.example.normpix.reminders

/**
 * What the phone says at the moment a reminder comes due, for deciding whether the floating card may appear over what the user
 * is doing. The caller reads each of these from Android; the decision itself ([decideOverlay]) is plain logic, so every rule has
 * a test.
 */
data class OverlayContext(
    /** Settings > Notifications > Floating reminders is on. */
    val featureOn: Boolean,
    /** Android's "Display over other apps" is allowed for NormPix. */
    val canDrawOverlays: Boolean,
    /** The screen is on. */
    val screenOn: Boolean,
    /** The phone is behind its lock screen (or an always-on display). */
    val locked: Boolean,
    /** NormPix itself is in front: the reminder is shown by the app's own alert there, and a card over it would be a second one. */
    val appInFront: Boolean,
    /** The user let NormPix see which app is in front (Android's "Usage access"). */
    val usageAccess: Boolean,
    /** The app in front, or null when Android cannot say. */
    val foregroundPackage: String?,
    /** Apps the card is never to appear over (banking, authenticators, anything private). */
    val excluded: Set<String>,
)

/** Why the floating card is not shown. The reminder still arrives as a notification in every one of these cases. */
enum class OverlaySkip {
    /** The switch is off. */
    Off,

    /** "Display over other apps" is not allowed. */
    NoPermission,

    /** The screen is off. */
    ScreenOff,

    /** The phone is locked: a notification (and, if allowed, a full-screen alert) is what shows there. */
    Locked,

    /** NormPix is in front. */
    InApp,

    /** Apps are excluded but NormPix has no Usage access, so it cannot tell whether one is in front. */
    NeedsUsageAccess,

    /** Apps are excluded and Android could not say which app is in front: not risked. */
    UnknownApp,

    /** An excluded app is in front. */
    ExcludedApp,
}

/** Show the card, or [skip] it for [reason] (the notification is what the user gets). */
sealed interface OverlayDecision {
    data object Show : OverlayDecision

    data class Skip(val reason: OverlaySkip) : OverlayDecision
}

/**
 * Whether the floating card may appear now. The rules, in the order they are checked:
 *  1. only when the user turned it on and Android allows it;
 *  2. only on an unlocked phone with its screen on (a locked phone gets the notification and the full-screen alert);
 *  3. never over NormPix itself;
 *  4. never over an excluded app - and with any app excluded, never when it cannot be told which app is in front: the choice
 *     the user made not to be shown over a banking app is kept even when the phone will not say what is in front.
 */
fun decideOverlay(c: OverlayContext): OverlayDecision {
    val skip = when {
        !c.featureOn -> OverlaySkip.Off
        !c.canDrawOverlays -> OverlaySkip.NoPermission
        !c.screenOn -> OverlaySkip.ScreenOff
        c.locked -> OverlaySkip.Locked
        c.appInFront -> OverlaySkip.InApp
        c.excluded.isEmpty() -> null
        !c.usageAccess -> OverlaySkip.NeedsUsageAccess
        c.foregroundPackage == null -> OverlaySkip.UnknownApp
        c.foregroundPackage in c.excluded -> OverlaySkip.ExcludedApp
        else -> null
    }
    return if (skip == null) OverlayDecision.Show else OverlayDecision.Skip(skip)
}
