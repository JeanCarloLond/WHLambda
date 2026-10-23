import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryTarget
import com.waveheaven.lambda.buildlogic.library
import com.waveheaven.lambda.buildlogic.libs
import com.waveheaven.lambda.buildlogic.pluginId
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.ExtensionAware
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/** [KmpLibraryConventionPlugin] plus Compose Multiplatform for shared UI modules. */
class KmpComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) =
        with(target) {
            pluginManager.apply(libs.pluginId("whlambda-kmp-library"))
            pluginManager.apply(libs.pluginId("compose-multiplatform"))
            pluginManager.apply(libs.pluginId("compose-compiler"))

            extensions.configure<KotlinMultiplatformExtension> {
                // The AGP 9 KMP library plugin skips Android resources by default, which would leave
                // Compose resources (strings, images) out of the APK and crash at runtime.
                (this as ExtensionAware).extensions.configure<KotlinMultiplatformAndroidLibraryTarget>("android") {
                    androidResources { enable = true }
                }

                sourceSets.getByName("commonMain").dependencies {
                    implementation(libs.library("compose-runtime"))
                    implementation(libs.library("compose-foundation"))
                    implementation(libs.library("compose-ui"))
                    implementation(libs.library("compose-material3"))
                    implementation(libs.library("compose-ui-tooling-preview"))
                }
            }
        }
}
