// MikuRay's design layer, vendored verbatim.
//
// This module intentionally holds *resources only* — no Kotlin, no manifest.
// It exists so the ported UI keeps MikuRay's layouts, styles, colours,
// drawables, fonts, animations and preference screens byte-for-byte, and so a
// future re-sync against upstream MikuRay is a directory copy rather than a
// merge. The code that inflates these resources lives in :app (com.miku.ray.*
// and com.mikubox.mihomo.*) and must reference the merged R class of the app
// module, i.e. com.mikubox.mihomo.R.
import java.time.LocalDate

plugins {
    alias(libs.plugins.android.library)
    // The module compiles MikuRay's vendored sources, so it needs Kotlin too.
    alias(libs.plugins.kotlin.android)
}

android {
    // MikuRay's vendored sources address resources as `com.miku.ray.R` and their
    // binding classes as `com.miku.ray.databinding.*`. Giving this module that
    // namespace generates both, so the copied code compiles with no rewriting.
    namespace = "com.miku.ray"
    compileSdk = 36

    // The vendored sources are compiled *by this module* even though they sit
    // under app/src/main/java: the generated view bindings name MikuRay's own
    // widgets, and those widgets are part of the same sources — compiling them
    // anywhere else would mean the app module depending on itself.
    //
    // The directory is named directly (rather than a parent plus an include
    // filter, which also pulled in MikuBox's own widgets); `app` excludes the
    // same subtree, so every file is compiled exactly once.
    sourceSets.getByName("main").java.srcDir(rootProject.file("app/src/main/java/com/miku/ray"))

    defaultConfig {
        minSdk = 24

        // MikuRay's sources read these from its module's BuildConfig and R.
        // A library has no applicationId or version of its own, so the values
        // mirror the app module's — the about screen prints them. Both the tag
        // overrides and the fallback come from the same properties the app
        // module reads (-PversionNameOverride/-PversionCodeOverride, then
        // appVersionName/appVersionCode in gradle.properties), so this module
        // can no longer report a version the installed package does not carry.
        val bannerVersionName = (findProperty("versionNameOverride") as? String?)?.takeIf { it.isNotBlank() }
            ?: (findProperty("appVersionName") as? String?)?.takeIf { it.isNotBlank() }
            ?: error("Set appVersionName in gradle.properties or pass -PversionNameOverride")
        val bannerVersionCode = (findProperty("versionCodeOverride") as? String?)?.toIntOrNull()
            ?: (findProperty("appVersionCode") as? String?)?.toIntOrNull()
            ?: error("Set appVersionCode in gradle.properties or pass -PversionCodeOverride")
        buildConfigField("String", "APPLICATION_ID", "\"com.mikubox.mihomo\"")
        buildConfigField("String", "VERSION_NAME", "\"$bannerVersionName\"")
        buildConfigField("int", "VERSION_CODE", "$bannerVersionCode")
        resValue("string", "uwu_version_name", bannerVersionName)
        resValue("string", "uwu_version_code", "$bannerVersionCode")
        resValue("string", "uwu_package_name", "com.mikubox.mihomo")
        // Same build-date source as the app module's resValue, so the banner
        // reads one date no matter which module's merged entry wins.
        resValue("string", "uwu_build_date", LocalDate.now().toString())
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    // Not a code dependency: the RemixIcon vectors are referenced by the
    // copied layouts, so their resources have to travel with ours.
    api(project(":remixicon"))

    // The generated view bindings name the view classes the vendored layouts
    // use, so every library those layouts reference has to be on this module's
    // compile classpath — `api` because the app compiles the same code.
    api(libs.material)
    api(libs.androidx.appcompat)
    api(libs.androidx.constraintlayout)
    api(libs.androidx.recyclerview)
    api(libs.androidx.viewpager2)
    api(libs.androidx.preference)
    api(libs.androidx.fragment.ktx)
    api(libs.androidx.swiperefreshlayout)
    api(libs.androidx.palette)
    api(libs.flexbox)
    api(libs.recyclerview.fastscroll)
    api(libs.skydoves.colorpickerview)
    api(libs.com.airbnb.android.lottie)
    api(libs.editorkit)
    api(libs.language.base)
    api(libs.language.json)
    api(libs.coroutines.play.services)
    api(libs.glide)
    api(libs.aboutlibraries.view)

    // The rest of what MikuRay's sources import.
    api(libs.androidx.core.ktx)
    api(libs.androidx.activity.ktx)
    api(libs.androidx.lifecycle.viewmodel)
    api(libs.androidx.lifecycle.livedata)
    api(libs.androidx.lifecycle.runtime)
    api(libs.kotlinx.coroutines.android)
    api(libs.androidx.work.runtime.ktx)
    api(libs.work.multiprocess)
    api(libs.mmkv)
    api(libs.okhttp)
    api(libs.gson)
    api(libs.snakeyaml)
    api(libs.play.services.location)
    api(libs.zxing.lite)
    api(libs.zxing.core)
    api(libs.ucrop)
    api(libs.flexbox)
    api(libs.qmdeve.blurview)

    // Libraries MikuRay's own layouts name: their custom views and, more
    // importantly, the attrs those views declare (`app:lottie_*`,
    // `app:fastScroll*`, `app:actionMode`) are resolved when the app links.
    api(libs.recyclerview.fastscroll)
}
