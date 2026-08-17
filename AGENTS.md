# Правила работы с репозиторием

## Что прочитать сначала

Перед изменением навигации или state handling прочитайте:

1. [`README.md`](README.md) — вопрос, который исследует проект, и его текущее устройство.
2. [`docs/PROJECT_CONTEXT.md`](docs/PROJECT_CONTEXT.md) — устойчивый архитектурный контекст, инварианты, риски и целевое направление.
3. [Roadmap](https://github.com/senneco/udf-sandbox/issues/7) и выбранную дочернюю задачу.

GitHub Issues — единственный источник истины для scope и статуса задач. Не создавайте второй checklist в документации репозитория.

## Цель проекта

`udf-sandbox` — одномодульный Android-эксперимент. Его главный вопрос: можно ли представить навигацию как сериализуемое состояние приложения и рендерить её как детерминированную Compose-проекцию.

Банковские экраны — только demo fixtures, а не продуктовый domain. Предпочитайте изменения, которые помогают проверить navigation hypothesis, а не развивают несвязанную функциональность приложения.

## Архитектурное направление

- UI отправляет типизированные actions и не изменяет глобальный state напрямую.
- Единственный lifecycle-aware store владеет `AppState` и применяет переходы через чистый reducer.
- Navigation history использует стабильную идентичность `BackStackEntry`, отделённую от данных route.
- Чистая проекция преобразует navigation state и конфигурацию окна в root-, nested- и modal-слоты.
- Compose рендерит проекцию и сообщает о UI-only событиях, например о завершении dismiss-анимации.
- Долгоживущий navigation state не содержит временный прогресс анимации.
- Root entry остаётся валидным после любого перехода, включая быстрые повторные события.
- Navigation state и entry identity сериализуются для восстановления после process death.

Использование AndroidX Navigation не запрещено, но внутренний back stack `NavController` не должен незаметно становиться каноническим state. Любая интеграция обязана сохранить детерминированное владение state и semantics восстановления.

## Legacy baseline

Текущий прототип появился раньше целевой архитектуры:

- `UdfApp.appState` глобален и напрямую изменяется из нескольких composables;
- `AnimatedNavigation` совмещает проекцию, рендеринг, выбор анимации, modal diff и мутацию state;
- инварианты destination и root stack не зафиксированы в модели;
- restoration и поведенческие тесты отсутствуют.

Считайте это ограничениями миграции, а не примерами для нового кода. Делайте изменения поведения явно и не начинайте большой rewrite без characterization tests.

## Разработка

- Используйте JDK 17 и Gradle Wrapper из репозитория.
- Работайте из GitHub issue с явным контекстом и acceptance criteria.
- Сохраняйте изменения небольшими; не смешивайте behavior refactoring с обновлением зависимостей или форматированием.
- Предпочитайте чистый Kotlin для reducers, navigation models, projection и тестов.
- Оставляйте Android- и Compose-зависимости на границах рендеринга и lifecycle.
- Добавляйте тесты для каждого изменённого state transition, projection rule, restoration path и Back behavior.
- Для UI-изменений прикладывайте скриншот или короткую запись для затронутой конфигурации окна.
- Не коммитьте signing keys, tokens, credentials, `local.properties` и generated build output.

Перед отправкой изменений выполните:

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug
```

## Приоритеты code review

Проверяйте в таком порядке:

1. crashes и невалидный или пустой navigation stack;
2. неверные semantics push, pop, replace, dismiss или Android Back;
3. потерю или дублирование state после recreation, process death или изменения layout;
4. устаревшие asynchronous callbacks и анимации, изменяющие уже новый entry;
5. несовместимую entry identity или Compose keys;
6. отсутствие сфокусированных тестов и UI evidence.

Считайте рискованными `!!`, точные сравнения `Float`, мутацию state во время composition и частичное обновление связки Android Gradle Plugin / Gradle / Kotlin / Compose, если они затрагивают изменяемый код.
