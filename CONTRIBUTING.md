# Contributing

This repository is a small Android sandbox for exploring unidirectional data flow (UDF), navigation, and Compose UI behavior. Keep changes focused and explain what the experiment is intended to demonstrate.

## Local setup

Install Android Studio with the Android SDK, JDK 17, and an Android emulator or device. The project pins its Gradle version through the wrapper and its Java major version in `.java-version`.

On macOS with the Homebrew `openjdk@17` formula:

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
./gradlew --version
./gradlew testDebugUnitTest lintDebug assembleDebug
```

Use a feature branch and open a pull request. Prefer small commits and include tests for changed behavior. UI changes should include a screenshot or short recording.

## Pull request checklist

Before opening a pull request:

1. Run `./gradlew testDebugUnitTest lintDebug assembleDebug`.
2. Check navigation and Android back behavior.
3. Check state restoration after activity or process recreation where relevant.
4. Remove secrets and personal data from logs, screenshots, fixtures, and commits.
