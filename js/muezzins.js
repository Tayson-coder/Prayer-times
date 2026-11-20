/*
 * قائمة المؤذنين المشاهير.
 * كل مؤذن يُبحث له عن صوت بهذا الترتيب:
 *   1) ملف رفعه المستخدم من داخل التطبيق (محفوظ في المتصفح)
 *   2) رابط مباشر أدخله المستخدم في الإعدادات
 *   3) الملف المحلي audio/<id>.mp3 داخل مجلد المشروع
 *   4) المصدر العام (url) من الإنترنت
 * لو لم يوجد أي منها يُشغَّل تنبيه صوتي بديل.
 */
(function (root) {
  'use strict';
  // تسجيلات عامة على GitHub (abodehq/Athan-MP3، مثبّتة على نسخة محددة)
  const athan = (f) =>
    'https://raw.githubusercontent.com/abodehq/Athan-MP3/cf1a9dd4f1e5e10009439099c5af74479ad332c0/Sounds/' +
    encodeURIComponent(f);
  const kiwi = (f) =>
    'https://raw.githubusercontent.com/Kiwifu/adhan-mp3/b9180a2bb769f74cff8e378ef2cc7aaf8db5cd5b/' +
    encodeURIComponent(f);
  const MUEZZINS = [
    { id: 'abdulbasit', name: 'الشيخ عبد الباسط عبد الصمد', note: 'أذان القاهرة', url: athan('Athan Abed Albase6.mp3') },
    { id: 'refaat', name: 'الشيخ محمد رفعت', note: 'أذان القاهرة', url: athan('Athan Mohammad Ref3at.mp3') },
    { id: 'naina', name: 'الشيخ أحمد نعينع', note: 'أذان القاهرة', url: athan('Athan Ahmad Nuyne3.mp3') },
    { id: 'minshawi', name: 'الشيخ محمد صديق المنشاوي', note: 'مصر', url: athan('Athan Mohammad Almenshawy.mp3') },
    { id: 'makkah', name: 'أذان الحرم المكي', note: 'مكة المكرمة', url: athan('Athan Makkah.mp3') },
    { id: 'afasy', name: 'الشيخ مشاري راشد العفاسي', note: 'الكويت', url: athan('Athan Mishary Alafasi.mp3') },
    { id: 'qatami', name: 'الشيخ ناصر القطامي', note: 'السعودية', url: athan('Athan Nasser Alqatami.mp3') },
    { id: 'malekfajr', name: 'الشيخ مالك شيبة الحمد', note: 'أذان الفجر (الصلاة خير من النوم)', url: athan('Athan Al-fajer - Malek chebae.mp3') },
    { id: 'zahrani', name: 'الشيخ منصور الزهراني', note: 'السعودية', url: athan('Athan Mansoor Az-Zahrani.mp3') },
    { id: 'hamathani', name: 'الشيخ ماجد الهمذاني', note: 'السعودية', url: athan('Athan Majed Al-hamathani.mp3') },
    { id: 'arkani', name: 'الشيخ إبراهيم الأركاني', note: 'السعودية', url: athan('Athan Ibrahim Al-Arkani.mp3') },
    { id: 'deghreri', name: 'الشيخ حمد دغريري', note: 'السعودية', url: athan('Athan Hamad Deghreri.mp3') },
    { id: 'malki', name: 'الشيخ حمدان المالكي', note: 'مسجد الملك فهد بنجران', url: athan('Athan Hamdan Almalki.mp3') },
    { id: 'suhaib', name: 'الشيخ صهيب هاني', note: 'فلسطين', url: athan('Athan Suhaib Khatba.mp3') },
    { id: 'tobar', name: 'الشيخ نصر الدين طوبار', note: 'مصر', url: kiwi('Nasreddine_Toubar_(نصر_الدين_طوبار).mp3') },
    { id: 'tobarfajr', name: 'الشيخ نصر الدين طوبار — الفجر', note: 'أذان الفجر', url: kiwi('Fajr_Adhan_by_Nasreddine_Toubar_(اذان_الفجر_بصوت_نصر_الدين_طوبار).mp3') },
    { id: 'husary', name: 'الشيخ محمود خليل الحصري', note: 'أذان القاهرة', url: kiwi('Mahmoud_Khalil_Al_Hussary_-_Cairo_(محمود_خليل_الحصري_-_القاهرة).mp3') },
    { id: 'banna', name: 'الشيخ محمود علي البنا', note: 'أذان القاهرة', url: kiwi('Mahmoud_Ali_Al_Banna_-_Cairo_(محمود_علي_البنا_-_القاهرة).mp3') },
    { id: 'mustafaismail', name: 'الشيخ مصطفى إسماعيل', note: 'مصر', url: kiwi('Mustafa_Ismail_-_Egypt_(مصطفى_إسماعيل_-_مصر).mp3') },
    { id: 'mulla', name: 'الشيخ علي أحمد ملا', note: 'مؤذن الحرم المكي', url: kiwi('Ali_Ibn_Ahmad_Mala_1_-_Al_Haram_Al_Maki_(علي_بن_أحمد_ملا_-_الحرم_المكي).mp3') },
    { id: 'hadrawi', name: 'الشيخ فاروق حضراوي', note: 'مؤذن الحرم المكي', url: kiwi('Faruq_Abdul_Rahman_Hadrawe_-_Al_Haram_Al_Maki_(فاروق_عبدالرحمن_حضراوي_-_الحرم_المكي).mp3') },
    { id: 'madani', name: 'أذان الحرم النبوي', note: 'المدينة المنورة', url: kiwi('Adhan_Al_Haram_Al_Madani_-_Al_Madinah_1_(أذان_الحرم_المدني_-_المدينة_المنورة).mp3') },
    { id: 'abdulmajeed', name: 'الشيخ عبد المجيد السريحي', note: 'مؤذن المسجد النبوي', url: kiwi('Abdul_Majid_Al_Surehi_1_(عبدالمجيد_السريحي).mp3') },
    { id: 'aqsa', name: 'أذان المسجد الأقصى', note: 'القدس', url: kiwi('Adhan_Al_Aqsa_-_Jerusalem_(أذان_المسجد_الأقصى_-_القدس).mp3') },
    { id: 'dosari', name: 'الشيخ ياسر الدوسري', note: 'السعودية', url: kiwi('Yasser_Al-Dosari_-_Saudi_Arabia_(ياسر_الدوسري_-_المملكة_العربية_السعودية).mp3') },
    { id: 'bukhari', name: 'الشيخ عصام بخاري', note: 'المسجد النبوي — أضف الملف بنفسك' },
  ];
  root.MUEZZINS = MUEZZINS;
})(self);
