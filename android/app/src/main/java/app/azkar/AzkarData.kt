package app.azkar

import org.json.JSONArray
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * ذكر من «حصن المسلم» كما هو في assets/azkar.json.
 * type: 0 صباحاً ومساءً، 1 صباحاً فقط، 2 مساءً فقط.
 */
data class Dhikr(
    val order: Int,
    val text: String,
    val count: Int,
    val countText: String,
    val fadl: String,
    val source: String,
    val type: Int,
)

enum class AzkarKind(val title: String, private val types: Set<Int>) {
    MORNING("أذكار الصباح", setOf(0, 1)),
    EVENING("أذكار المساء", setOf(0, 2));

    fun includes(d: Dhikr) = d.type in types
}

object AzkarData {
    fun parse(json: String): List<Dhikr> {
        val arr = JSONArray(json)
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            Dhikr(
                order = o.getInt("order"),
                text = o.getString("content").trim(),
                count = o.getInt("count").coerceAtLeast(1),
                countText = o.optString("count_description").trim(),
                fadl = o.optString("fadl").trim(),
                source = o.optString("source").trim(),
                type = o.getInt("type"),
            )
        }.sortedBy { it.order }
    }

    fun of(all: List<Dhikr>, kind: AzkarKind) = all.filter(kind::includes)

    /** أذكار قصيرة من نفس المصدر لإشعارات اليوم (بدون الآيات الطويلة) */
    fun short(all: List<Dhikr>) = all.filter { it.text.length <= 130 && '﴿' !in it.text }.map { it.text }.distinct()
}

/** حسابات الأوقات (منطق خالص قابل للاختبار) */
object AzkarTimes {
    /** من ٣ فجراً حتى قبل ٣ عصراً أذكار الصباح، وغير ذلك أذكار المساء */
    fun suggested(now: ZonedDateTime): AzkarKind = if (now.hour in 3..14) AzkarKind.MORNING else AzkarKind.EVENING

    /** أول موعد يومي بعد اللحظة المعطاة عند الساعة المحددة بالتوقيت المحلي */
    fun nextDaily(after: Instant, zone: ZoneId, time: LocalTime): Instant {
        val local = after.atZone(zone)
        var at = local.toLocalDate().atTime(time).atZone(zone)
        if (!at.toInstant().isAfter(after)) at = local.toLocalDate().plusDays(1).atTime(time).atZone(zone)
        return at.toInstant()
    }

    /** موعد إشعار الذكر القادم بعد intervalHours، داخل نافذة اليوم [startHour, endHour) */
    fun nextDhikr(after: Instant, zone: ZoneId, intervalHours: Int, startHour: Int = 8, endHour: Int = 22): Instant {
        val candidate = after.plus(Duration.ofHours(intervalHours.coerceIn(1, 12).toLong())).atZone(zone)
        val dayStart = candidate.toLocalDate().atTime(startHour, 0).atZone(zone)
        val dayEnd = candidate.toLocalDate().atTime(endHour, 0).atZone(zone)
        return when {
            candidate.isBefore(dayStart) -> dayStart.toInstant()
            !candidate.isBefore(dayEnd) -> dayStart.plusDays(1).toInstant()
            else -> candidate.toInstant()
        }
    }

    fun parseTime(s: String?, fallback: LocalTime): LocalTime =
        runCatching { LocalTime.parse(s ?: "") }.getOrDefault(fallback)
}
