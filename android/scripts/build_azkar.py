#!/usr/bin/env python3
"""
يبني ملف الأذكار للتطبيق من قاعدة بيانات أذكار الصباح والمساء
(Seen-Arabic/Morning-And-Evening-Adhkar-DB، رخصة MIT) المأخوذة من كتاب
«حصن المسلم من أذكار الكتاب والسنة» للشيخ سعيد بن علي بن وهف القحطاني وموقع hisnmuslim.com.

التعديلات الوحيدة على النص:
1) في الكتاب تأتي صيغ المساء مختصرة وبدون تشكيل بعد ذكر الصباح، مثل:
   «[وإذا أمسى قال: أمسينا وأمسى الملك لله]». هنا تُكتب صيغة المساء كاملة بتشكيل
   ذكر الصباح نفسه، مع تغيير الكلمات التي يتغير بها المعنى فقط (أصبحنا ← أمسينا ...).
2) تصحيح خطأين في تشكيل المصدر: «الْملْكُ» ← «الْمُلْكُ»، و«وَنورَهُ» ← «وَنُورَهُ».
3) توحيد ترتيب علامات التشكيل (Unicode NFC)، ولا يتغير شكل النص.

الاستخدام: python3 build_azkar.py <ar.json من المصدر> <ملف الإخراج>
"""
import json
import sys
import unicodedata

src, out = sys.argv[1], sys.argv[2]
data = json.load(open(src, encoding="utf-8"))
nfc = lambda t: unicodedata.normalize("NFC", t)
for x in data:
    for k in ("content", "count_description", "fadl", "source"):
        x[k] = nfc(x[k])
by_order = {x["order"]: x for x in data}


def swap(text, pairs):
    for old, new in pairs:
        old, new = nfc(old), nfc(new)
        if old not in text:
            sys.exit(f"لم يوجد «{old}» في: {text[:60]}")
        text = text.replace(old, new)
    return text


# (رقم ذكر المساء، رقم ذكر الصباح المقابل، الاستبدالات)
EVENING_FROM_MORNING = [
    (8, 7, [("أَصْبَحْنَا وَأَصْبَحَ", "أَمْسَيْنَا وَأَمْسَى"), ("هَذَا الْيَوْمِ", "هَذِهِ اللَّيْلَةِ"), ("مَا بَعْدَهُ", "مَا بَعْدَهَا")]),
    (10, 9, [("بِكَ أَصْبَحْنَا", "@@1"), ("بِكَ أَمْسَيْنَا", "بِكَ أَصْبَحْنَا"), ("@@1", "بِكَ أَمْسَيْنَا"), ("النُّشُورُ", "الْمَصِيرُ")]),
    (13, 12, [("أَصْبَحْتُ", "أَمْسَيْتُ")]),
    (15, 14, [("مَا أَصْبَحَ بِي", "مَا أَمْسَى بِي")]),
    (24, 23, [
        ("أَصْبَحْنَا وَأَصْبَحَ", "أَمْسَيْنَا وَأَمْسَى"), ("هَذَا الْيَوْمِ", "هَذِهِ اللَّيْلَةِ"),
        ("فَتْحَهُ", "فَتْحَهَا"), ("وَنَصْرَهُ", "وَنَصْرَهَا"), ("وَنُورَهُ", "وَنُورَهَا"),
        ("وَبَرَكَتَهُ", "وَبَرَكَتَهَا"), ("وَهُدَاهُ", "وَهُدَاهَا"), ("مَا فِيهِ", "مَا فِيهَا"), ("مَا بَعْدَهُ", "مَا بَعْدَهَا"),
    ]),
    (26, 25, [("أَصْبَحْنا", "أَمْسَيْنَا")]),
]

by_order[7]["content"] = swap(by_order[7]["content"], [("الْملْكُ", "الْمُلْكُ")])
by_order[23]["content"] = swap(by_order[23]["content"], [("وَنورَهُ", "وَنُورَهُ")])
for ev, mo, pairs in EVENING_FROM_MORNING:
    by_order[ev]["content"] = swap(by_order[mo]["content"], pairs)

result = [
    {k: x[k] for k in ("order", "content", "count", "count_description", "fadl", "source", "type")}
    for x in sorted(by_order.values(), key=lambda x: x["order"])
]
json.dump(result, open(out, "w", encoding="utf-8"), ensure_ascii=False, indent=1)
print(f"wrote {len(result)} adhkar to {out}")
