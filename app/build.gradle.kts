plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.dataeater.app"
    testBuildType = providers.gradleProperty("deviceTestBuildType").getOrElse("research")
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.dataeater.app"
        minSdk = 26
        targetSdk = 37
        versionCode = 8
        versionName = "1.4.2"
        // LiteRT-LM ships native inference only for these 64-bit ABIs.
        // Avoid an installable 32-bit APK that would fail when loading AI.
        ndk { abiFilters += listOf("arm64-v8a", "x86_64") }

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        create("research") {
            initWith(getByName("debug"))
            applicationIdSuffix = ".research"
            matchingFallbacks += listOf("debug")
        }
        release {
            optimization {
                // Partial dependency optimization caused a Kotlin/AndroidX
                // IllegalAccessError during startup on the device.
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    // See libs.versions.toml for why this one was added.
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.litertlm.android)
    // On-device selectable PDF text extraction; no OCR or native MuPDF bundle.
    implementation("com.tom-roush:pdfbox-android:2.0.27.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.6.1")
    testImplementation(libs.junit)
    // Real org.json for JVM unit tests. The android.jar version is a stub
    // that throws "not mocked", which would make the licence tests
    // impossible. Test-only: it is not part of the APK.
    testImplementation(libs.org.json)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}