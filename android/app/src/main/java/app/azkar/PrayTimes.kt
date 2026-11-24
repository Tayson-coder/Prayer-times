package app.azkar

import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * حساب مواقيت الصلاة فلكياً بدون إنترنت (نفس خوارزمية نسخة الويب).
 * النتائج بالساعات العشرية حسب التوقيت المحلي للموقع.
 */
object PrayTimes {

    /** isha: زاوية بالدرجات، أو ishaMinutes بعد المغرب */
    data class Method(val name: String, val fajr: Double, val isha: Double? = null, val ishaMinutes: Int? = null)

    val METHODS: LinkedHashMap<String, Method> = linkedMapOf(
        "egypt" to Method("الهيئة المصرية العامة للمساحة", 19.5, 17.5),
        "makkah" to Method("أم القرى - مكة المكرمة", 18.5, ishaMinutes = 90),
        "mwl" to Method("رابطة العالم الإسلامي", 18.0, 17.0),
        "gulf" to Method("منطقة الخليج", 19.5, ishaMinutes = 90),
        "dubai" to Method("الإمارات (دبي)", 18.2, 18.2),
        "kuwait" to Method("الكويت", 18.0, 17.5),
        "qatar" to Method("قطر", 18.0, ishaMinutes = 90),
        "jordan" to Method("الأردن", 18.0, 18.0),
        "algeria" to Method("الجزائر", 18.0, 17.0),
        "morocco" to Method("المغرب", 19.0, 17.0),
        "tunisia" to Method("تونس", 18.0, 18.0),
        "turkey" to Method("تركيا (ديانت)", 18.0, 17.0),
        "karachi" to Method("جامعة العلوم الإسلامية - كراتشي", 18.0, 18.0),
        "isna" to Method("أمريكا الشمالية (ISNA)", 15.0, 15.0),
    )

    data class Times(
        val fajr: Double, val sunrise: Double, val dhuhr: Double,
        val asr: Double, val maghrib: Double, val isha: Double,
    ) {
        operator fun get(p: Prayer): Double = when (p) {
            Prayer.FAJR -> fajr
            Prayer.SUNRISE -> sunrise
            Prayer.DHUHR -> dhuhr
            Prayer.ASR -> asr
            Prayer.MAGHRIB -> maghrib
            Prayer.ISHA -> isha
        }
    }

    private fun rad(d: Double) = Math.toRadians(d)
    private fun deg(r: Double) = Math.toDegrees(r)
    private fun dsin(d: Double) = sin(rad(d))
    private fun dcos(d: Double) = cos(rad(d))
    private fun dtan(d: Double) = tan(rad(d))
    private fun darcsin(x: Double) = deg(asin(x))
    private fun darccos(x: Double) = deg(acos(x))
    private fun darctan2(y: Double, x: Double) = deg(atan2(y, x))
    private fun darccot(x: Double) = deg(atan(1 / x))
    private fun fix(a: Double, b: Double): Double {
        val r = a - b * floor(a / b)
        return if (r < 0) r + b else r
    }
    private fun fixAngle(a: Double) = fix(a, 360.0)
    private fun fixHour(a: Double) = fix(a, 24.0)

    fun julian(year: Int, month: Int, day: Int): Double {
        var y = year
        var m = month
        if (m <= 2) { y -= 1; m += 12 }
        val a = floor(y / 100.0)
        val b = 2 - a + floor(a / 4)
        return floor(365.25 * (y + 4716)) + floor(30.6001 * (m + 1)) + day + b - 1524.5
    }

    private class SunPos(val declination: Double, val equation: Double)

    private fun sunPosition(jd: Double): SunPos {
        val d = jd - 2451545.0
        val g = fixAngle(357.529 + 0.98560028 * d)
        val q = fixAngle(280.459 + 0.98564736 * d)
        val l = fixAngle(q + 1.915 * dsin(g) + 0.020 * dsin(2 * g))
        val e = 23.439 - 0.00000036 * d
        val ra = darctan2(dcos(e) * dsin(l), dcos(l)) / 15
        return SunPos(darcsin(dsin(e) * dsin(l)), q / 15 - fixHour(ra))
    }

    fun compute(
        year: Int, month: Int, day: Int,
        lat: Double, lng: Double, tzOffset: Double,
        methodKey: String = "egypt",
        hanafiAsr: Boolean = false,
        adjustMinutes: Map<Prayer, Int> = emptyMap(),
        elevation: Double = 0.0,
    ): Times {
        val method = METHODS[methodKey] ?: METHODS.getValue("egypt")
        val asrFactor = if (hanafiAsr) 2.0 else 1.0
        val jDate = julian(year, month, day) - lng / (15 * 24)

        fun midDay(t: Double) = fixHour(12 - sunPosition(jDate + t).equation)
        fun sunAngleTime(angle: Double, t: Double, ccw: Boolean = false): Double {
            val decl = sunPosition(jDate + t).declination
            val noon = midDay(t)
            val v = darccos((-dsin(angle) - dsin(decl) * dsin(lat)) / (dcos(decl) * dcos(lat))) / 15
            return noon + if (ccw) -v else v
        }
        fun asrTime(factor: Double, t: Double): Double {
            val decl = sunPosition(jDate + t).declination
            val angle = -darccot(factor + dtan(abs(lat - decl)))
            return sunAngleTime(angle, t)
        }
        val riseSet = 0.833 + 0.0347 * sqrt(elevation)

        var fajr = 5.0; var sunrise = 6.0; var dhuhr = 12.0
        var asr = 13.0; var sunset = 18.0; var isha = 18.0
        repeat(2) {
            val nf = sunAngleTime(method.fajr, fajr / 24, true)
            val nr = sunAngleTime(riseSet, sunrise / 24, true)
            val nd = midDay(dhuhr / 24)
            val na = asrTime(asrFactor, asr / 24)
            val ns = sunAngleTime(riseSet, sunset / 24)
            val ni = if (method.isha != null) sunAngleTime(method.isha, isha / 24) else 18.0
            fajr = nf; sunrise = nr; dhuhr = nd; asr = na; sunset = ns; isha = ni
        }

        val shift = tzOffset - lng / 15
        fajr += shift; sunrise += shift; dhuhr += shift; asr += shift; sunset += shift; isha += shift

        // خطوط العرض العالية: طريقة الزاوية
        val night = fixHour(sunrise - sunset)
        val fajrMax = method.fajr / 60 * night
        if (fajr.isNaN() || fixHour(sunrise - fajr) > fajrMax) fajr = sunrise - fajrMax
        if (method.isha != null) {
            val ishaMax = method.isha / 60 * night
            if (isha.isNaN() || fixHour(isha - sunset) > ishaMax) isha = sunset + ishaMax
        } else {
            isha = sunset + (method.ishaMinutes ?: 90) / 60.0
        }

        fun adj(p: Prayer) = (adjustMinutes[p] ?: 0) / 60.0
        return Times(
            fajr + adj(Prayer.FAJR), sunrise + adj(Prayer.SUNRISE), dhuhr + adj(Prayer.DHUHR),
            asr + adj(Prayer.ASR), sunset + adj(Prayer.MAGHRIB), isha + adj(Prayer.ISHA),
        )
    }
}
