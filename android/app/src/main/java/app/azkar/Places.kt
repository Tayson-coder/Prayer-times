package app.azkar

enum class Prayer(val key: String, val arabic: String, val isPrayer: Boolean) {
    FAJR("fajr", "الفجر", true),
    SUNRISE("sunrise", "الشروق", false),
    DHUHR("dhuhr", "الظهر", true),
    ASR("asr", "العصر", true),
    MAGHRIB("maghrib", "المغرب", true),
    ISHA("isha", "العشاء", true);

    companion object {
        fun fromKey(key: String?): Prayer? = entries.firstOrNull { it.key == key }
    }
}

data class City(val name: String, val lat: Double, val lng: Double, val tz: String, val method: String)

val CITIES = listOf(
    City("القاهرة", 30.0444, 31.2357, "Africa/Cairo", "egypt"),
    City("الإسكندرية", 31.2001, 29.9187, "Africa/Cairo", "egypt"),
    City("مكة المكرمة", 21.4225, 39.8262, "Asia/Riyadh", "makkah"),
    City("المدينة المنورة", 24.4672, 39.6111, "Asia/Riyadh", "makkah"),
    City("الرياض", 24.7136, 46.6753, "Asia/Riyadh", "makkah"),
    City("جدة", 21.4858, 39.1925, "Asia/Riyadh", "makkah"),
    City("دبي", 25.2048, 55.2708, "Asia/Dubai", "dubai"),
    City("أبوظبي", 24.4539, 54.3773, "Asia/Dubai", "dubai"),
    City("الكويت", 29.3759, 47.9774, "Asia/Kuwait", "kuwait"),
    City("الدوحة", 25.2854, 51.531, "Asia/Qatar", "qatar"),
    City("المنامة", 26.2285, 50.586, "Asia/Bahrain", "gulf"),
    City("مسقط", 23.588, 58.3829, "Asia/Muscat", "gulf"),
    City("عمّان", 31.9454, 35.9284, "Asia/Amman", "jordan"),
    City("القدس", 31.7683, 35.2137, "Asia/Jerusalem", "jordan"),
    City("بيروت", 33.8938, 35.5018, "Asia/Beirut", "mwl"),
    City("دمشق", 33.5138, 36.2765, "Asia/Damascus", "egypt"),
    City("بغداد", 33.3152, 44.3661, "Asia/Baghdad", "mwl"),
    City("الخرطوم", 15.5007, 32.5599, "Africa/Khartoum", "egypt"),
    City("طرابلس", 32.8872, 13.1913, "Africa/Tripoli", "egypt"),
    City("تونس", 36.8065, 10.1815, "Africa/Tunis", "tunisia"),
    City("الجزائر", 36.7538, 3.0588, "Africa/Algiers", "algeria"),
    City("الرباط", 34.0209, -6.8416, "Africa/Casablanca", "morocco"),
    City("الدار البيضاء", 33.5731, -7.5898, "Africa/Casablanca", "morocco"),
    City("صنعاء", 15.3694, 44.191, "Asia/Aden", "makkah"),
    City("إسطنبول", 41.0082, 28.9784, "Europe/Istanbul", "turkey"),
    City("لندن", 51.5074, -0.1278, "Europe/London", "mwl"),
    City("باريس", 48.8566, 2.3522, "Europe/Paris", "mwl"),
    City("برلين", 52.52, 13.405, "Europe/Berlin", "mwl"),
    City("نيويورك", 40.7128, -74.006, "America/New_York", "isna"),
)
