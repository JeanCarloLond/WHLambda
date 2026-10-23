plugins {
    `kotlin-dsl`
}

group = "com.waveheaven.lambda.buildlogic"

dependencies {
    compileOnly(libs.android.gradlePlugin)
    compileOnly(libs.kotlin.gradlePlugin)
    compileOnly(libs.compose.gradlePlugin)
    compileOnly(libs.compose.compiler.gradlePlugin)
    compileOnly(libs.detekt.gradlePlugin)
}

gradlePlugin {
    plugins {
        register("kmpLibrary") {
            id =
                libs.plugins.whlambda.kmp.library
                    .get()
                    .pluginId
            implementationClass = "KmpLibraryConventionPlugin"
        }
        register("kmpCompose") {
            id =
                libs.plugins.whlambda.kmp.compose
                    .get()
                    .pluginId
            implementationClass = "KmpComposeConventionPlugin"
        }
        register("androidApplication") {
            id =
                libs.plugins.whlambda.android.application
                    .get()
                    .pluginId
            implementationClass = "AndroidApplicationConventionPlugin"
        }
        register("desktopApplication") {
            id =
                libs.plugins.whlambda.desktop.application
                    .get()
                    .pluginId
            implementationClass = "DesktopApplicationConventionPlugin"
        }
        register("detekt") {
            id =
                libs.plugins.whlambda.detekt
                    .get()
                    .pluginId
            implementationClass = "DetektConventionPlugin"
        }
    }
}
