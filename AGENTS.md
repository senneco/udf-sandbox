# Repository guidelines

## Project

`udf-sandbox` is a single-module Android application for experiments with Kotlin, Jetpack Compose, navigation, and unidirectional data flow.

## Development

- Use JDK 17 and the committed Gradle wrapper.
- Keep experiments small and avoid unrelated dependency or formatting churn.
- Run `./gradlew testDebugUnitTest lintDebug assembleDebug` before submitting changes.
- Add tests for changed state transitions, navigation, and user-visible behavior.
- Never commit signing keys, tokens, credentials, `local.properties`, or generated build output.

## Code Review Rules

- Prioritize crashes, invalid state transitions, lost state after recreation, and broken back/navigation behavior.
- Check that asynchronous effects cannot update stale state or run more than once unintentionally.
- Treat unsafe null assertions, exact floating-point comparisons, and release-only logging or backup behavior as risks when they affect the changed code.
- Require evidence for behavior changes: focused tests plus screenshots or a short recording for UI changes.
- Flag dependency changes that update only one incompatible part of the Gradle, Android Gradle Plugin, Kotlin, or Compose toolchain.
