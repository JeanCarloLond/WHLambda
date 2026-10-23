import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryTarget
import com.waveheaven.lambda.buildlogic.libs
import com.waveheaven.lambda.buildlogic.namespaceFromPath
import com.waveheaven.lambda.buildlogic.pluginId
import com.waveheaven.lambda.buildlogic.version
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.ExtensionAware
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/**
 * Kotlin Multiplatform library targeting every platform WaveHeaven Lambda ships on:
 * Android and the desktop JVM (Windows, Linux).
 *
 * Source sets: `commonMain` for platform-agnostic code, `androidMain` / `desktopMain` for `actual`s.
 * Unit tests live in `commonTest` and run on both the desktop JVM and the Android host JVM.
 */
class KmpLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) =
        with(target) {
            pluginManager.apply(libs.pluginId("kotlin-multiplatform"))
            pluginManager.apply(libs.pluginId("android-kmp-library"))
            pluginManager.apply(libs.pluginId("whlambda-detekt"))
            pluginManager.apply(libs.pluginId("kover"))

            extensions.configure<KotlinMultiplatformExtension> {
                jvmToolchain(libs.version("jdk").toInt())

                (this as ExtensionAware).extensions.configure<KotlinMultiplatformAndroidLibraryTarget>("android") {
                    namespace = namespaceFromPath
                    compileSdk = libs.version("android-compileSdk").toInt()
                    minSdk = libs.version("android-minSdk").toInt()
                    // Run commonTest on the Android JVM too, so Android `actual`s are covered.
                    withHostTest {}
                    compilerOptions {
                        jvmTarget.set(JvmTarget.fromTarget(libs.version("jvmTarget")))
                    }
                }

                jvm("desktop") {
                    compilerOptions {
                        jvmTarget.set(JvmTarget.fromTarget(libs.version("jvmTarget")))
                    }
                }

                compilerOptions {
                    allWarningsAsErrors.set(true)
                }

                sourceSets.getByName("commonTest").dependencies {
                    implementation(kotlin("test"))
                }
            }
        }
}
