plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

/** A build setting: CI passes it as an environment variable, a machine of its own as a Gradle property. */
fun setting(variable: String, property: String): String? =
    providers.environmentVariable(variable).orNull?.takeIf { it.isNotBlank() } ?: providers.gradleProperty(property).orNull

/**
 * The versionCode for a versionName: 1.0.3 is 10003, 1.12.0 is 11200. A higher version always has the higher code, so
 * every release installs over the one before it, and the in-app updater compares releases by this number, never by text.
 */
fun versionCodeOf(name: String): Int {
    val parts = Regex("""(\d+)\.(\d+)\.(\d+)""").matchEntire(name)?.groupValues?.drop(1)?.map(String::toInt)
        ?: error("versionName must be major.minor.patch, like 1.0.3, not \"$name\"")
    val (major, minor, patch) = parts
    check(minor < 100 && patch < 100) { "versionName $name: minor and patch have to stay below 100" }
    return major * 10_000 + minor * 100 + patch
}

/**
 * The GitHub repository whose releases the app updates from ("owner/name"). Its releases have to be public: the app asks
 * GitHub without signing in and carries no token. CI sets it to the repository it publishes to.
 */
val updateRepository = setting("NORMPIX_UPDATE_REPOSITORY", "normpixUpdateRepository") ?: "saik111919/norm-pix"
check(Regex("""[A-Za-z0-9-]+/[A-Za-z0-9._-]+""").matches(updateRepository)) { "Not a GitHub repository: $updateRepository" }

android {
    namespace = "com.example.normpix"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.example.normpix"
        minSdk = 26
        targetSdk = 35
        versionName = "1.0.2"
        // A release of main is built as the next free version, which CI works out from the tags (android.yml).
        setting("NORMPIX_VERSION_NAME", "normpixVersionName")?.let { versionName = it }
        versionCode = versionCodeOf(versionName!!)

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        manifestPlaceholders["appLabel"] = "NormPix"
        buildConfigField("boolean", "SECURE_WINDOW", "false")
        buildConfigField("String", "UPDATE_REPOSITORY", "\"$updateRepository\"")
        // Only a release updates itself: debug and qa builds carry another key (and qa another package), so no release
        // could ever install over them.
        buildConfigField("boolean", "UPDATES_ENABLED", "false")
    }

    signingConfigs {
        // Debug and qa builds have a key of their own, kept here so every machine signs them alike. It is never the
        // release key: a debug build can't install over the real app, nor a release over a debug build.
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
        // Every release is signed with one fixed key, so each new APK installs over the last and keeps the user's data.
        // CI reads it from repository secrets (the NORMPIX_* variables); a machine of its own from the normpix* Gradle
        // properties (~/.gradle/gradle.properties). Without either, a release is signed with the debug key above.
        val keystore = setting("NORMPIX_KEYSTORE_FILE", "normpixKeystoreFile")
        if (keystore != null) {
            create("release") {
                storeFile = file(keystore)
                storePassword = setting("NORMPIX_KEYSTORE_PASSWORD", "normpixKeystorePassword")
                keyAlias = setting("NORMPIX_KEY_ALIAS", "normpixKeyAlias")
                keyPassword = setting("NORMPIX_KEY_PASSWORD", "normpixKeyPassword")
            }
        }
    }

    buildTypes {
        release {
            // R8: drops unused code (most of the icon set, unused library classes) and the resources only it referenced.
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
            buildConfigField("boolean", "SECURE_WINDOW", "true")
            buildConfigField("boolean", "UPDATES_ENABLED", "true")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            ndk {
                // Phones only. The card-scan OCR library is ~11 MB per CPU type and x86 / x86_64 exist for emulators;
                // `-PemulatorAbi` adds x86_64 back to check a release build on an emulator.
                abiFilters += listOf("arm64-v8a", "armeabi-v7a")
                if (project.hasProperty("emulatorAbi")) abiFilters += "x86_64"
            }
        }
        create("qa") {
            initWith(getByName("debug"))
            applicationIdSuffix = ".qa"
            matchingFallbacks += listOf("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    androidResources {
        // The app's own text is English; this drops the other languages' copies of library strings.
        localeFilters += listOf("en")
    }
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            // Robolectric keeps a sandbox for each SDK the tests ask for, and the Compose tests render real bitmaps: on
            // Gradle's default 512 MB the worker thrashes the garbage collector (a screen's first load took 30 s and then
            // timed out) and, late in a full run, dies.
            all { test ->
                test.maxHeapSize = "2g"
                // `-Pscreenshots=<folder>`: ReadmeScreenshots renders the README's pictures there (it is skipped otherwise).
                providers.gradleProperty("screenshots").orNull?.let { test.systemProperty("normpix.screenshots", it) }
            }
        }
    }
}

androidComponents {
    onVariants(selector().withBuildType("release")) { variant ->
        // Store native libraries compressed in the release APK (the OCR library deflates from 11 MB to 4.4 MB). They
        // are unpacked once at install, so the download is what shrinks.
        variant.packaging.jniLibs.useLegacyPackaging.set(true)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    // The loading indicator morphs between rounded polygons (already part of the app through Material).
    implementation(libs.androidx.graphics.shapes)
    implementation(libs.material.kolor)
    implementation(libs.mlkit.text.recognition)
    implementation(libs.androidsvg)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
