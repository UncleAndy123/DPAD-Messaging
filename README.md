# DPAD SMS

A messaging app designed for dumbphones with D-pad navigation.

## Features

- SMS/MMS send and receive
- Group messaging with fan-out or group MMS
- D-pad optimized navigation
- Dark/light theme with customizable accent colors
- Pin, archive, mute, and delete conversations
- Recycle bin with recovery
- Keyword-based message blocking
- Delivery reports
- Scheduled messages
- Contact lookup with auto-suggest

## Requirements

- Android 6.0+ (API 23+)
- Default SMS app permission

## Building from Source

### Prerequisites

- Android Studio (2023+)
- Gradle 8.5+
- Java 17

### Build Commands

```bash
# Debug build
./gradlew assembleDebug

# Release build
./gradlew assembleRelease
```

The APK will be generated at `app/build/outputs/apk/debug/app-debug.apk` or `app/build/outputs/apk/release/app-release.apk`

## Project Structure

```
app/src/main/
├── kotlin/com/dpad/messaging/
│   ├── activities/       # Activity classes
│   ├── adapters/         # RecyclerView adapters
│   ├── databases/        # Room database
│   ├── helpers/          # Utility classes (ThemeManager, Prefs, MmsSender, etc.)
│   ├── models/           # Data models
│   ├── receivers/        # Broadcast receivers
│   └── services/         # Background services
└── res/
    ├── layout/           # XML layouts
    ├── values/           # Colors, strings, themes
    └── drawable/         # Icons and drawables
```

## Key Components

### ThemeManager
Handles theme mode (system/light/dark) and accent color selection.

### Prefs
SharedPreferences wrapper for app settings storage.

### MmsSender
Handles MMS message composition and sending via system MmsService.

### SmsSender
Handles SMS message sending via SmsManager.

## Support

If you find this app useful, consider supporting its development:

[![Buy Me a Coffee](https://www.buymeacoffee.com/assets/img/custom_images/orange_img.png)](https://buymeacoffee.com/jbriones95)

## License

MIT
