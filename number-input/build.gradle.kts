import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
    id("maven-publish")
}

group = "dev.viethung"
version = "1.0.0-SNAPSHOT"

kotlin {
    androidTarget {
        compilerOptions {
            // JVM 11 to match of1-freight-mobile; raising this would exclude it.
            jvmTarget.set(JvmTarget.JVM_11)
        }
        publishLibraryVariants("release")
    }

    // iosX64 is retained deliberately: BFSOne still declares it, and CMP 1.11
    // removed it. Dropping it here would break that consumer.
    iosX64()
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            // Foundation-only by design — no Material3. Consumers bring their own
            // design system; styling arrives via NumberInputStyle.
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.ui)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}

android {
    namespace = "dev.viethung.numberinput"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}
