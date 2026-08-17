# udf-sandbox

An Android sandbox for experimenting with unidirectional data flow (UDF), Jetpack Compose, navigation, animation, and modal UI state.

## Stack

- Kotlin and Jetpack Compose
- A single Android application module
- Gradle Wrapper with JDK 17

The project intentionally remains small so state and navigation ideas can be explored in isolation. It is not a production application or a published library.

## Run locally

Open the repository in Android Studio, select JDK 17 for Gradle, install the Android SDK requested by the project, and run the `app` configuration on an emulator or device.

Command-line verification:

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug
```

See [CONTRIBUTING.md](CONTRIBUTING.md) for local setup and pull request expectations.
