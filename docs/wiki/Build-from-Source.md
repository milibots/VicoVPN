# Build from source

## Requirements

- Android Studio
- JDK 17
- Android SDK
- Git
- The Gradle wrapper included in the repository

## Windows

```powershell
git clone REPOSITORY_URL
cd VicoVPN
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat clean lintDebug testDebugUnitTest assembleDebug --no-daemon
```

The debug APK is normally produced under:

```text
app\build\outputs\apk\debug\
```

## Linux or macOS

```bash
git clone REPOSITORY_URL
cd VicoVPN
chmod +x gradlew
./gradlew clean lintDebug testDebugUnitTest assembleDebug --no-daemon
```

`local.properties` is machine-specific and must not be committed.
