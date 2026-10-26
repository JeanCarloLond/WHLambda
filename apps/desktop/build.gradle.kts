import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.whlambda.desktop.application)
}

dependencies {
    implementation(projects.shared)
    implementation(compose.desktop.currentOs)
    implementation(libs.kotlinx.coroutines.swing)
    // Spike #4 prototype only.
    implementation(projects.core.audio)
    implementation(libs.jna.platform)
    implementation(libs.kotlinx.serialization.json)
}

tasks.register<JavaExec>("runSpike") {
    group = "spike"
    description = "Spike #4: list app audio sessions, change one app's volume and meter the microphone."
    mainClass.set("com.waveheaven.lambda.desktop.spike.AppVolumeSpikeKt")
    classpath = sourceSets["main"].runtimeClasspath
    javaLauncher.set(javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(21)) })
}

compose.desktop {
    application {
        mainClass = "com.waveheaven.lambda.desktop.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Deb, TargetFormat.Rpm)
            packageName = "WaveHeaven Lambda"
            packageVersion = "0.1.0"
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
