import com.android.build.api.dsl.ApplicationExtension
import com.waveheaven.lambda.buildlogic.BASE_PACKAGE
import com.waveheaven.lambda.buildlogic.libs
import com.waveheaven.lambda.buildlogic.pluginId
import com.waveheaven.lambda.buildlogic.version
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension

/**
 * Android application module. AGP 9 compiles Kotlin itself ("built-in Kotlin"),
 * so no separate Kotlin Android plugin is applied.
 */
class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) =
        with(target) {
            pluginManager.apply(libs.pluginId("android-application"))
            pluginManager.apply(libs.pluginId("compose-compiler"))
            pluginManager.apply(libs.pluginId("whlambda-detekt"))

            val javaVersion = JavaVersion.toVersion(libs.version("jvmTarget"))

            extensions.configure<ApplicationExtension> {
                namespace = BASE_PACKAGE
                compileSdk = libs.version("android-compileSdk").toInt()

                defaultConfig {
                    applicationId = BASE_PACKAGE
                    minSdk = libs.version("android-minSdk").toInt()
                    targetSdk = libs.version("android-targetSdk").toInt()
                }

                compileOptions {
                    sourceCompatibility = javaVersion
                    targetCompatibility = javaVersion
                }

                buildFeatures {
                    compose = true
                }

                lint {
                    warningsAsErrors = true
                    abortOnError = true
                    // Dependency freshness is handled by Dependabot, not by failing builds.
                    disable += setOf("GradleDependency", "NewerVersionAvailable", "AndroidGradlePluginVersion")
                }
            }

            extensions.configure<KotlinAndroidProjectExtension> {
                jvmToolchain(libs.version("jdk").toInt())
                compilerOptions {
                    jvmTarget.set(JvmTarget.fromTarget(libs.version("jvmTarget")))
                    allWarningsAsErrors.set(true)
                }
            }
        }
}
