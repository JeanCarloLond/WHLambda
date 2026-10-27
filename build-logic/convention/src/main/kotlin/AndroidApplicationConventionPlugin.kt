import com.android.build.api.dsl.ApplicationExtension
import com.waveheaven.lambda.buildlogic.BASE_PACKAGE
import com.waveheaven.lambda.buildlogic.appVersion
import com.waveheaven.lambda.buildlogic.libs
import com.waveheaven.lambda.buildlogic.pluginId
import com.waveheaven.lambda.buildlogic.version
import com.waveheaven.lambda.buildlogic.versionCode
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension

/**
 * Android application module. AGP 9 compiles Kotlin itself ("built-in Kotlin"),
 * so no separate Kotlin Android plugin is applied. Versioned from `-PappVersion`, signed from the environment.
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
                    versionName = appVersion
                    versionCode = versionCode(appVersion)
                }

                // Release signing comes from the environment (CI secrets, docs/release.md); never from the repository.
                val keystore = providers.environmentVariable("ANDROID_KEYSTORE_FILE").orNull
                if (keystore != null) {
                    val release =
                        signingConfigs.create("release") {
                            storeFile = file(keystore)
                            storePassword = providers.environmentVariable("ANDROID_KEYSTORE_PASSWORD").get()
                            keyAlias = providers.environmentVariable("ANDROID_KEY_ALIAS").get()
                            keyPassword = providers.environmentVariable("ANDROID_KEY_PASSWORD").get()
                        }
                    buildTypes.getByName("release").signingConfig = release
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
