package app.prayertimes.azan

enum class Prayer(val key: String, val arabic: String, val hasAzan: Boolean) {
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

/**
 * المؤذنون المشاهير. معرّف كل مؤذن (id) هو أيضاً اسم الملف الصوتي:
 * - ملف يختاره المستخدم أو يحمّله من داخل التطبيق
 * - أو رابط مباشر يدخله المستخدم
 * - أو ملف مدمج في التطبيق: app/src/main/res/raw/<id>.mp3
 * url: مصدر عام للتحميل بلمسة واحدة (null = لا يوجد مصدر، يضيفه المستخدم بنفسه)
 */
data class Muezzin(val id: String, val name: String, val note: String, val url: String? = null)

/** تسجيلات عامة على GitHub (المستودع abodehq/Athan-MP3، مثبّتة على نسخة محددة) */
private fun athan(file: String) =
    "https://raw.githubusercontent.com/abodehq/Athan-MP3/cf1a9dd4f1e5e10009439099c5af74479ad332c0/Sounds/" +
        file.replace(" ", "%20")

val MUEZZINS = listOf(
    Muezzin("abdulbasit", "الشيخ عبد الباسط عبد الصمد", "أذان القاهرة", athan("Athan Abed Albase6.mp3")),
    Muezzin("refaat", "الشيخ محمد رفعت", "أذان القاهرة", athan("Athan Mohammad Ref3at.mp3")),
    Muezzin("naina", "الشيخ أحمد نعينع", "أذان القاهرة", athan("Athan Ahmad Nuyne3.mp3")),
    Muezzin("minshawi", "الشيخ محمد صديق المنشاوي", "مصر", athan("Athan Mohammad Almenshawy.mp3")),
    Muezzin("makkah", "أذان الحرم المكي", "مكة المكرمة", athan("Athan Makkah.mp3")),
    Muezzin("afasy", "الشيخ مشاري راشد العفاسي", "الكويت", athan("Athan Mishary Alafasi.mp3")),
    Muezzin("qatami", "الشيخ ناصر القطامي", "السعودية", athan("Athan Nasser Alqatami.mp3")),
    Muezzin("malekfajr", "الشيخ مالك شيبة الحمد", "أذان الفجر (الصلاة خير من النوم)", athan("Athan Al-fajer - Malek chebae.mp3")),
    Muezzin("zahrani", "الشيخ منصور الزهراني", "السعودية", athan("Athan Mansoor Az-Zahrani.mp3")),
    Muezzin("hamathani", "الشيخ ماجد الهمذاني", "السعودية", athan("Athan Majed Al-hamathani.mp3")),
    Muezzin("arkani", "الشيخ إبراهيم الأركاني", "السعودية", athan("Athan Ibrahim Al-Arkani.mp3")),
    Muezzin("deghreri", "الشيخ حمد دغريري", "السعودية", athan("Athan Hamad Deghreri.mp3")),
    Muezzin("malki", "الشيخ حمدان المالكي", "مسجد الملك فهد بنجران", athan("Athan Hamdan Almalki.mp3")),
    Muezzin("suhaib", "الشيخ صهيب هاني", "فلسطين", athan("Athan Suhaib Khatba.mp3")),
    // بدون مصدر عام: يضيف المستخدم الملف بنفسه
    Muezzin("tobar", "الشيخ نصر الدين طوبار", "مصر — أضف الملف بنفسك"),
    Muezzin("husary", "الشيخ محمود خليل الحصري", "مصر — أضف الملف بنفسك"),
    Muezzin("banna", "الشيخ محمود علي البنا", "مصر — أضف الملف بنفسك"),
    Muezzin("mulla", "الشيخ علي أحمد ملا", "الحرم المكي — أضف الملف بنفسك"),
    Muezzin("hadrawi", "الشيخ فاروق حضراوي", "الحرم المكي — أضف الملف بنفسك"),
    Muezzin("bukhari", "الشيخ عصام بخاري", "المسجد النبوي — أضف الملف بنفسك"),
    Muezzin("abdulmajeed", "الشيخ عبد المجيد السريحي", "المسجد النبوي — أضف الملف بنفسك"),
    Muezzin("aqsa", "أذان المسجد الأقصى", "القدس — أضف الملف بنفسك"),
)

fun muezzinById(id: String?): Muezzin = MUEZZINS.firstOrNull { it.id == id } ?: MUEZZINS[0]

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
