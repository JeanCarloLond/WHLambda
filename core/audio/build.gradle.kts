plugins {
    alias(libs.plugins.whlambda.kmp.library)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.core.domain)
        }
        // Per-application volume and capture on desktop (ADR-0009): pactl's JSON on Linux, WASAPI through JNA on Windows.
        getByName("desktopMain").dependencies {
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.jna.platform)
        }
        commonTest.dependencies {
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
