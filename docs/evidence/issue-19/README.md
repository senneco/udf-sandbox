# Evidence для issue #19

Дата проверки: 2026-08-28.

## Конфигурация

- отдельный `emulator-5580`;
- AVD `Pixel_4`, Android API 29;
- debug variant `com.shmakov.udf`;
- test package `com.shmakov.udf.test`;
- JDK 17;
- общий эмулятор другого агента не запускался и не выбирался.

## Test-first цикл

Сначала был добавлен black-box `ReferenceNavigationE2ETest`. Первая компиляция была RED, потому
что требуемый stable scrim test tag ещё отсутствовал. После минимального seam test APK собрался.
Первый runtime-run дал 4/5: scrim tag терялся в `clearAndSetSemantics`. Tag был перенесён внутрь
того же очищающего semantic block, после чего адресный scrim test и весь E2E package стали GREEN.

Production navigation behavior для прохождения тестов не менялся. Scrim получил только internal
test tag без accessibility click action; существующие четыре координатных scrim selectors были
заменены semantic selector-ом.

## Compile-time gate

```text
./gradlew testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest

BUILD SUCCESSFUL
```

JVM suite: 189 tests в 20 suites, `failures=0`, `errors=0`, `skipped=0`.

## Reference E2E package

Адресный запуск `com.shmakov.udf.e2e`:

```text
Time: 4.576

OK (5 tests)
```

Проверены полный UI forward/back journey, portrait ↔ landscape reprojection, stacked sheets с
двумя Back в одном UI turn, scrim dismissal и настоящий swipe dismissal. E2E не читает
`AppViewModel`, entry IDs, revision или presentation tokens.

## Полный instrumentation suite

Прямой runner:

```text
Time: 30.53

OK (46 tests)
```

Каноническая документированная команда также проверена:

```text
ANDROID_SERIAL=emulator-5580 ./gradlew connectedDebugAndroidTest

Starting 46 tests on Pixel_4(AVD) - 10
BUILD SUCCESSFUL in 37s
```

Сгенерированы HTML report, XML, per-test logcat и device info в путях из
[`docs/DEVICE_TESTING.md`](../../DEVICE_TESTING.md).

## Installed-APK smoke

Repository-owned [Maestro flow](../../../.maestro/issue-19/account-journey.yaml) прошёл все 19
команд по отчёту runner-а (18 action/assertion steps в YAML) без sleeps и координат: начальная
history была размотана до Home, затем UI прошёл
`Home → Accounts → Account(1) → details → Back → sheet → Accounts → Home`.

Дополнительный flow (13 команд по отчёту runner-а, 12 steps в YAML) снова дошёл до details для
screenshot checkpoint:

![Account details reached through the full UI journey](reference-account-details.png)

Screenshot показывает только видимый installed-APK checkpoint. Correctness reducer, exact entry
identity, recreation и races доказывают соответствующие JVM/instrumentation assertions, а не кадр.

## Граница CI

Основной CI теперь компилирует instrumentation APK через `assembleDebugAndroidTest`. Обязательный
emulator job сознательно не симулируется compile-only зеленью и вынесен в явную follow-up
[issue #38](https://github.com/senneco/udf-sandbox/issues/38).
