package com.example.normpix.updates

import android.content.pm.PackageInstaller
import com.example.normpix.updates.Releases.installed
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull

/** What is checked of a downloaded APK before Android is asked to install it, and what the installer's answers mean. */
class ApkChecksTest {
    private val release = UpdateRelease(
        versionCode = 10003, versionName = "1.0.3", tag = "v1.0.3", publishedAt = null, notes = "", apkName = "NormPix-1.0.3.apk",
        apkUrl = "https://github.com/owner/normpix/releases/download/v1.0.3/NormPix-1.0.3.apk", size = 10, sha256 = "0".repeat(64),
        mandatory = false,
    )
    private val ours = setOf("release-key")

    private fun problem(facts: ApkFacts?, installedSigners: Set<String>? = ours) = apkProblem(facts, release, installed, installedSigners)

    @Test
    fun `the release, signed like the installed app, is fine`() {
        assertNull(problem(ApkFacts(Releases.APP_ID, 10003, ours)))
        // A key rotated since: the new APK vouches for the installed one's key in its history.
        assertNull(problem(ApkFacts(Releases.APP_ID, 10003, setOf("new-key", "release-key"))))
        // Where Android can't tell signatures (before Android 9) the installer itself decides.
        assertNull(problem(ApkFacts(Releases.APP_ID, 10003, null)))
        assertNull(problem(ApkFacts(Releases.APP_ID, 10003, ours), installedSigners = null))
    }

    @Test
    fun `another app, an older or same version, or another key is refused`() {
        assertEquals(UpdateProblem.NotAnApk, problem(null))
        assertEquals(UpdateProblem.WrongPackage("com.example.other"), problem(ApkFacts("com.example.other", 10003, ours)))
        assertEquals(UpdateProblem.NotNewer(10002), problem(ApkFacts(Releases.APP_ID, 10002, ours)))
        assertEquals(UpdateProblem.NotNewer(9), problem(ApkFacts(Releases.APP_ID, 9, ours)))
        assertIs<UpdateProblem.BadRelease>(problem(ApkFacts(Releases.APP_ID, 10004, ours)), "newer, but not the release it claimed to be")
        assertEquals(UpdateProblem.SignatureMismatch, problem(ApkFacts(Releases.APP_ID, 10003, setOf("someone-else"))))
        // None of these is worth trying again with the same file.
        listOf(UpdateProblem.WrongPackage("x"), UpdateProblem.NotNewer(1), UpdateProblem.SignatureMismatch).forEach { assertFalse(it.canRetry, it.toString()) }
    }

    @Test
    fun `what the installer's answers mean`() {
        assertEquals(InstallOutcome.Success, installOutcome(PackageInstaller.STATUS_SUCCESS, null))
        assertEquals(InstallOutcome.Cancelled, installOutcome(PackageInstaller.STATUS_FAILURE_ABORTED, "User rejected permissions"))
        assertEquals(InstallOutcome.Failed(UpdateProblem.InstallBlocked), installOutcome(PackageInstaller.STATUS_FAILURE_BLOCKED, null))
        assertEquals(InstallOutcome.Failed(UpdateProblem.InstallStorage), installOutcome(PackageInstaller.STATUS_FAILURE_STORAGE, null))
        assertEquals(InstallOutcome.Failed(UpdateProblem.InstallIncompatible), installOutcome(PackageInstaller.STATUS_FAILURE_INCOMPATIBLE, "INSTALL_FAILED_NO_MATCHING_ABIS"))
        assertEquals(InstallOutcome.Failed(UpdateProblem.NotAnApk), installOutcome(PackageInstaller.STATUS_FAILURE_INVALID, "INSTALL_PARSE_FAILED_NOT_APK"))
        assertEquals(InstallOutcome.Failed(UpdateProblem.InstallFailed("oops")), installOutcome(PackageInstaller.STATUS_FAILURE, "oops"))
        assertEquals(InstallOutcome.Failed(UpdateProblem.InstallFailed(null)), installOutcome(12345, null))
    }

    @Test
    fun `a key mismatch is recognised however the installer words it`() {
        val said = listOf(
            PackageInstaller.STATUS_FAILURE_CONFLICT to "INSTALL_FAILED_UPDATE_INCOMPATIBLE: Package com.example.normpix signatures do not match newer version; ignoring!",
            PackageInstaller.STATUS_FAILURE_INCOMPATIBLE to "INSTALL_FAILED_UPDATE_INCOMPATIBLE: Existing package signatures do not match",
            PackageInstaller.STATUS_FAILURE_INVALID to "INSTALL_PARSE_FAILED_INCONSISTENT_CERTIFICATES",
        )
        for ((status, message) in said) assertEquals(InstallOutcome.Failed(UpdateProblem.SignatureMismatch), installOutcome(status, message), message)
        assertIs<UpdateProblem.NotNewer>(
            (installOutcome(PackageInstaller.STATUS_FAILURE_CONFLICT, "INSTALL_FAILED_VERSION_DOWNGRADE") as InstallOutcome.Failed).problem,
        )
    }
}
