pluginManagement {
    repositories {
        google()
        mavenLocal()
        mavenCentral()
    }
    plugins {
        id("com.android.application") version "8.13.2"
        id("com.android.library") version "8.13.2"
        id("org.jetbrains.kotlin.android") version "2.4.10-RC"
        id("org.jetbrains.kotlin.plugin.compose") version "2.4.10-RC"
    }
}

include(":app")
rootProject.name = "MB4A"
