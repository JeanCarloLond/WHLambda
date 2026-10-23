import com.waveheaven.lambda.buildlogic.libs
import com.waveheaven.lambda.buildlogic.pluginId
import com.waveheaven.lambda.buildlogic.version
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

/** Compose Desktop application (Windows and Linux installers). */
class DesktopApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) =
        with(target) {
            pluginManager.apply(libs.pluginId("kotlin-jvm"))
            pluginManager.apply(libs.pluginId("compose-multiplatform"))
            pluginManager.apply(libs.pluginId("compose-compiler"))
            pluginManager.apply(libs.pluginId("whlambda-detekt"))

            extensions.configure<KotlinJvmProjectExtension> {
                jvmToolchain(libs.version("jdk").toInt())
                compilerOptions {
                    jvmTarget.set(JvmTarget.fromTarget(libs.version("jdk")))
                    allWarningsAsErrors.set(true)
                }
            }
        }
}
