plugins {
    // Loaded once here so every module shares the same plugin classloader.
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.kmp.library) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.compose.multiplatform) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.kover)
    alias(libs.plugins.spotless)
}

spotless {
    val ktlintVersion = libs.versions.ktlint.get()
    kotlin {
        target("**/*.kt")
        targetExclude("**/build/**", "**/node_modules/**")
        ktlint(ktlintVersion)
    }
    kotlinGradle {
        target("**/*.gradle.kts")
        targetExclude("**/build/**", "**/node_modules/**")
        ktlint(ktlintVersion)
    }
}

dependencies {
    // Modules whose tests count towards the merged coverage report.
    kover(projects.core.domain)
}

kover {
    reports {
        verify {
            rule("Domain logic must stay well tested") {
                minBound(80)
            }
        }
    }
}
