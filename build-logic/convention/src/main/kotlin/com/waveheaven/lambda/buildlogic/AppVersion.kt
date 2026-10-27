package com.waveheaven.lambda.buildlogic

import org.gradle.api.Project

/**
 * The version both apps ship with: `-PappVersion=1.2.3` (the release workflow passes the tag without its `v`), or
 * [DEFAULT_APP_VERSION] for local builds. Semantic versioning, optionally with a pre-release suffix (`1.2.0-beta.1`).
 */
internal val Project.appVersion: String
    get() {
        val version = providers.gradleProperty("appVersion").orNull ?: DEFAULT_APP_VERSION
        require(SEMVER.matches(version)) { "appVersion must look like 1.2.3 or 1.2.3-beta.1, was '$version'" }
        return version
    }

/** `MAJOR.MINOR.PATCH` without the pre-release suffix, as MSI, DEB and RPM installers require. */
internal fun numericVersion(version: String): String = version.substringBefore('-')

/**
 * Android's `versionCode`: `MAJOR·10000 + MINOR·100 + PATCH`, so every release is greater than the last as long as
 * minor and patch stay below 100.
 */
internal fun versionCode(version: String): Int {
    val (major, minor, patch) = numericVersion(version).split('.').map(String::toInt)
    require(minor < PART_LIMIT && patch < PART_LIMIT) { "minor and patch must stay below $PART_LIMIT for the versionCode" }
    return major * PART_LIMIT * PART_LIMIT + minor * PART_LIMIT + patch
}

private const val DEFAULT_APP_VERSION = "0.1.0"
private const val PART_LIMIT = 100
private val SEMVER = Regex("""\d+\.\d+\.\d+(-[0-9A-Za-z.]+)?""")
