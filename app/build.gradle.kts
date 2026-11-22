@file:Suppress("UnstableApiUsage")

plugins {
    id("com.android.application")
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.parcelize)
    alias(libs.plugins.ksp)
}

setupApp()

kotlin {
    jvmToolchain(21)
}

android {
    namespace = "io.nekohasekai.sagernet"

    defaultConfig {
        vectorDrawables.useSupportLibrary = true
    }

    androidResources {
        localeFilters += listOf("en", "zh-rCN", "zh-rTW", "in", "ja", "ru", "tr")
        generateLocaleConfig = true
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
    }

    ksp {
        arg("room.incremental", "true")
        arg("room.schemaLocation", "$projectDir/schemas")
    }

    bundle {
        language {
            enableSplit = false
        }
    }

    buildFeatures {
        buildConfig = true
        viewBinding = true
        compose = true
        aidl = true
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }
}

dependencies {
    implementation(fileTree("libs"))

    // Kotlin & Coroutines
    implementation(libs.kotlinx.coroutines.android)

    // AndroidX Core & UI
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.browser)
    implementation(libs.androidx.swiperefreshlayout)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.work.multiprocess)

    // Jetpack Compose & Material 3 Expressive
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.bundles.compose)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // Google Material & Utilities
    implementation(libs.google.material)
    implementation(libs.google.gson)
    implementation(libs.google.guava)

    // Preference & Widgets
    implementation(libs.preferencex)
    implementation(libs.preferencex.simplemenu)
    implementation(libs.recyclerview.fastscroll) {
        exclude(group = "androidx.recyclerview")
        exclude(group = "androidx.appcompat")
    }

    // Media, Editor & Tools
    implementation(libs.lottie)
    implementation(libs.glide)
    implementation(libs.ucrop)
    implementation(libs.speedviewlib)
    implementation(libs.zxing.lite)
    implementation(libs.bundles.editorkit)

    // Networking & Serialization
    implementation(libs.okhttp)
    implementation(libs.snakeyaml)
    implementation(libs.process.phoenix)
    implementation(libs.kryo)
    implementation(libs.ini4j)

    // Room & Migrations
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.roomigrant.lib)
    ksp(libs.roomigrant.compiler)

    // Desugaring
    coreLibraryDesugaring(libs.desugar.jdk.libs)
}
