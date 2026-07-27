# Troubleshooting

## CI cannot execute Gradle

Confirm `gradlew` exists and is committed. GitHub Actions runs:

```bash
chmod +x ./gradlew
```

## SDK XML warning

A warning about SDK XML versions usually means Android Studio and command-line
SDK tools were released at different times. Update Android SDK command-line
tools when practical.

## Native library cannot be stripped

Messages about packaging `libgojni.so` without stripping are commonly warnings
rather than build failures. Check the first actual compiler or Gradle error.

## Release contains only a debug APK

Add all four Android signing secrets described in
[Releases and signing](Releases-and-Signing).

## Wiki sync cannot clone

Enable the repository Wiki, create the first Home page once, and confirm that
`WIKI_TOKEN` can write to the Wiki repository.
