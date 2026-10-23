plugins {
    alias(libs.plugins.whlambda.kmp.library)
}

kotlin {
    // Pure domain logic: every public declaration must be intentional.
    explicitApi()

    sourceSets {
        commonMain.dependencies {
            // Only for Flow/StateFlow types in ports; the domain never launches coroutines or picks dispatchers.
            api(libs.kotlinx.coroutines.core)
        }
        commonTest.dependencies {
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
