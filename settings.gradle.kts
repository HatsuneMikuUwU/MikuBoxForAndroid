pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // ucrop and zxing-lite, which MikuRay's vendored sources use, live here.
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "MikuBoxCla"

include(":app")

// MikuRay's design layer, vendored verbatim: every layout, style, colour,
// drawable, animation and preference screen the ported UI inflates. The
// Kotlin for those screens lives in :app under com.miku.ray.* so the copied
// XML keeps working without any tag rewriting.
include(":mikuray-ui")

// MikuRay's RemixIcon vectors (@drawable/rmx_*), also vendored verbatim.
include(":remixicon")
