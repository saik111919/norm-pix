package com.example.normpix.wallet

import androidx.fragment.app.FragmentActivity

/** The activity currently on screen, for services that need one (biometric prompt, NFC reader mode). */
class ActivityHost {
    @Volatile
    var activity: FragmentActivity? = null

    /** NormPix's screen is in front (started and not yet stopped): a reminder coming due then is shown by the app itself. */
    @Volatile
    var visible: Boolean = false
}
