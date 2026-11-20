package app.prayertimes.azan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class AzkarTest {
    private val cairo = PrayerSchedule.Location(30.0444, 31.2357, ZoneId.of("Africa/Cairo"), "egypt", false, emptyMap())
    private val zone = cairo.zone
    private fun local(s: String) = java.time.LocalDateTime.parse(s).atZone(zone).toInstant()

    @Test fun listsAreCompleteAndValid() {
        for (list in listOf(MORNING_AZKAR, EVENING_AZKAR)) {
            assertTrue(list.size >= 20)
            list.forEach { assertTrue(it.text.isNotBlank() && it.count >= 1) }
        }
        assertTrue(MORNING_AZKAR.first().text.contains("الحي القيوم"))
        assertTrue(MORNING_AZKAR.any { it.text.startsWith("أصبحنا وأصبح الملك لله") })
        assertTrue(EVENING_AZKAR.any { it.text.startsWith("أمسينا وأمسى الملك لله") })
        assertTrue(EVENING_AZKAR.none { it.text.contains("أصبحت") || it.text.startsWith("أصبحنا") })
        assertTrue(SHORT_DHIKR.size >= 10)
    }

    @Test fun suggestedKindFollowsTheDay() {
        val t = PrayerSchedule.day(cairo, java.time.LocalDate.of(2026, 11, 20))
        assertEquals(AzkarKind.MORNING, AzkarTimes.suggested(t, local("2026-11-20T07:00")))
        assertEquals(AzkarKind.MORNING, AzkarTimes.suggested(t, local("2026-11-20T13:00")))
        assertEquals(AzkarKind.EVENING, AzkarTimes.suggested(t, local("2026-11-20T15:00")))
        assertEquals(AzkarKind.EVENING, AzkarTimes.suggested(t, local("2026-11-20T03:00")))
    }

    @Test fun remindersComeAfterFajrAndAsr() {
        // فجر القاهرة 4:54 والعصر 2:37
        val (k1, at1) = AzkarTimes.nextReminder(cairo, local("2026-11-20T01:00"))
        assertEquals(AzkarKind.MORNING, k1)
        assertEquals(local("2026-11-20T05:09"), at1)
        val (k2, at2) = AzkarTimes.nextReminder(cairo, local("2026-11-20T09:00"))
        assertEquals(AzkarKind.EVENING, k2)
        assertEquals(local("2026-11-20T14:52"), at2)
        val (k3, at3) = AzkarTimes.nextReminder(cairo, local("2026-11-20T20:00"))
        assertEquals(AzkarKind.MORNING, k3)
        assertEquals(java.time.LocalDate.of(2026, 11, 21), at3.atZone(zone).toLocalDate())
    }

    @Test fun dhikrStaysInsideTheDayWindow() {
        assertEquals(local("2026-11-20T12:00"), AzkarTimes.nextDhikr(local("2026-11-20T10:00"), zone, 2))
        assertEquals(local("2026-11-21T08:00"), AzkarTimes.nextDhikr(local("2026-11-20T21:00"), zone, 2))
        assertEquals(local("2026-11-20T08:00"), AzkarTimes.nextDhikr(local("2026-11-20T02:00"), zone, 3))
        assertEquals(local("2026-11-20T21:59"), AzkarTimes.nextDhikr(local("2026-11-20T20:59"), zone, 1))
    }
}
