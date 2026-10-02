# v0.6 Android device validation

Prepared profiles are a test plan, not completed physical-device measurements.

| Profile | OS | Priority checks |
| --- | --- | --- |
| Pixel 8 / current Pixel | Android 15 (API 35) | Edge-to-edge insets, SAF import/export, audio focus, background resume, large text |
| Samsung Galaxy A52 / comparable midrange | Android 11–14 (API 30–34) | One UI lifecycle, rotation lock, 100k-army day tick, low-memory reload, haptics |
| Xiaomi Redmi Note 8 / comparable older hardware | Android 8–10 (API 26–29) | MIUI process termination, minimum-API JSON/SAF support, reduced animation, audio pause |

For each physical profile: install with the same signing identity; continue a migrated v1/v2/v3 campaign; inspect active missions/battle, portrait URI and protected original backup; save all three slots; force-stop/restart; roundtrip export/import through Documents; enable 160% text and TalkBack; disable sound/music/haptics/animation; run a siege and review damaged buildings; record startup, frame timing, peak memory and day/save latency. Retain package version, device/OS, traces and screenshots.

The bounded `scripts/android_v06_smoke.py` records JSON/XML/PNG on an explicitly selected **disposable emulator**. It never clears app data or uninstalls. Invoke one action at a time, e.g.:

```bash
python3 scripts/android_v06_smoke.py --adb "$ANDROID_HOME/platform-tools/adb" --serial emulator-5554 --output evidence/v06 launch
python3 scripts/android_v06_smoke.py --adb "$ANDROID_HOME/platform-tools/adb" --serial emulator-5554 --output evidence/v06 capture --label menu
```

`tap --label`, `scroll`, `create`, `day`, `verify`, `restart` and `update --apk` support explicit navigation and persistence probes. Create only in an empty disposable campaign slot. `update` compares exact stored campaign JSON before/after `install -r`; it checks signing-family update persistence, not legacy-schema migration. Legacy migration is covered separately by JVM fixtures, and requires the previous APK and original signing key for a real-device update test.

Do not use emulation with software CPU/GPU rendering to certify physical-device 60 FPS or the five-second startup target. Android 26 software-emulator observations and the final build/test evidence are recorded in `V0.6.0-IMPLEMENTATION.md`.
