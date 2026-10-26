plugins {
    alias(libs.plugins.whlambda.kmp.compose)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.core.designsystem)
            implementation(projects.feature.exposure)
            implementation(projects.feature.onboarding)
            implementation(projects.feature.profiles)
            implementation(projects.feature.dashboard)
            implementation(projects.feature.about)
            implementation(projects.feature.settings)
            implementation(projects.core.data)
            implementation(projects.core.audio)
            implementation(libs.jetbrains.navigation3.ui)
            implementation(libs.jetbrains.lifecycle.viewmodel.navigation3)
            implementation(libs.kotlinx.serialization.core)
            api(libs.koin.core)
            implementation(libs.koin.compose)
        }
        // UI tests render headlessly with Skia, which is only available on the desktop JVM.
        getByName("desktopTest").dependencies {
            implementation(libs.compose.ui.test)
            implementation(compose.desktop.currentOs)
            // Provides Dispatchers.Main, which ViewModels and lifecycle-aware collection need.
            implementation(libs.kotlinx.coroutines.swing)
        }
    }
}
