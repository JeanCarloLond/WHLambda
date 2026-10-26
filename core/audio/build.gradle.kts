plugins {
    alias(libs.plugins.whlambda.kmp.library)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.core.domain)
        }
        commonTest.dependencies {
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
