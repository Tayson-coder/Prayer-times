package app.azkar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

class AzkarTest {
    private val all = AzkarData.parse(
        listOf("src/main/assets/azkar.json", "app/src/main/assets/azkar.json", "android/app/src/main/assets/azkar.json")
            .map(::File).first { it.exists() }.readText(),
    )
    private val morning = AzkarData.of(all, AzkarKind.MORNING)
    private val evening = AzkarData.of(all, AzkarKind.EVENING)
    private val diacritics = Regex("[ً-ْ]")

    @Test fun countsMatchTheBook() {
        assertEquals(34, all.size)
        assertEquals(26, morning.size)
        assertEquals(24, evening.size)
        assertEquals(listOf(1, 1, 3, 3, 3), morning.take(5).map { it.count })
        assertTrue(morning.any { it.count == 4 } && evening.any { it.count == 4 })
        assertTrue(morning.count { it.count == 100 } == 3)
    }

    @Test fun everyDhikrIsFullyVowelized() {
        for (d in all) {
            val letters = d.text.count { it in 'ء'..'ي' }
            val marks = diacritics.findAll(d.text).count()
            assertTrue("قليل التشكيل: ${d.order}", marks >= letters * 0.6)
        }
    }

    @Test fun morningAndEveningWordingIsCorrect() {
        assertTrue(morning.none { it.text.contains("أَمْسَيْتُ") || it.text.startsWith("أَمْسَيْنَا") })
        assertTrue(evening.none { it.text.contains("أَصْبَحْتُ") || it.text.startsWith("أَصْبَحْنَا") })
        assertTrue(evening.any { it.text.startsWith("أَمْسَيْنَا وَأَمْسَى الْمُلْكُ لِلَّهِ") })
        assertTrue(evening.any { it.text.contains("وَإِلَيْكَ الْمَصِيرُ") })
        assertTrue(morning.any { it.text.contains("وَإِلَيْكَ النُّشُورُ") })
        assertFalse(all.any { it.text.contains("الْملْكُ") })
        // آية الكرسي في الاثنين، وخواتيم البقرة مساءً فقط
        assertTrue(morning[1].text.contains("الْحَيُّ الْقَيُّومُ") && evening[1].text.contains("الْحَيُّ الْقَيُّومُ"))
        assertTrue(evening.any { it.text.contains("آمَنَ الرَّسُولُ") } && morning.none { it.text.contains("آمَنَ الرَّسُولُ") })
    }

    @Test fun shortDhikrForNotifications() {
        val s = AzkarData.short(all)
        assertTrue(s.size >= 8)
        assertTrue(s.contains("سُبْحَانَ اللَّهِ وَبِحَمْدِهِ"))
    }

    private val zone = ZoneId.of("Africa/Cairo")
    private fun local(s: String) = LocalDateTime.parse(s).atZone(zone).toInstant()

    @Test fun timing() {
        assertEquals(AzkarKind.MORNING, AzkarTimes.suggested(LocalDateTime.parse("2026-11-20T06:00").atZone(zone)))
        assertEquals(AzkarKind.EVENING, AzkarTimes.suggested(LocalDateTime.parse("2026-11-20T16:00").atZone(zone)))
        assertEquals(AzkarKind.EVENING, AzkarTimes.suggested(LocalDateTime.parse("2026-11-20T01:00").atZone(zone)))
        assertEquals(local("2026-11-20T06:30"), AzkarTimes.nextDaily(local("2026-11-20T05:00"), zone, LocalTime.of(6, 30)))
        assertEquals(local("2026-11-21T06:30"), AzkarTimes.nextDaily(local("2026-11-20T06:30"), zone, LocalTime.of(6, 30)))
        assertEquals(local("2026-11-21T08:00"), AzkarTimes.nextDhikr(local("2026-11-20T21:00"), zone, 2))
        assertEquals(local("2026-11-20T12:00"), AzkarTimes.nextDhikr(local("2026-11-20T10:00"), zone, 2))
        assertEquals(LocalTime.of(17, 0), AzkarTimes.parseTime("bad", LocalTime.of(17, 0)))
    }
}
