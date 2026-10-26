package com.waveheaven.lambda.core.domain.consent

import kotlin.time.Instant

/**
 * The user's explicit consent to process sound-exposure data, which is health data (Ley 1581 de 2012, ADR-0007).
 * Recorded with the version of the text the user accepted, so a new text asks again (WH-072).
 */
public data class Consent(
    val version: Int,
    val acceptedAt: Instant,
) {
    init {
        require(version >= 1) { "Consent versions start at 1" }
    }

    public companion object {
        /**
         * Version of the consent text shown today. Bump it whenever the text changes in substance (what is measured,
         * stored or shared); users are then asked again before metering resumes. History in `docs/privacy.md`.
         */
        public const val CURRENT_VERSION: Int = 1
    }
}

public enum class ConsentStatus {
    /** Never given, or withdrawn. */
    MISSING,

    /** Given for an older text; must be asked again. */
    OUTDATED,

    /** Given for the current text. */
    GIVEN,
}

/** Status of this consent against [currentVersion]. */
public fun Consent?.status(currentVersion: Int = Consent.CURRENT_VERSION): ConsentStatus =
    when {
        this == null -> ConsentStatus.MISSING
        version < currentVersion -> ConsentStatus.OUTDATED
        else -> ConsentStatus.GIVEN
    }

/** Metering is allowed only with consent for the current text (WH-071, WH-072). */
public fun Consent?.allowsMetering(currentVersion: Int = Consent.CURRENT_VERSION): Boolean = status(currentVersion) == ConsentStatus.GIVEN
