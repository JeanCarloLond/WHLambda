import com.waveheaven.lambda.buildlogic.library
import com.waveheaven.lambda.buildlogic.libs
import com.waveheaven.lambda.buildlogic.pluginId
import com.waveheaven.lambda.buildlogic.version
import io.gitlab.arturbosch.detekt.Detekt
import io.gitlab.arturbosch.detekt.extensions.DetektExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.withType

/** Static analysis with detekt (code smells, complexity) plus Compose-specific rules. */
class DetektConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) =
        with(target) {
            pluginManager.apply(libs.pluginId("detekt"))

            extensions.configure<DetektExtension> {
                buildUponDefaultConfig = true
                config.setFrom(rootProject.file("config/detekt/detekt.yml"))
                // Multiplatform modules keep sources in src/<sourceSet>/kotlin, not just src/main.
                source.setFrom(fileTree("src") { include("**/kotlin/**/*.kt") })
            }

            tasks.withType<Detekt>().configureEach {
                jvmTarget = libs.version("jvmTarget")
                reports {
                    html.required.set(true)
                    sarif.required.set(true)
                    xml.required.set(false)
                    txt.required.set(false)
                    md.required.set(false)
                }
            }

            // detekt 1.23 embeds Kotlin 2.0; keep the Kotlin plugin from upgrading its classpath.
            configurations.matching { it.name == "detekt" }.configureEach {
                resolutionStrategy.eachDependency {
                    if (requested.group == "org.jetbrains.kotlin") {
                        useVersion(libs.version("detekt-kotlin"))
                    }
                }
            }

            dependencies {
                add("detektPlugins", libs.library("detekt-compose-rules"))
            }
        }
}
