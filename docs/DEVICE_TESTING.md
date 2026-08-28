# Reference navigation device suite

Device-suite доказывает, что чистые navigation contracts действительно соединены с настоящими
`MainActivity`, Compose renderer, Material bottom sheet и Android lifecycle. Он дополняет JVM-тесты,
а не заменяет их: exact entry identity, reducer outcomes и projection trees точнее проверяются без
устройства.

## Требования

- JDK 17;
- Android SDK Platform 33 и `platform-tools`;
- один явно выбранный отдельный emulator/device; API 24 — `minSdk`, а не проверенная device matrix;
- debug application ID `com.shmakov.udf` и test application ID `com.shmakov.udf.test`.

Воспроизводимый baseline проекта — `Pixel_4`, API 29. Serial ниже является примером: подставьте ID
своего устройства из `adb devices -l`. Не запускайте suite на общем эмуляторе, которым в этот момент
пользуется другой разработчик или агент.

AVD/device должен быть уже запущен, полностью загружен и разблокирован. Gradle-команда собирает и
устанавливает APK, но не создаёт, не запускает и не разблокирует эмулятор.
Для повторного использования AVD предпочтителен чистый app/task state; при сомнении очистите данные
`com.shmakov.udf` или используйте свежий dedicated AVD. Доказанный baseline — именно API 29;
поддержка остальных API в диапазоне `minSdk..targetSdk` требует отдельного прогона.

```bash
adb devices -l
export ANDROID_SERIAL=emulator-5580
```

## Канонические команды

Compile-time gate, который выполняет и CI:

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest
```

Полный device-suite с автоматической сборкой, установкой и выгрузкой отчёта:

```bash
./gradlew connectedDebugAndroidTest
```

Только black-box reference journeys:

```bash
./gradlew installDebug installDebugAndroidTest
adb -s "$ANDROID_SERIAL" shell am instrument -w -r \
  -e package com.shmakov.udf.e2e \
  com.shmakov.udf.test/androidx.test.runner.AndroidJUnitRunner
```

Полный уже установленный instrumentation APK можно запустить напрямую:

```bash
adb -s "$ANDROID_SERIAL" shell am instrument -w -r \
  com.shmakov.udf.test/androidx.test.runner.AndroidJUnitRunner
```

Не выбирайте первое устройство неявно, если подключено несколько targets. Число тестов и
проверенная конфигурация конкретного релиза фиксируются в issue evidence, а не зашиваются в scripts.

## Что именно проверяется

| Граница | Эталонное доказательство |
| --- | --- |
| Полный пользовательский путь | `ReferenceNavigationE2ETest.homeAccountsSheetDetailsAndBackTraverseVisibleHistory` |
| Один state в single-/expanded-layout | `ReferenceNavigationE2ETest.sameHistoryReprojectsAcrossPortraitAndLandscape`, `EntrySaveableStateRegressionTest.sameExactAccountsEntryMovesRootNestedRootWithoutResetOrDuplicateNode` и pure projection contracts |
| Stacked sheets и быстрый Back | `ReferenceNavigationE2ETest.rapidBackOnStackedSheetsDismissesOnlyCapturedTopSheet` плюс exact-ID reducer/presentation contracts |
| Scrim и swipe dismissal | Два `ReferenceNavigationE2ETest` journey и phase-focused `BottomSheetLayoutRegressionTest` |
| Activity recreation | `MainActivityDeepLinkRegressionTest` и `MainActivityRestorationRegressionTest` |
| Независимый UI state одинаковых routes | `EntrySaveableStateRegressionTest.duplicateRoutesWithDifferentEntryIdsKeepIndependentState` |
| Retained outgoing/modal presentation | `AnimatedNavigationRegressionTest` |

Black-box E2E меняет state только через UI semantics, gestures, Android Back и orientation. Он не
читает `AppViewModel`, revision, transition tokens или renderer internals. Focused tests отдельно
проверяют точные IDs и переходы, поэтому UI journey не пытается косвенно восстановить внутреннюю
модель из цвета или координат.

Scrim намеренно не публикует accessibility click action: доступное закрытие sheet предоставляет
Material collapse semantics. Для автоматизации scrim имеет только internal test tag, который не
озвучивается accessibility-сервисами. Все остальные действия выбираются по видимому тексту,
test tag или `SemanticsActions`; fixed screen coordinates не используются.

## Детерминизм и диагностика

- Не добавляйте `Thread.sleep`, shell `sleep` или произвольное увеличение задержек.
- Ожидайте observable semantics/state predicate через `waitUntil`, `waitForIdle` или управляемый
  Compose clock.
- Два race-события отправляйте в одном UI turn, если проверяется поведение до recomposition. В
  instrumentation-тесте это прямые вызовы `onBackPressedDispatcher`; Maestro-flow использует
  настоящий системный Back уже установленного приложения.
- При timeout сначала изучите semantics tree и test report, а не меняйте animation duration.

Gradle сохраняет отчёты полного connected-run здесь:

- `app/build/reports/androidTests/connected/index.html`;
- `app/build/outputs/androidTest-results/connected/`.

`ActivityScenario.recreate()` доказывает Activity recreation и сохранение owner-а, но не реальное
убийство OS process. Fresh-owner restoration дополнительно проверяется через сериализованный
`SavedStateHandle` payload. Эти claims нельзя расширять до полной platform process-death E2E без
отдельного сценария.

CI компилирует test APK на каждом PR. Автоматический emulator job отслеживается отдельно в
[issue #38](https://github.com/senneco/udf-sandbox/issues/38); до его завершения локальный полный
device-run и приложенное evidence обязательны для изменений renderer/lifecycle behavior.

## Optional installed-APK smoke

Файл [`.maestro/issue-19/account-journey.yaml`](../.maestro/issue-19/account-journey.yaml) повторяет
короткий пользовательский путь по видимому тексту без sleeps и координат. Он удобен для скриншота и
ручного smoke установленного APK, но не заменяет instrumentation assertions о гонках, exact IDs,
restoration или независимом entry state.

```bash
maestro --device "$ANDROID_SERIAL" test .maestro/issue-19/account-journey.yaml
```
