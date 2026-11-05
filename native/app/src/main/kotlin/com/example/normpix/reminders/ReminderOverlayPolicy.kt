package com.example.normpix.reminders

/**
 * What the phone says at the moment a reminder comes due, for deciding whether the floating card may appear over what the user
 * is doing. The caller reads each of these from Android; the decision itself ([decideOverlay]) is plain logic, so every rule has
 * a test.
 *
 * Which other app is in front is deliberately **not** one of them: knowing it takes Android's "Usage access", which is a window
 * onto what the person does with their phone and has nothing to do with money or reminders, so NormPix never asks for it.
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
 *  3. never over NormPix itself.
 */
fun decideOverlay(c: OverlayContext): OverlayDecision {
    val skip = when {
        !c.featureOn -> OverlaySkip.Off
        !c.canDrawOverlays -> OverlaySkip.NoPermission
        !c.screenOn -> OverlaySkip.ScreenOff
        c.locked -> OverlaySkip.Locked
        c.appInFront -> OverlaySkip.InApp
        else -> null
    }
    return if (skip == null) OverlayDecision.Show else OverlayDecision.Skip(skip)
}
