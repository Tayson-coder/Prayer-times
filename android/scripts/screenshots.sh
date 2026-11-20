#!/usr/bin/env bash
# يشغّل التطبيق على المحاكي ويلتقط صوراً لشاشاته في أوقات مختلفة من اليوم، ويتحقق من عدم وجود أعطال
set -uo pipefail
cd "$(dirname "$0")/.."
PKG=app.prayertimes.azan
OUT=../screenshots
mkdir -p "$OUT"
rm -f "$OUT"/*.png

adb root >/dev/null 2>&1; sleep 4; adb wait-for-device
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell pm grant $PKG android.permission.POST_NOTIFICATIONS || true
adb shell settings put global auto_time 0 || true

cat > /tmp/prefs.xml <<'XML'
<?xml version='1.0' encoding='utf-8' standalone='yes' ?>
<map>
    <string name="lat">30.0444</string>
    <string name="lng">31.2357</string>
    <string name="location_name">القاهرة</string>
    <string name="tz">Africa/Cairo</string>
    <string name="city">0</string>
    <string name="method">egypt</string>
</map>
XML
adb push /tmp/prefs.xml /data/local/tmp/prefs.xml
adb shell chmod 644 /data/local/tmp/prefs.xml
adb shell "run-as $PKG mkdir -p shared_prefs && run-as $PKG cp /data/local/tmp/prefs.xml shared_prefs/${PKG}_preferences.xml"

shot() { sleep "${2:-4}"; adb exec-out screencap -p > "$OUT/$1.png"; echo "shot $1"; }
at() { adb shell "date -u $1" >/dev/null; }             # MMDDhhmmYYYY.ss (UTC)
launch() { adb shell am force-stop $PKG; adb shell am start -W -n "$PKG/.$1" >/dev/null; }
tap_id() {
  adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
  adb pull /sdcard/ui.xml /tmp/ui.xml >/dev/null 2>&1
  python3 - "$PKG:id/$1" <<'PY' | { read -r x y && adb shell input tap "$x" "$y"; }
import re, sys
s = open('/tmp/ui.xml', encoding='utf-8').read()
m = re.search(r'resource-id="%s"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"' % re.escape(sys.argv[1]), s)
x1, y1, x2, y2 = map(int, m.groups())
print((x1 + x2) // 2, (y1 + y2) // 2)
PY
}

adb shell cmd uimode night no
at 112010202026.00; launch MainActivity; shot 1-main-afternoon 6     # 12:20 القاهرة: بين الظهر والعصر
at 112015102026.00; launch MainActivity; shot 2-main-maghrib          # 17:10: بعد المغرب
tap_id muezzin_row; shot 3-muezzin-picker 2
adb shell input keyevent KEYCODE_BACK
adb shell cmd uimode night yes
at 112019002026.00; launch MainActivity; shot 4-main-night-dark      # 21:00: ليلاً، الوضع الداكن
at 112003302026.00; launch MainActivity; shot 5-main-fajr-dark        # 05:30: بين الفجر والشروق
adb shell cmd uimode night no
launch SettingsActivity; shot 6-settings
launch AudioActivity; shot 7-audio
adb shell cmd uimode night yes
launch AudioActivity; shot 8-audio-dark
adb shell cmd uimode night no

adb logcat -d -b crash > "$OUT/crash-log.txt" || true
if grep -q "$PKG" "$OUT/crash-log.txt"; then echo "APP CRASHED:"; cat "$OUT/crash-log.txt"; exit 1; fi
rm -f "$OUT/crash-log.txt"
echo "no crashes"
