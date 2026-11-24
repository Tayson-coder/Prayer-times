#!/usr/bin/env bash
# يشغّل التطبيق على المحاكي ويلتقط صوراً لشاشاته، ويتحقق من عدم وجود أعطال
set -uo pipefail
cd "$(dirname "$0")/.."
PKG=app.azkar.sabahmasaa
CLS=app.azkar
OUT=../screenshots
mkdir -p "$OUT"
rm -f "$OUT"/*.png

adb root >/dev/null 2>&1; sleep 4; adb wait-for-device
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell pm grant $PKG android.permission.POST_NOTIFICATIONS || true
adb shell settings put global auto_time 0 || true
adb shell settings put global hide_error_dialogs 1 || true
sleep 20
adb shell am broadcast -a android.intent.action.CLOSE_SYSTEM_DIALOGS >/dev/null 2>&1 || true

# موقع القاهرة لحساب المواقيت
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
adb push /tmp/prefs.xml /data/local/tmp/prefs.xml >/dev/null
adb shell chmod 644 /data/local/tmp/prefs.xml
adb shell "run-as $PKG mkdir -p shared_prefs && run-as $PKG cp /data/local/tmp/prefs.xml shared_prefs/${PKG}_preferences.xml"

shot() { sleep "${2:-3}"; adb exec-out screencap -p > "$OUT/$1.png"; echo "shot $1"; }
start() {
  adb shell am force-stop $PKG
  adb shell am broadcast -a android.intent.action.CLOSE_SYSTEM_DIALOGS >/dev/null 2>&1 || true
  adb shell am start -W -n "$PKG/$CLS.$1" "${@:2}" >/dev/null
}
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

adb shell "date -u 112004002026.00" >/dev/null       # 06:00 القاهرة: وقت أذكار الصباح
adb shell cmd uimode night no
start MainActivity; shot 1-home 5
start ReaderActivity --es kind MORNING; shot 2-reader-first
tap_id next; shot 3-reader-ayat-alkursi 2
tap_id next; sleep 1; tap_id tasbih; sleep 0.5; tap_id tasbih; shot 4-reader-ikhlas-counting 2
tap_id info; shot 5-info-dialog 2
adb shell input keyevent KEYCODE_BACK
start MainActivity; shot 6-home-progress
adb shell input swipe 540 1700 540 700 300; shot 6b-home-scrolled 2
adb shell cmd uimode night yes
start ReaderActivity --es kind EVENING; shot 7-reader-evening-dark
start MainActivity; shot 8-home-dark
adb shell cmd uimode night no
start SettingsActivity; shot 9-settings
start TasbihActivity; sleep 2
for i in 1 2 3 4 5 6 7; do tap_id tasbih; sleep 0.3; done
shot 11-tasbih 2
adb shell cmd uimode night yes
start TasbihActivity; shot 12-tasbih-dark
adb shell cmd uimode night no
adb shell input swipe 540 1800 540 600 300 >/dev/null 2>&1 || true
adb shell am broadcast -n "$PKG/$CLS.ReminderReceiver" -a app.azkar.REMIND --es kind MORNING >/dev/null
adb shell am broadcast -n "$PKG/$CLS.ReminderReceiver" -a app.azkar.DHIKR >/dev/null
sleep 2
adb shell cmd statusbar expand-notifications
shot 10-notifications 3
adb shell cmd statusbar collapse

adb logcat -d -b crash > "$OUT/crash-log.txt" || true
if grep -q "$PKG\|$CLS" "$OUT/crash-log.txt"; then echo "APP CRASHED:"; cat "$OUT/crash-log.txt"; exit 1; fi
rm -f "$OUT/crash-log.txt"
echo "no crashes"
