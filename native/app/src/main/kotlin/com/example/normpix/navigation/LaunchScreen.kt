package com.example.normpix.navigation

/**
 * What Android's own launch screen (Android 12 and later draw one over every cold start) is doing, as [com.example.normpix.MainActivity]
 * reports it to the splash that takes over from it.
 *
 * The launch screen is the first thing on screen: the system draws it before the app's process exists. The cookie's intro is
 * played there (`avd_splash.xml`), by the system, so there is no empty screen while the app starts and nothing the app is busy
 * with can make it stutter. The app is handed the screen once it has drawn; it keeps it up, draws the same settled cookie
 * under it, and takes it away - nothing changes where it was - when it is ready to open onto the app.
 */
sealed interface LaunchScreen {
    /** Android 12 or later, and the launch screen has not been handed to the app yet (it is, a frame or two after the app's first draw). */
    data object Pending : LaunchScreen

    /** Nothing to wait for or to cover: before Android 12, or a launch screen that was let go (it fades away on its own). */
    data object None : LaunchScreen

    /**
     * The launch screen is over the app, and is the cookie. Nothing visible is left to move in its intro at [introEndsAtNanos] on the
     * clock the frames come on ([System.nanoTime]; already past when the app was slower than the intro). The app has drawn the
     * settled cookie under it since its first frame and calls [release] to take it away (it fades, over the identical cookie).
     */
    class Held(val introEndsAtNanos: Long, val release: () -> Unit) : LaunchScreen
}
