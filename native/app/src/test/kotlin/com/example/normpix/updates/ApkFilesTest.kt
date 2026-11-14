package com.example.normpix.updates

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Which file names are NormPix's own releases, as CI names them and as a browser may have renamed a second copy. */
class ApkFilesTest {
    @Test
    fun `a release as CI names it is a NormPix update`() {
        for (name in listOf("NormPix-1.5.12.apk", "NormPix-1.5.0.apk", "NormPix-2.0.apk", "NormPix-10.20.30.apk")) {
            assertTrue(isNormPixApkName(name), name)
        }
    }

    @Test
    fun `so is one built for a CPU type, or the universal one`() {
        for (cpu in listOf("arm64-v8a", "armeabi-v7a", "x86_64", "universal")) {
            assertTrue(isNormPixApkName("NormPix-1.5.12-$cpu.apk"), cpu)
        }
    }

    @Test
    fun `so is a prerelease build`() {
        assertTrue(isNormPixApkName("NormPix-1.5.12-build.42.apk"))
        assertTrue(isNormPixApkName("NormPix-1.5.12-universal-build.7.apk"))
    }

    @Test
    fun `and a second copy a browser renamed`() {
        for (name in listOf(
            "NormPix-1.5.12 (1).apk",
            "NormPix-1.5.12 (12).apk",
            "NormPix-1.5.12-1.apk",
            "NormPix-1.5.12-universal (2).apk",
            "NormPix-1.5.12-build.42 (1).apk",
        )) {
            assertTrue(isNormPixApkName(name), name)
        }
    }

    @Test
    fun `only the extension may be in any case`() {
        assertTrue(isNormPixApkName("NormPix-1.5.12.APK"))
        assertTrue(isNormPixApkName("NormPix-1.5.12.Apk"))
        assertFalse(isNormPixApkName("normpix-1.5.12.apk"))
        assertFalse(isNormPixApkName("NORMPIX-1.5.12.apk"))
    }

    @Test
    fun `another app, another file type or no version is not`() {
        for (name in listOf(
            "Other-1.0.apk",
            "NormPix.apk.txt",
            "NormPix-x.apk",
            "NormPix.apk",
            "NormPix-.apk",
            "NormPix-1.5.12.apk.txt",
            "NormPix-1.5.12.zip",
            "NormPix-1.5.12-unknown.apk",
            "NormPix-1.5.12-build.apk",
            "My NormPix-1.5.12.apk",
            "NormPix-1.5.12.apk.apk",
            "NormPix-1.5.12 (a).apk",
            "",
        )) {
            assertFalse(isNormPixApkName(name), "\"$name\"")
        }
    }
}
