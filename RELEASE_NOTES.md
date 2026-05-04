DPAD Messaging Release v1.0.0
=============================

Summary
- D-pad friendly SMS/MMS messaging app optimized for low-RAM devices and hardware D-pad navigation.

Notable changes in this release
- D-pad navigation fixes and clear focus highlights (three-dot menu focus)
- Coil tuned for low-RAM devices (12MB mem cache, 32MB disk cache, downscale MMS thumbnails)
- Message paging: initial load limited to last 50 messages; "Load earlier" to fetch older messages
- Fixed delete bug: deleteThread now deletes via Threads URI (safe, scoped conversation deletion)
- README and screenshots added; Buy Me A Coffee support link and FUNDING.yml

How to install
- Debug APK: app/build/outputs/apk/debug/app-debug.apk
- Release APK (signed): app/build/outputs/apk/release/app-release.apk
- Install with: adb install -r <apk-path>

Notes
- Release APK is signed using keystore.properties if present in repo root; otherwise the local debug keystore (~/.android/debug.keystore) is used for convenience.
- MMS delivery depends on carrier; test on real devices.
