# Evidence для issue #28

Дата проверки: 2026-08-28.

## Что является oracle

Instrumentation fixture возвращает новый `Screen` при каждом binding, как production catalog.
Каждый screen записывает свой exact `EntryId` в отдельный process-local atomic counter из
`SideEffect`. Counter не является Compose state, поэтому сам не вызывает invalidation, и считает
только успешно применённые content passes. UI semantics отдельно подтверждают, что ожидаемое
изменение действительно стало видимым.

Это строже ручного просмотра логов: assertions автоматически сравнивают parent baseline с каждым
кадром nested transition. Положительные controls меняют local state самого parent, observable
parent input и явный immutable input key binding-а. Все три обязаны увеличить counter, иначе
измеритель или заявленные update paths считались бы невалидными.

## Test-first цикл

До production-изменения результаты были такими:

| Сценарий | Parent counter | Результат |
| --- | --- | --- |
| Pure projection: заменить только nested destination | exact parent и `ChildOf(parentId)` сохранены | GREEN |
| Local state меняется внутри nested screen | `Home: 1 -> 2` | RED |
| `Accounts` заменяется на `Transactions` внутри того же parent | `Home: 1 -> 2` | RED |
| Local state меняется у самого parent | counter увеличился | GREEN positive control |

Первая реализация добавляла второй `movableContentOf` вокруг nested content. Новые cases стали
GREEN, но critical regression suite обнаружил четыре нарушения relocation/saveable ownership:
один entry host мог быть вызван дважды, а три focus/relocation сценария падали. Этот вариант был
отброшен целиком.

Принятое решение сохраняет один существующий exact-entry movable host. В пределах одной renderer
composition для неизменившихся полного `BackStackEntry`, типа `Screen` и `parentInputsKey` он
получает тот же stable content object и удерживает первый `Screen` instance; меняющийся
renderer-owned `childContent` читается в отдельном restart scope. Последующие instances с той же
identity не служат update channel. Реактивные данные parent читаются как observable Compose state,
а новый immutable input передаётся catalog-ом через обязательный internal `parentInputsKey`.
Bound content identity входит в equality renderer branch отдельно от navigation target: новый key
доходит при той же navigation revision, не подделывает navigation event и не создаёт content motion.

## Адресный device gate

Финальные regression cases:

| Сценарий | Доказательство |
| --- | --- |
| Local update nested screen | значение `Accounts` меняется `0 -> 1`, nested counter растёт, `Home` остаётся на baseline |
| Nested destination replacement | catalog действительно создаёт новый binding `Home`; outgoing и incoming одновременно видимы во время transition; затем остаётся только `Transactions`, а `Home` остаётся на baseline на каждом кадре |
| Parent positive control | значение `Home` меняется `0 -> 1`, parent counter становится больше baseline |
| Observable parent input | удержанный `Home` показывает `Input 1`, parent counter становится больше baseline |
| Explicit parent input key | при том же exact target/revision host-only refresh показывает `Explicit 1`, parent counter становится больше baseline |

Адресный package:

```bash
adb -s emulator-5580 shell am instrument -w -r \
  -e class com.shmakov.udf.composable.common.NestedRecompositionIsolationRegressionTest \
  com.shmakov.udf.test/androidx.test.runner.AndroidJUnitRunner
```

```text
NestedRecompositionIsolationRegressionTest

Time: 2.343
OK (5 tests)
```

Critical renderer suite после отказа от вложенного movable content:

```text
AnimatedNavigationRegressionTest
EntrySaveableStateRegressionTest
NestedRecompositionIsolationRegressionTest

Time: 14.474
OK (26 tests)
```

## Полные gates

Проверка выполнена с JDK 17 на отдельном `emulator-5580`, AVD `Pixel_4`, Android API 29. Общий
эмулятор другого агента не запускался, не выбирался и не изменялся.

```text
./gradlew testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest

BUILD SUCCESSFUL
```

JVM suite: 190 tests в 20 suites, `failures=0`, `errors=0`, `skipped=0`.

Канонический полный device gate:

```text
ANDROID_SERIAL=emulator-5580 ./gradlew connectedDebugAndroidTest

Starting 51 tests on Pixel_4(AVD) - 10
BUILD SUCCESSFUL
```

Android test report: 51 tests, `failures=0`, `errors=0`, `skipped=0`, test time `33.109s`.
В него входят прежние reference E2E, recreation, modal, relocation и saveable-state contracts,
поэтому новый renderer seam проверен не только адресными cases.

## Граница утверждения

Доказана изоляция committed parent content при двух конкретных nested changes. Это не утверждение
«внешний screen никогда не recomposes»: его собственный local state и observable state, который он
читает внутри `Content`, должны закономерно вызывать recomposition. Служебные renderer scopes также
могут recomposes без повторного committed pass пользовательского `Screen.Content`.

Видимый дизайн не менялся, поэтому статичный screenshot не добавляет доказательной силы. Oracle
этой задачи — exact-entry counters, UI semantics и покадровые assertions, а не Layout Inspector или
поиск строк в logcat.
