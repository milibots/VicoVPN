# Contributing to VicoVPN

## Development requirements

- Android Studio
- JDK 17
- Android SDK configured through `local.properties`
- The repository Gradle wrapper

## Before opening a pull request

Run:

```bash
./gradlew lintDebug testDebugUnitTest assembleDebug
```

On Windows:

```powershell
.\gradlew.bat lintDebug testDebugUnitTest assembleDebug
```

Check both Persian and English layouts, light and dark themes, onboarding,
connection states, server selection, and bottom navigation behavior.

Never commit subscription keys, VPN configurations, keystores, passwords,
private endpoints, or unsanitized logs.
