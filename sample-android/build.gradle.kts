plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
}

/**
 * The library's Android proving ground.
 *
 * This module exists because the previous one didn't hold. Verification used to run against a separate
 * CMP app in another repository, which drifted to Kotlin 2.4 / CMP 1.11 / AGP 9 while the library and
 * every consumer stayed on the floor below — so the screenshots it produced described a Compose nobody
 * was running. Four rendering defects shipped behind that gap in 2.2.0.
 *
 * Two properties make this one different, and both are structural rather than a promise to be careful:
 *
 *  - It reads the **same** `libs.versions.toml` as `:number-input`, so it cannot be on a different
 *    Kotlin, Compose or AGP than the library it is testing. Bumping the floor moves both at once.
 *  - It depends on `project(":number-input")`, not on a published coordinate, so there is no
 *    `publishToMavenLocal` round-trip between changing the library and seeing it on a device.
 *
 * `minSdk` is the one place this deliberately disagrees with the library. The library floors at 23 so
 * it is never a consumer's binding constraint; the sample has no consumers and `enableEdgeToEdge`
 * wants 24+, so it takes 24 rather than dragging the library up.
 */
android {
    namespace = "dev.viethung.numberinput.sample"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "dev.viethung.numberinput.sample"
        minSdk = libs.versions.sample.minSdk.get().toInt()
        targetSdk = libs.versions.android.compileSdk.get().toInt()
        versionCode = 1
        versionName = "1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
        }
    }

    buildTypes {
        // Debug-only on purpose: this is never released, and a release variant would only add a
        // signing config to keep working.
        getByName("debug") {
            isMinifyEnabled = false
        }
    }
}

dependencies {
    implementation(project(":number-input"))

    // Foundation only, mirroring the library's own dependency set. A Material theme here would let a
    // defect hide behind Material's own field chrome, which is the opposite of what this module is for.
    implementation(compose.foundation)
    implementation(compose.ui)
    implementation(libs.androidx.activity.compose)
}
