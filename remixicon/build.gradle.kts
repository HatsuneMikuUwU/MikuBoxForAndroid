// MikuRay's RemixIcon set, vendored verbatim: 1768 vector drawables referenced
// as @drawable/rmx_* by the ported layouts and preference screens.
plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.miku.ray.remixicon"
    compileSdk = 36

    defaultConfig {
        minSdk = 24

        vectorDrawables {
            useSupportLibrary = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(libs.material)
}
