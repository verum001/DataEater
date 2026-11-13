plugins { alias(libs.plugins.android.library) }

// Optional local override keeps large compiler intermediates off a full workspace disk.
providers.gradleProperty("dataeaterNativeBuildDir").orNull?.let { layout.buildDirectory.set(file(it)) }
android {
    namespace = "cz.adaptech.tesseract4android"
    compileSdk = 37
    ndkVersion = "27.2.12479018"
    defaultConfig {
        minSdk = 26
        ndk { abiFilters += listOf("arm64-v8a", "x86_64") }
        buildConfigField("String", "FLAVOR", "\"standard\"")
        consumerProguardFiles("consumer-rules.pro")
        externalNativeBuild { cmake {
            arguments += listOf("-DANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON", "-DCMAKE_BUILD_TYPE=Release",
                "-DCMAKE_JOB_POOLS=compile_pool=2;link_pool=1", "-DCMAKE_JOB_POOL_COMPILE=compile_pool", "-DCMAKE_JOB_POOL_LINK=link_pool")
        } }
    }
    externalNativeBuild { cmake {
        path = file("src/main/cpp/CMakeLists.txt")
        version = "3.22.1"
        providers.gradleProperty("dataeaterNativeBuildDir").orNull?.let { buildStagingDirectory = file("$it/cmake") }
    } }
    buildFeatures { buildConfig = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}
dependencies { implementation("androidx.annotation:annotation:1.3.0") }
