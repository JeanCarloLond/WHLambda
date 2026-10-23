plugins {
    alias(libs.plugins.whlambda.kmp.compose)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.core.designsystem)
            implementation(projects.core.domain)
            implementation(libs.compose.components.resources)
        }
        // UI tests render headlessly with Skia, which is only available on the desktop JVM.
        getByName("desktopTest").dependencies {
            implementation(libs.compose.ui.test)
            implementation(compose.desktop.currentOs)
        }
    }
}

compose.resources {
    packageOfResClass = "com.waveheaven.lambda.shared.resources"
}
