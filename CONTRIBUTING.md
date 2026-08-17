# Участие в разработке

`udf-sandbox` — сфокусированный Android-эксперимент о навигации как состоянии приложения. Перед предложением архитектурных изменений прочитайте [README.md](README.md) и [контекст проекта](docs/PROJECT_CONTEXT.md).

## Выбор задачи

GitHub Issues — единственный источник истины для запланированной работы и её статуса:

1. Начните с [roadmap возрождения Navigation as State](https://github.com/senneco/udf-sandbox/issues/7).
2. Выберите одну issue с явными acceptance criteria и зависимостями.
3. Не выходите за scope выбранной issue в pull request.
4. Если реализация меняет архитектурное решение или инвариант, обновите `docs/PROJECT_CONTEXT.md` в том же pull request.

Не копируйте issue checklists в документацию репозитория. Ссылайтесь на issue, чтобы статус задачи оставался актуальным в одном месте.

## Локальная настройка

Установите Android Studio, нужный Android SDK, эмулятор или устройство и JDK 17. Версия Gradle закреплена wrapper-файлами, а major-версия Java — в `.java-version`.

На macOS с Homebrew `openjdk@17`:

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
./gradlew --version
./gradlew testDebugUnitTest lintDebug assembleDebug
```

## Подход к разработке

- Перед большим rewrite предпочитайте небольшой behavior-preserving extraction.
- По возможности оставляйте navigation models, reducers и projection logic чистым Kotlin-кодом.
- Отделяйте route state от анимации и другого временного presentation state.
- Добавляйте сфокусированные тесты для изменений push, pop, replace, dismiss, restoration и adaptive projection.
- Не смешивайте обновление зависимостей или форматирование всего репозитория с изменением поведения.
- Не включайте credentials, signing material, `local.properties`, персональные данные и generated build output.

## Checklist pull request

Перед открытием pull request:

1. Укажите GitHub issue и объясните, какие acceptance criteria выполнены.
2. Выполните `./gradlew testDebugUnitTest lintDebug assembleDebug` на JDK 17.
3. Добавьте или обновите тесты для каждого изменённого state transition или projection rule.
4. Проверьте Android Back, быстрые повторные события и устаревшие animation callbacks, если это относится к изменению.
5. Проверьте Activity recreation и process restoration, если это относится к изменению.
6. Приложите скриншот или короткую запись для видимых UI-изменений и укажите конфигурацию окна.
7. Явно опишите оставшиеся trade-offs и follow-up work вместо скрытого расширения scope.
