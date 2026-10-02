// Kotlin 2.4 metadata requires R8 9.1.29 or newer; AGP 8.13 bundles an older D8/R8.
buildscript {
    repositories {
        maven {
            url = uri("https://storage.googleapis.com/r8-releases/raw")
            content { includeModule("com.android.tools", "r8") }
        }
        google()
        mavenCentral()
    }
    dependencies { classpath("com.android.tools:r8:9.1.29") }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.aboutLibraries) apply false
}
