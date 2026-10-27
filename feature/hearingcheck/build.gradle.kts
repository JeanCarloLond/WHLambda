plugins {
    alias(libs.plugins.whlambda.kmp.feature)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            // Shows the date of the last check in the user's time zone.
            implementation(libs.kotlinx.datetime)
        }
    }
}
