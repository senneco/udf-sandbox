# Issue #18: account deep-link evidence

Проверка фиксирует полный путь `udf-sandbox://accounts/42/details`: внешний URI чисто нормализуется в валидную history `Home -> Accounts -> Account(42) -> AccountDetails(42)`, затем применяется одной reducer action через тот же state owner на cold и warm Android-входах.

## Окружение

Device-проверка выполнена на отдельном `emulator-5580`, AVD `Pixel_4`, API 29. Общий эмулятор не запускался, не выбирался и не изменялся. Maestro обнаружил только `emulator-5580` и не подключённый Chromium target.

## JDK 17 quality gate

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest
```

Результат: `BUILD SUCCESSFUL`. JVM suite содержит 189 тестов в 20 suites, без failures, errors или skips.

## Instrumentation gate

Полный suite выполнен адресно на `emulator-5580`:

```text
Time: 28.746

OK (41 tests)
```

Три новых platform regression test проверяют:

- разрешение canonical VIEW/BROWSABLE URI в `MainActivity` и `singleTop` из установленного manifest;
- cold hydration через `AppViewModel`/reducer: revision `1`, `HistoryReplaced`, точные routes и Back-цепочку;
- Activity recreation без повторного применения launch intent и без замены entry IDs;
- warm platform delivery через `onNewIntent` в тот же Activity и ViewModel, новый полный state и обновлённый `activity.intent`.

## Production flow

Maestro успешно выполнил 13-командный production flow: открыл canonical URI в остановленном приложении, проверил leaf screen, прошёл системным Back через account sheet и Accounts до Home. Затем отдельный подтверждённый flow снова открыл ссылку и сохранил кадр исходной deep-link projection.

[![Account details, открытый из полного hydrated state](account-details-deep-link.png)](account-details-deep-link.png)

Статичный кадр доказывает только итоговую production UI projection. Полную history, единственную reducer revision, exact entry IDs, cold/warm parity, recreation и Back semantics доказывают JVM- и instrumentation-контракты.
