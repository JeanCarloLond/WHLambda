import com.waveheaven.lambda.buildlogic.composeDesktopCurrentOs
import com.waveheaven.lambda.buildlogic.library
import com.waveheaven.lambda.buildlogic.libs
import com.waveheaven.lambda.buildlogic.namespaceFromPath
import com.waveheaven.lambda.buildlogic.pluginId
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.getByType
import org.jetbrains.compose.ComposeExtension
import org.jetbrains.compose.resources.ResourcesExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/**
 * A feature module: one user-facing capability (screens, ViewModels, strings) following the
 * unidirectional data flow pattern described in `docs/architecture.md`.
 *
 * Features may depend on `core:*` modules, never on other features or on `:shared`.
 */
class KmpFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) =
        with(target) {
            pluginManager.apply(libs.pluginId("whlambda-kmp-compose"))
            pluginManager.apply(libs.pluginId("kotlin-serialization"))

            extensions.configure<KotlinMultiplatformExtension> {
                sourceSets.getByName("commonMain").dependencies {
                    implementation(project(":core:designsystem"))
                    implementation(project(":core:domain"))
                    implementation(libs.library("compose-components-resources"))
                    implementation(libs.library("jetbrains-lifecycle-viewmodel-compose"))
                    implementation(libs.library("jetbrains-lifecycle-runtime-compose"))
                    implementation(libs.library("androidx-navigation3-runtime"))
                    implementation(libs.library("koin-compose-viewmodel"))
                    implementation(libs.library("kotlinx-serialization-core"))
                }
                sourceSets.getByName("commonTest").dependencies {
                    implementation(libs.library("kotlinx-coroutines-test"))
                }
                // Compose UI tests render headlessly with Skia, which only the desktop JVM provides.
                sourceSets.getByName("desktopTest").dependencies {
                    implementation(libs.library("compose-ui-test"))
                    implementation(composeDesktopCurrentOs())
                    // Provides Dispatchers.Main, which ViewModels and lifecycle-aware collection need.
                    implementation(libs.library("kotlinx-coroutines-swing"))
                }
            }

            extensions.getByType<ComposeExtension>().extensions.configure<ResourcesExtension> {
                packageOfResClass = "$namespaceFromPath.resources"
            }
        }
}
