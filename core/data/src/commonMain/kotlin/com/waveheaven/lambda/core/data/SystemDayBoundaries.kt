package com.waveheaven.lambda.core.data

import com.waveheaven.lambda.core.domain.exposure.DayBoundaries
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/** [DayBoundaries] in the device's current time zone, read on every call so travelling users get local days. */
class SystemDayBoundaries(
    private val timeZone: () -> TimeZone = { TimeZone.currentSystemDefault() },
) : DayBoundaries {
    override fun startOfDay(instant: Instant): Instant {
        val zone = timeZone()
        return instant.toLocalDateTime(zone).date.atStartOfDayIn(zone)
    }
}
