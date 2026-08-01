import com.vanniktech.maven.publish.SonatypeHost
import org.jetbrains.compose.ExperimentalComposeLibrary
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.maven.publish)
}

group = "dev.viethung"
version = "2.1.0"

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
        iosTest.dependencies {
            // ui-test's runComposeUiTest runs the same semantics tree Compose hands to the platform
            // accessibility service, on the real simulator this target already runs on. That is what
            // catches a key publishing as bare text instead of a button, which a plain state test
            // cannot see at all: onTextChange never runs, so `rawText` and `value` stay correct while
            // the key is unreachable to a screen reader or a UI test. Android-side coverage would need
            // Robolectric, which this repo does not otherwise depend on; iOS is also where the defect
            // this guards against was found.
            @OptIn(ExperimentalComposeLibrary::class)
            implementation(compose.uiTest)
        }
    }
}

mavenPublishing {
    publishToMavenCentral(SonatypeHost.CENTRAL_PORTAL)

    // Central requires every artifact to be signed, but `publishToMavenLocal` is the inner loop for
    // the sample app and must keep working on a machine with no GPG key. Sign only once signing
    // credentials are actually present — see README for which properties to set.
    if (providers.gradleProperty("signingInMemoryKey").isPresent ||
        providers.gradleProperty("signing.keyId").isPresent
    ) {
        signAllPublications()
    }

    pom {
        name.set("number-input")
        description.set(
            "Locale-aware numeric text field for Compose Multiplatform (Android + iOS) with " +
                "live thousands grouping and a Clear / plus-minus / Done keyboard toolbar.",
        )
        inceptionYear.set("2026")
        url.set("https://github.com/hugues-vnsgn/number-input")
        licenses {
            license {
                name.set("The Apache License, Version 2.0")
                url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                distribution.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
            }
        }
        developers {
            developer {
                id.set("hugues-vnsgn")
                name.set("Do Viet Hung")
                url.set("https://github.com/hugues-vnsgn")
            }
        }
        scm {
            url.set("https://github.com/hugues-vnsgn/number-input")
            connection.set("scm:git:git://github.com/hugues-vnsgn/number-input.git")
            developerConnection.set("scm:git:ssh://git@github.com/hugues-vnsgn/number-input.git")
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
