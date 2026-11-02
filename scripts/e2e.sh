#!/usr/bin/env bash
# Emülatörde uçtan uca test. android-emulator-runner içinden çağrılır.
set -x
OUT=${OUT:-e2e-out}
mkdir -p "$OUT/shots"
APP=com.ozzystore35.vintagehunter
adb wait-for-device
adb shell settings put global window_animation_scale 0
adb shell settings put global transition_animation_scale 0
adb shell settings put global animator_duration_scale 0
adb install -r -g apks/app-debug.apk
adb install -r -g apks/testsim-debug.apk
adb shell cmd notification allow_listener $APP/$APP.VHListenerService
adb shell dumpsys deviceidle whitelist +$APP
adb shell am start -n com.vhtest.dolap/.ListingActivity
sleep 2
adb shell input keyevent KEYCODE_HOME
adb logcat -c
adb shell am start -n $APP/.MainActivity
sleep 12
python3 scripts/e2e.py "$OUT" 2>&1 | tee "$OUT/e2e.log"
RC=${PIPESTATUS[0]}
adb logcat -d > "$OUT/logcat.txt"
adb shell dumpsys notification --noredact > "$OUT/notifications.txt"
exit $RC
