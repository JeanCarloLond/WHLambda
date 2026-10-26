plugins {
    alias(libs.plugins.whlambda.kmp.feature)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            // Shows the date consent was given, in the user's time zone.
            implementation(libs.kotlinx.datetime)
        }
    }
}
