package app.prayertimes.azan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.roundToInt

class PrayTimesTest {
    private fun minutes(h: Double) = (h * 60).roundToInt()
    private fun hm(s: String) = s.split(":").let { it[0].toInt() * 60 + it[1].toInt() }

    private fun check(t: PrayTimes.Times, expected: Map<Prayer, String>, tolerance: Int = 2) {
        for ((p, e) in expected) {
            val diff = abs(minutes(t[p]) - hm(e))
            assertTrue("${p.key}: expected $e, diff $diff min", diff <= tolerance)
        }
    }

    @Test fun cairoEgyptianAuthority() = check(
        PrayTimes.compute(2026, 11, 20, 30.0444, 31.2357, 2.0, "egypt"),
        mapOf(Prayer.FAJR to "04:54", Prayer.SUNRISE to "06:24", Prayer.DHUHR to "11:41",
            Prayer.ASR to "14:37", Prayer.MAGHRIB to "16:57", Prayer.ISHA to "18:18"),
    )

    @Test fun makkahUmmAlQura() = check(
        PrayTimes.compute(2026, 11, 20, 21.4225, 39.8262, 3.0, "makkah"),
        mapOf(Prayer.FAJR to "05:16", Prayer.DHUHR to "12:06", Prayer.ASR to "15:16", Prayer.MAGHRIB to "17:37"),
    )

    @Test fun ishaIs90MinutesAfterMaghribForUmmAlQura() {
        val t = PrayTimes.compute(2026, 11, 20, 21.4225, 39.8262, 3.0, "makkah")
        assertEquals(90, minutes(t.isha) - minutes(t.maghrib))
    }

    @Test fun hanafiAsrIsLater() {
        val std = PrayTimes.compute(2026, 11, 20, 30.0444, 31.2357, 2.0, "egypt")
        val han = PrayTimes.compute(2026, 11, 20, 30.0444, 31.2357, 2.0, "egypt", hanafiAsr = true)
        assertTrue(han.asr > std.asr + 0.5)
    }

    @Test fun manualAdjustment() {
        val std = PrayTimes.compute(2026, 11, 20, 30.0444, 31.2357, 2.0, "egypt")
        val adj = PrayTimes.compute(2026, 11, 20, 30.0444, 31.2357, 2.0, "egypt", adjustMinutes = mapOf(Prayer.FAJR to 3))
        assertEquals(3, minutes(adj.fajr) - minutes(std.fajr))
    }

    @Test fun highLatitudeTimesAreValidAndOrdered() {
        val t = PrayTimes.compute(2026, 6, 21, 64.1466, -21.9426, 0.0, "mwl") // ريكيافيك
        val order = Prayer.entries.map { t[it] }
        order.forEach { assertTrue(it.isFinite()) }
        assertEquals(order.sorted(), order)
    }

    private val cairo = PrayerSchedule.Location(30.0444, 31.2357, ZoneId.of("Africa/Cairo"), "egypt", false, emptyMap())

    @Test fun scheduleProducesRoundedLocalInstants() {
        val day = PrayerSchedule.day(cairo, LocalDate.of(2026, 11, 20))
        val fmt = DateTimeFormatter.ofPattern("HH:mm").withZone(cairo.zone)
        assertEquals("11:41", fmt.format(day[Prayer.DHUHR]))
        assertEquals(0, day.getValue(Prayer.FAJR).epochSecond % 60)
    }

    @Test fun nextPrayerSkipsSunriseAndRollsOverToTomorrow() {
        // بعد العشاء ← فجر الغد
        val (p, at) = PrayerSchedule.nextPrayer(cairo, Instant.parse("2026-11-20T20:00:00Z"))
        assertEquals(Prayer.FAJR, p)
        assertEquals(LocalDate.of(2026, 11, 21), at.atZone(cairo.zone).toLocalDate())
        // بين الشروق والظهر ← الظهر
        assertEquals(Prayer.DHUHR, PrayerSchedule.nextPrayer(cairo, Instant.parse("2026-11-20T05:00:00Z")).first)
    }

    @Test fun nextAzanRespectsDisabledPrayers() {
        val next = PrayerSchedule.nextAzan(cairo, Instant.parse("2026-11-20T05:00:00Z")) { it != Prayer.DHUHR }
        assertNotNull(next)
        assertEquals(Prayer.ASR, next!!.first)
        assertEquals(null, PrayerSchedule.nextAzan(cairo, Instant.now()) { false })
    }
}
