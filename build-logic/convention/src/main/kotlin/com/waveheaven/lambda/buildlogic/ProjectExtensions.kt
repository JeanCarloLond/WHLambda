package com.waveheaven.lambda.buildlogic

import org.gradle.api.Project
import org.gradle.api.artifacts.MinimalExternalModuleDependency
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.provider.Provider
import org.gradle.kotlin.dsl.getByType

internal val Project.libs: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")

internal fun VersionCatalog.version(alias: String): String = findVersion(alias).get().requiredVersion

internal fun VersionCatalog.library(alias: String): Provider<MinimalExternalModuleDependency> = findLibrary(alias).get()

internal fun VersionCatalog.pluginId(alias: String): String = findPlugin(alias).get().get().pluginId

/** Compose Desktop runtime (with Skia natives) for the OS and CPU running the build, e.g. for headless UI tests. */
internal fun Project.composeDesktopCurrentOs(): String {
    val os = System.getProperty("os.name").lowercase()
    val family =
        when {
            "win" in os -> "windows"
            "mac" in os -> "macos"
            else -> "linux"
        }
    val arch = if (System.getProperty("os.arch") in setOf("aarch64", "arm64")) "arm64" else "x64"
    return "org.jetbrains.compose.desktop:desktop-jvm-$family-$arch:${libs.version("compose-multiplatform")}"
}

/** Root package shared by every module, e.g. `:core:domain` → `com.waveheaven.lambda.core.domain`. */
internal const val BASE_PACKAGE = "com.waveheaven.lambda"

internal val Project.namespaceFromPath: String
    get() =
        BASE_PACKAGE +
            path.split(':').filter(String::isNotEmpty).joinToString(separator = "") { segment ->
                "." + segment.replace('-', '_')
            }
