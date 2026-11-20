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
    { id: 'tobar', name: 'الشيخ نصر الدين طوبار', note: 'مصر — أضف الملف بنفسك' },
    { id: 'husary', name: 'الشيخ محمود خليل الحصري', note: 'مصر — أضف الملف بنفسك' },
    { id: 'banna', name: 'الشيخ محمود علي البنا', note: 'مصر — أضف الملف بنفسك' },
    { id: 'mulla', name: 'الشيخ علي أحمد ملا', note: 'الحرم المكي — أضف الملف بنفسك' },
    { id: 'hadrawi', name: 'الشيخ فاروق حضراوي', note: 'الحرم المكي — أضف الملف بنفسك' },
    { id: 'bukhari', name: 'الشيخ عصام بخاري', note: 'المسجد النبوي — أضف الملف بنفسك' },
    { id: 'abdulmajeed', name: 'الشيخ عبد المجيد السريحي', note: 'المسجد النبوي — أضف الملف بنفسك' },
    { id: 'aqsa', name: 'أذان المسجد الأقصى', note: 'القدس — أضف الملف بنفسك' },
  ];
  root.MUEZZINS = MUEZZINS;
})(self);
