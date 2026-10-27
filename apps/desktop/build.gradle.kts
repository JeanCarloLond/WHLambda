import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.whlambda.desktop.application)
}

dependencies {
    implementation(projects.shared)
    implementation(projects.core.data)
    implementation(projects.core.audio)
    implementation(projects.core.designsystem)
    implementation(compose.desktop.currentOs)
    implementation(libs.compose.components.resources)
    implementation(libs.kotlinx.coroutines.swing)
}

compose.resources {
    packageOfResClass = "com.waveheaven.lambda.desktop.resources"
}

compose.desktop {
    application {
        mainClass = "com.waveheaven.lambda.desktop.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Deb, TargetFormat.Rpm)
            packageName = "WaveHeaven Lambda"
            description = "Safe-listening companion that adapts volume to ambient noise."
            vendor = "WaveHeaven"
            licenseFile.set(rootProject.file("LICENSE"))

            windows {
                menuGroup = "WaveHeaven"
                // Stable across versions so MSI upgrades replace the previous install.
                upgradeUuid = "82271e11-8df3-4b6f-84b0-634621dce8d3"
            }
            linux {
                packageName = "waveheaven-lambda"
            }
        }
    }
}
