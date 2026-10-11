plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.example.normpix"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.example.normpix"
        minSdk = 26
        targetSdk = 35
        versionCode = 8
        versionName = "0.0.8"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        manifestPlaceholders["appLabel"] = "NormPix"
        buildConfigField("boolean", "SECURE_WINDOW", "false")
    }

    buildTypes {
        release {
            // R8: drops unused code (most of the icon set, unused library classes) and the resources only it referenced.
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("debug")
            buildConfigField("boolean", "SECURE_WINDOW", "true")
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
            all { test -> test.maxHeapSize = "2g" }
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
