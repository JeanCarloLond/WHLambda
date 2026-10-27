plugins {
    alias(libs.plugins.whlambda.kmp.feature)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            // Day-of-week labels for the history chart, in the user's time zone.
            implementation(libs.kotlinx.datetime)
        }
    }
}
