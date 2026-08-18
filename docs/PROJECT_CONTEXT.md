# Контекст проекта: navigation as state

Этот документ — устойчивый handoff для разработчиков и агентов, которые подключаются к `udf-sandbox` без контекста прошлых обсуждений. Здесь зафиксированы исследовательская цель, baseline, ограничения и направление развития. Актуальный статус работ хранится только в GitHub Issues.

## Цель и критерии успеха

Проект исследует, можно ли представить Android-навигацию как обычное состояние приложения и рендерить её с помощью Jetpack Compose.

Эксперимент считается успешным, если:

1. navigation state является каноническим описанием истории;
2. события пользователя и системы создают новый state через явные детерминированные переходы;
3. state вместе с конфигурацией окна однозначно определяет видимое navigation tree;
4. fullscreen-, nested- и modal-destinations имеют согласованные Back semantics;
5. state сериализуется и восстанавливается после process death;
6. transitions и projection тестируются как чистый Kotlin без Compose runtime;
7. временное animation state не может повредить долгоживущий navigation state.

Ближайшая цель — не публикация navigation framework. Сначала нужно точно сформулировать гипотезу, проверить сложные случаи и понять, какие абстракции действительно полезны.

## Baseline возрождения

- репозиторий: [`senneco/udf-sandbox`](https://github.com/senneco/udf-sandbox);
- baseline commit: `578c0ca4424b2a2c517771a3c43f90cf7c8ce172`;
- один Android-модуль `app` на Kotlin и Jetpack Compose;
- начальная demo-история: `Home -> Accounts -> Account(1)`;
- unit- и instrumentation-тестов поведения на baseline нет.

Проект остаётся sandbox, а не production-приложением. Текущий код доказывает жизнеспособность идеи, но пока совмещает несколько разных обязанностей внутри Compose.

## Текущая модель

`AppState` содержит `NavState`, а `NavState` — непустой список `BackStackEntry(id, route)`. Route описывает semantic target и arguments, а entry ID — конкретное появление route в истории. Модель проверяет content-root, однозначный content/modal kind и уникальность IDs.

Открытые `ContentRoute` и `ModalRoute` позволяют приложению объявлять собственные routes. Versioned primitive snapshot отделён от route objects; приложение подключает их через `RouteCodec`, а восстановление всегда повторно использует validation `NavState`.

Navigation input представлен закрытым набором typed `NavAction`, а `NavReducer.reduce(state, action)` является чистой Kotlin-функцией. Результат — `NavReduction.Changed(state, transition)` либо `NavReduction.Unchanged(state, reason)`. Action factories материализуют identity новых entries до reduction, поэтому reducer не генерирует случайные значения.

`NavTransitionIntent` описывает только что совершившийся Push, Pop, branch replacement, modal dismiss или full-history replacement. Он возвращается отдельно от durable `NavState` и не попадает в snapshot. Временный `UdfApp` удерживает последний intent рядом с `AppState` в process-local render envelope до следующего `Changed`, а renderer использует его для выбора legacy motion. Одноразовое consumption, lifecycle-aware store и настраиваемая animation policy появятся отдельными инкрементами.

`AnimatedNavigation(navState, navTransition)` сворачивает entries и выбирает content для конкретного внутреннего render slot. `Screen.whereToShowChild` может вернуть другой слот, поэтому одна логическая история имеет разные физические layout-проекции.

Для `Home -> Accounts -> Account(1)`:

- в compact/portrait `Accounts` занимает application root, а `Account(1)` показывается как sheet;
- в expanded/landscape `Home` остаётся в application root, `Accounts` рендерится во вложенном navigator, а над ним показывается тот же sheet.

Таким образом, изменение размера или ориентации окна должно быть изменением рендеринга, а не navigation event. Логическая история остаётся прежней. Это центральная идея, которую важно сохранить.

## Термины

- **Route** — семантическое назначение и его аргументы, например `AccountDetails(accountId = 42)`.
- **Back-stack entry** — одно конкретное появление route в истории со стабильной идентичностью.
- **Navigation state** — упорядоченная логическая история entries.
- **Action** — событие пользователя, системы, lifecycle или завершения presentation-анимации, отправленное владельцу state.
- **Reduction** — результат чистого применения `NavAction`: изменённый state с transient transition intent либо тот же state с typed причиной no-op.
- **Transition intent** — эфемерное описание совершившегося navigation transition; оно не является частью `NavState` и не восстанавливается из snapshot.
- **Projection** — детерминированное преобразование navigation state и конфигурации окна в видимое размещение.
- **Navigation tree** — root content, nested slots и modal layers, которые должен отрисовать Compose.

Route отвечает на вопрос «куда», entry — «какое именно появление». Modal использует presentation UI, но его присутствие в истории остаётся state.

## Эталонные semantics

Эти правила задают общее понимание до начала миграции:

| Событие | State до | State после | Правило |
| --- | --- | --- | --- |
| Guarded Push | `Home` | `Home -> Accounts` | Новый entry добавляется, только если указанный `expectedTopId` всё ещё является top. |
| `NavigateFrom` / замена ветки | `Home -> Accounts -> Account(1)` | `Home -> Transactions` | Для остающегося `Home` все descendants удаляются, затем добавляется новый child. Если source уже top, это guarded push. |
| Android Back / Pop | `Home -> Accounts -> Account(1)` | `Home -> Accounts` | Удаляется ровно верхний entry. Root не удаляется. |
| Exact modal dismiss | `Home -> Accounts -> Account(1)` | `Home -> Accounts` | Reducer удаляет только указанный modal entry по identity; повторный или stale dismiss является no-op. |
| `ReplaceHistory` | любая history | валидная target history | Logout и deep link заменяют state одной атомарной action, без наблюдаемых промежуточных Push. Сохранённый ID обязан по-прежнему обозначать тот же semantic route. |
| Изменение layout | `Home -> Accounts` | `Home -> Accounts` | Меняется только projection, navigation state остаётся прежним. |

Эталонные проекции:

| Navigation state | Single-pane | Multi-pane |
| --- | --- | --- |
| `Home` | `Home` | `Home` |
| `Home -> Accounts` | `Accounts` в root | `Home` в root, `Accounts` во вложенном слоте |
| `Home -> Accounts -> Account(1)` | `Accounts` и account sheet | `Home`, вложенный `Accounts` и тот же sheet |
| `Home -> Accounts -> Account(1) -> AccountDetails(1)` | `AccountDetails` в root | `AccountDetails` в root; предыдущая multi-pane projection сохраняется только как outgoing UI до конца exit-анимации |

Последовательный Back из details сначала возвращает projection с account sheet, затем закрывает sheet, затем возвращает `Home`.

## Что уже показал прототип

Можно считать подтверждёнными следующие наблюдения:

- navigation history может храниться в application state;
- route equality можно отделить от стабильной identity каждого появления route;
- структурные инварианты и snapshot round trip можно тестировать как чистый Kotlin;
- typed navigation actions и reducer transitions можно тестировать без Android и Compose;
- stale actions и защищённый root дают явный `Unchanged`, не исключение и не скрытый переход;
- content и modal destinations могут участвовать в единой последовательности Back;
- responsive placement можно вычислять без переписывания логической истории;
- branch replacement полезен, когда родитель остаётся видимым и выбирает другого ребёнка;
- удалённый modal entry иногда нужно временно удерживать в presentation state до завершения exit-анимации.

Reducer contract покрывает эталонные state transitions и подключён к одной временной runtime boundary, но lifecycle-aware store, чистая projection, renderer races, lifecycle restoration и сложные анимации ещё не доказаны end-to-end.

## Текущий data flow

Чистое navigation core уже имеет однонаправленную границу:

```text
NavState + NavAction
    -> NavReducer
    -> NavReduction.Changed(newState, NavTransitionIntent)
     | NavReduction.Unchanged(sameState, reason)
```

В demo runtime все navigation mutations уже проходят через одну boundary:

```text
UI callback -> UdfApp.dispatchNavigation(NavAction)
            -> NavReducer
            -> AppState + transient NavTransitionIntent
            -> Compose rendering
```

`appState` доступен UI только для чтения; `MainActivity`, demo composables и modal callback больше не собирают history вручную. Эта boundary всё ещё process-global и не lifecycle-aware; effect boundary, чистая projection и настраиваемая reducer-to-renderer animation policy пока отсутствуют.

## Известные риски и незавершённая работа

### Владение state

- `UdfApp` всё ещё владеет глобальным для процесса Compose frame, хотя `appState` снаружи доступен только для чтения.
- UI и renderer отправляют typed actions в `dispatchNavigation`, но эта временная boundary не привязана к lifecycle конкретной Activity.
- обычная Activity recreation может случайно сохранить state, но после process death восстанавливается hard-coded начальная история.
- несколько Activity или task instances разделяли бы один navigation state.

### Модель и идентичность

- `NavState` уже отделяет semantic route от entry identity и отклоняет empty stack, non-content root, неоднозначный route kind, blank или duplicate IDs.
- ID создаётся action factory на application boundary и сохраняется как строка; `NavReducer` не генерирует случайность внутри reduction.
- primitive snapshot и codec существуют, но ещё не подключены к `SavedStateHandle` и process-death lifecycle.
- `NavTransitionIntent` вынесен из durable state и snapshot; renderer пока сводит его к жёстко заданным legacy motions без отдельной policy.
- `ReplaceHistory` разрешает пересечение old/target IDs только для тех же routes; для новых deeplink- и logout-occurrences следует создавать свежие IDs.

### Проекция и рендеринг

- projection logic находится внутри composable и читает orientation из окружения.
- registry destinations реализован ручными `when` и завершается небезопасным `!!`.
- объявленные `Transaction` и `Card` не имеют зарегистрированного content screen.
- outgoing `AnimatedContent` может получить nested tail, рассчитанный для incoming state, и визуально «прыгнуть» во время exit.
- screen может запросить nested placement, но забыть отрендерить переданный nested state.

### Modal lifecycle

- предыдущие modal entries хранятся в `AtomicReference`, который изменяется во время composition.
- modal merge теряет элементы при пакетном добавлении и может упасть при перестановке.
- отложенный dismiss callback dispatch-ит durable action только после завершения анимации; request и completion пока не разделены.
- завершение bottom sheet определяется точным сравнением `Float` offset с высотой контейнера.

### Back и гонки событий

- `NavState` и `NavReducer` защищают root независимо от устаревшего состояния UI callback.
- pure reducer детерминированно останавливает быстрые Pop на root и возвращает typed no-op для stale IDs.
- demo callbacks dispatch-ят actions через одну process-global boundary; lifecycle и конкурентное владение будут формализованы единым store.

### Надёжность и поддержка

- model, identity, snapshot и reducer покрыты pure Kotlin contract tests, но projection-, lifecycle- и UI navigation tests ещё отсутствуют;
- Android/Compose toolchain отражает исходный прототип и должен обновляться только после появления safety net;
- demo UI смешивает Material 2 и Material 3.

## Целевая архитектура

Целевой цикл:

```text
UI/System event
    -> AppAction
    -> state owner
    -> NavReducer.reduce(previous NavState, NavAction)
    -> NavReduction
    -> new AppState + transient NavTransitionIntent
    -> project(NavState, WindowConfiguration)
    -> NavigationTree(root, nested slots, modals)
    -> Compose rendering
```

Асинхронная работа и завершение анимаций возвращают явные actions. Они не получают право произвольно изменять любой будущий state.

Предполагаемые границы модели:

```kotlin
data class BackStackEntry(
    val id: EntryId,
    val route: Route,
)

data class NavState(
    val entries: NonEmptyList<BackStackEntry>,
)

object NavReducer {
    fun reduce(
        state: NavState,
        action: NavAction,
    ): NavReduction
}

fun project(
    navState: NavState,
    window: WindowConfiguration,
): NavigationTree
```

Route, entry identity, validated `NavState`, primitive snapshot и pure navigation reducer уже реализуют deterministic core этой схемы. Временная runtime boundary передаёт transition intent renderer-у; lifecycle-aware state owner, чистая projection и настраиваемая animation policy ещё не реализованы.

## Обязательные инварианты

Будущая реализация должна явно обеспечивать и тестировать следующие правила:

1. Пока application task активен, stack не бывает пустым.
2. Root всегда является renderable content entry.
3. У каждого entry стабильная identity, не зависящая от route arguments.
4. `Pop` не удаляет защищённый root.
5. Durable modal dismiss адресует конкретный entry ID и является idempotent; animation completion меняет только presentation state.
6. Каждый route имеет exhaustive renderer или явный unsupported-state result.
7. Для одинаковых state и window input projection одинакова.
8. Projection не изменяет application state.
9. Activity recreation и process restoration воспроизводят ту же логическую историю.
10. Local UI state сохраняется или осознанно удаляется по entry identity.

## Решения и ограничения

- Сохранять проект небольшим и single-module, пока новая граница не докажет свою пользу.
- Предпочитать чистый Kotlin для state, reducer и projection.
- Не смешивать миграцию поведения с массовым обновлением зависимостей или форматированием.
- Сначала зафиксировать ожидаемое поведение, затем заменять renderer.
- Передавать в projection явную layout policy; конкретный Android API выбирается отдельно, а orientation остаётся только временным demo-упрощением.
- Не сохранять animation progress в navigation state.
- Не сохранять `NavTransitionIntent` в navigation state или snapshot; `Unchanged` не создаёт transition.
- Logout и deep link заменяют полную валидную history атомарным `ReplaceHistory`.
- AndroidX Navigation допустим как implementation detail, если application state остаётся каноническим.
- Каждая behavior-changing issue определяет наблюдаемые acceptance criteria и сфокусированные тесты.
- GitHub Issues — единственный live task tracker; документация описывает фазы и решения, но не дублирует статус.

## Открытые вопросы дизайна

Это темы для отдельных issues и экспериментов, а не уже принятые решения:

- Должна ли каноническая навигация остаться линейной историей или стать явным деревом?
- Удаляется ли modal entry в начале dismiss или после завершения exit-анимации?
- Как Compose renderer будет сопоставлять transient `NavTransitionIntent` с настраиваемой animation policy?
- Как normalise deep link в валидную navigation history?
- Как привязать saveable UI state к entry при перемещении projection между слотами?
- Когда действительно понадобятся multiple back stacks?
- Как predictive Back интегрируется с reducer-owned navigation state?
- Может ли Navigation Compose помочь с платформенной интеграцией, не становясь владельцем канонической истории?

## Не-цели фазы возрождения

- реальный data layer для accounts, transactions или cards;
- выделение reusable library до стабилизации application model;
- поддержка всех navigation patterns сразу;
- внедрение большого architecture framework только ради терминологии UDF;
- обновление всего toolchain в одном изменении с navigation behavior.

## Roadmap

Работа разделена на контракт, deterministic state core, корректный Compose renderer и доказательство lifecycle durability. Обновление toolchain ведётся отдельным maintenance-track. Точный порядок, зависимости и актуальный статус находятся только в [GitHub roadmap](https://github.com/senneco/udf-sandbox/issues/7).

## Как продолжить работу

Новому разработчику или агенту:

1. Прочитать `README.md`, этот документ и `AGENTS.md`.
2. Выбрать одну GitHub issue и сохранить заявленный scope.
3. Проверить `git status` перед редактированием и не перезаписывать чужую работу.
4. Добавить или обновить сфокусированные тесты до изменения state или projection behavior.
5. Делать commits и pull requests достаточно небольшими, чтобы каждый объяснял один архитектурный шаг.
6. Сообщать точные результаты проверки и прикладывать UI evidence при изменении рендеринга.

## Важные файлы

- `app/src/main/java/com/shmakov/udf/AppState.kt`
- `app/src/main/java/com/shmakov/udf/UdfApp.kt`
- `app/src/main/java/com/shmakov/udf/MainActivity.kt`
- `app/src/main/java/com/shmakov/udf/navigation/Route.kt`
- `app/src/main/java/com/shmakov/udf/navigation/NavState.kt`
- `app/src/main/java/com/shmakov/udf/navigation/NavStateSnapshot.kt`
- `app/src/main/java/com/shmakov/udf/navigation/NavigationReducer.kt`
- `app/src/main/java/com/shmakov/udf/navigation/Screen.kt`
- `app/src/main/java/com/shmakov/udf/composable/common/AnimatedNavigation.kt`
- `app/src/main/java/com/shmakov/udf/composable/common/BottomSheetLayout.kt`
- `app/src/main/java/com/shmakov/udf/composable/content/HomeScreenContent.kt`
- `app/src/main/java/com/shmakov/udf/composable/content/AccountBottomSheetContent.kt`

## Baseline проверки

Ожидаемая команда проверки репозитория:

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug
```

На baseline нет unit- или instrumentation-test sources. До успешного свежего запуска команды на JDK 17 baseline нельзя считать полностью проверенным.
