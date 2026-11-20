package app.prayertimes.azan

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.math.roundToLong

/** تحويل الأوقات المحسوبة إلى لحظات زمنية فعلية، وإيجاد الصلاة/الأذان القادم */
object PrayerSchedule {

    data class Location(
        val lat: Double, val lng: Double, val zone: ZoneId,
        val method: String, val hanafiAsr: Boolean, val adjust: Map<Prayer, Int>,
    )

    fun day(loc: Location, date: LocalDate): Map<Prayer, Instant> {
        val noon = date.atTime(12, 0).atZone(loc.zone).toInstant()
        val offsetHours = loc.zone.rules.getOffset(noon).totalSeconds / 3600.0
        val t = PrayTimes.compute(
            date.year, date.monthValue, date.dayOfMonth, loc.lat, loc.lng, offsetHours,
            loc.method, loc.hanafiAsr, loc.adjust,
        )
        val base = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        return Prayer.entries.associateWith { p ->
            val ms = base + ((t[p] - offsetHours) * 3_600_000).roundToLong()
            // تقريب لأقرب دقيقة كما في التقاويم المنشورة
            Instant.ofEpochMilli(Math.round(ms / 60_000.0) * 60_000)
        }
    }

    fun today(loc: Location, now: Instant = Instant.now()): LocalDate = now.atZone(loc.zone).toLocalDate()

    /** أول صلاة (من الخمس) بعد اللحظة المعطاة */
    fun nextPrayer(loc: Location, after: Instant): Pair<Prayer, Instant> =
        upcoming(loc, after) { it.hasAzan }!!

    /** أول أذان مفعّل بعد اللحظة المعطاة */
    fun nextAzan(loc: Location, after: Instant, enabled: (Prayer) -> Boolean): Pair<Prayer, Instant>? =
        upcoming(loc, after) { it.hasAzan && enabled(it) }

    private fun upcoming(loc: Location, after: Instant, filter: (Prayer) -> Boolean): Pair<Prayer, Instant>? {
        val start = today(loc, after).minusDays(1)
        for (i in 0..2) {
            val times = day(loc, start.plusDays(i.toLong()))
            for (p in Prayer.entries) {
                if (!filter(p)) continue
                val at = times.getValue(p)
                if (at.isAfter(after)) return p to at
            }
        }
        return null
    }
}
