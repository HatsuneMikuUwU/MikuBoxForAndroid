import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

fun secret(name: String): String? =
    (findProperty(name) as String?)?.takeIf { it.isNotBlank() }
        ?: localProps.getProperty(name)?.takeIf { it.isNotBlank() }
        ?: System.getenv(name)?.takeIf { it.isNotBlank() }

android {
    namespace = "top.uwu.mikubox"
    compileSdk = 36
    buildToolsVersion = "36.1.0"
    ndkVersion = "29.0.13599879"

    defaultConfig {
        applicationId = "top.uwu.mikubox"
        minSdk = 24
        targetSdk = 36
        versionCode = 10
        versionName = "UwU-1.0.0"

        externalNativeBuild {
            cmake {
                arguments += "-DMIHOMO_JNI_LIBS_DIR=${layout.buildDirectory.get().asFile}/generated/mihomo-jniLibs"
            }
        }
    }

    val keystorePass = secret("KEYSTORE_PASS")
    if (keystorePass != null) {
        signingConfigs {
            create("release") {
                storeFile = rootProject.file(secret("KEYSTORE_PATH") ?: "release.keystore")
                storePassword = keystorePass
                keyAlias = secret("ALIAS_NAME")
                keyPassword = secret("ALIAS_PASS")
                enableV1Signing = false
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildFeatures {
        viewBinding = true
    }

    splits {
        abi {
            isEnable = true
            reset()
            include("armeabi-v7a", "arm64-v8a", "x86_64")
            isUniversalApk = false
        }
    }

    packaging {
        resources {
            // Coroutine debug probes are useful only to a debugger and should
            // not be packaged in production or debug APKs.
            excludes += "DebugProbesKt.bin"
            excludes += "META-INF/**"
            excludes += "/META-INF/**"
        }
        jniLibs {
            useLegacyPackaging = true
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfigs.findByName("release")?.let { signingConfig = it }
        }
        debug {
            signingConfigs.findByName("release")?.let { signingConfig = it }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

val mihomoBridgeDir = rootProject.file("core/mihomo-bridge")
val mihomoSourceDir = rootProject.file("core/mihomo")
val mihomoJniLibsDir = layout.buildDirectory.dir("generated/mihomo-jniLibs")

android.sourceSets.getByName("main").jniLibs.srcDir(mihomoJniLibsDir)

val buildMihomoBridge by tasks.registering {
    group = "build"
    description = "Builds the bundled HSSkyBoy/mihomo Alpha JNI bridge for every Android ABI."
    inputs.dir(mihomoBridgeDir)
    inputs.dir(mihomoSourceDir)
    outputs.dir(mihomoJniLibsDir)

    doLast {
        val ndk = android.ndkDirectory
        // Pick the NDK prebuilt toolchain for the build host (Windows uses .cmd
        // wrappers) so this works both locally and on Linux/macOS CI runners.
        val hostOs = System.getProperty("os.name").lowercase()
        val (hostTag, exeExt) = when {
            hostOs.contains("win") -> "windows-x86_64" to ".cmd"
            hostOs.contains("mac") || hostOs.contains("darwin") -> "darwin-x86_64" to ""
            else -> "linux-x86_64" to ""
        }
        val clangDir = ndk.resolve("toolchains/llvm/prebuilt/$hostTag/bin")
        val targets = mapOf(
            "armeabi-v7a" to "armv7a-linux-androideabi24-clang",
            "arm64-v8a" to "aarch64-linux-android24-clang",
            "x86_64" to "x86_64-linux-android24-clang",
        )

        targets.forEach { (abi, compiler) ->
            val output = mihomoJniLibsDir.get().file("$abi/libmihomo.so").asFile
            output.parentFile.mkdirs()
            exec {
                workingDir = mihomoBridgeDir
                environment("GOOS", "android")
                environment("GOARCH", when (abi) {
                    "armeabi-v7a" -> "arm"
                    "arm64-v8a" -> "arm64"
                    "x86" -> "386"
                    "x86_64" -> "amd64"
                    else -> error("Unsupported Android ABI: $abi")
                })
                environment("GOARM", if (abi == "armeabi-v7a") "7" else "")
                environment("CGO_ENABLED", "1")
                environment("CC", clangDir.resolve(compiler + exeExt).absolutePath)
                commandLine(
                    "go", "build", "-trimpath", "-buildmode=c-shared",
                    "-ldflags=-s -w", "-o", output.absolutePath, ".",
                )
            }
        }
    }
}

tasks.configureEach {
    if (name.contains("CMake") || name.endsWith("JniLibFolders")) {
        dependsOn(buildMihomoBridge)
    }
}

val stripDebugApkMetadata by tasks.registering {
    dependsOn("packageDebug")

    doLast {
        val outputDir = layout.buildDirectory.dir("outputs/apk/debug").get().asFile
        val debugKeystore = file("${System.getProperty("user.home")}/.android/debug.keystore")
        check(debugKeystore.isFile) { "Debug keystore was not found: $debugKeystore" }

        val buildToolsDir = android.sdkDirectory.resolve("build-tools/${android.buildToolsVersion}")
        val zipalign = buildToolsDir.resolve("zipalign.exe")
        val apksigner = buildToolsDir.resolve("apksigner.bat")
        check(zipalign.isFile && apksigner.isFile) { "Android build-tools are incomplete in $buildToolsDir" }

        outputDir.listFiles { file -> file.extension == "apk" }?.forEach { apk ->
            val unaligned = apk.resolveSibling("${apk.nameWithoutExtension}-stripped-unaligned.apk")
            val aligned = apk.resolveSibling("${apk.nameWithoutExtension}-stripped-aligned.apk")
            ZipFile(apk).use { input ->
                ZipOutputStream(unaligned.outputStream().buffered()).use { output ->
                    val entries = input.entries()
                    while (entries.hasMoreElements()) {
                        val entry = entries.nextElement()
                        if (entry.name == "DebugProbesKt.bin" || entry.name.startsWith("META-INF/")) continue
                        output.putNextEntry(ZipEntry(entry.name).apply { time = entry.time })
                        if (!entry.isDirectory) input.getInputStream(entry).use { it.copyTo(output) }
                        output.closeEntry()
                    }
                }
            }
            exec { commandLine(zipalign.absolutePath, "-f", "4", unaligned.absolutePath, aligned.absolutePath) }
            exec {
                commandLine(
                    apksigner.absolutePath,
                    "sign",
                    "--ks", debugKeystore.absolutePath,
                    "--ks-pass", "pass:android",
                    "--key-pass", "pass:android",
                    "--v1-signing-enabled", "false",
                    "--out", apk.absolutePath,
                    aligned.absolutePath,
                )
            }
            unaligned.delete()
            aligned.delete()
        }
    }
}

tasks.matching { it.name == "assembleDebug" }.configureEach {
    finalizedBy(stripDebugApkMetadata)
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.zxing.core)
}
