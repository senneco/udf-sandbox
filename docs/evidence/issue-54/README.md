# Evidence for issue #54

Проверено 2 сентября 2026 года на отдельном self-owned AVD `Pixel_4`, API 29,
serial `emulator-5582`. Подключённый `emulator-5554` принадлежал другому агенту и не использовался.

## Quality gate

```bash
export JAVA_HOME="$HOME/.gradle/jdks/eclipse_adoptium-17-aarch64-os_x.2/jdk-17.0.17+10/Contents/Home"
./gradlew testDebugUnitTest --rerun-tasks lintDebug assembleDebug assembleDebugAndroidTest
```

Runtime: Temurin `17.0.17`. Результат: `BUILD SUCCESSFUL`; 285 JVM tests в 32 suites,
0 failures/errors/skips, lint,
debug APK и androidTest APK.

При двух подключённых эмуляторах APK устанавливались и runner запускался только адресно:

```bash
adb -s emulator-5582 install -r app/build/outputs/apk/debug/app-debug.apk
adb -s emulator-5582 install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb -s emulator-5582 shell am instrument -w -r \
  com.shmakov.udf.test/androidx.test.runner.AndroidJUnitRunner
```

Финальный результат после reinstall собранных JDK 17 APK: `OK (66 tests)`, 41.275 s.

## Что доказано

- `MainActivity` рендерит ViewModel-owned tab graph и переводит system Back в exact
  revision-guarded leaf/select/finish policy.
- Cold/warm deep links создают один `OpenTab`, сохраняют inactive tab и не replay-ятся после
  Activity recreation.
- Настоящий UI-путь Accounts → Cards → Accounts сохраняет обе независимые histories, exact IDs и
  entry-local `rememberSaveable` значения.
- Скопированный через `Bundle`/`Parcel` payload создаёт fresh owner с тем же полным graph и
  process-local `revision = 0`, `transition = null`.
- Whole reset публикуется одним `ReplaceGraph` frame.

Recomposition проверяется не logcat-сообщениями, а test-only счётчиками успешно применённых
Compose passes: `SideEffect` находится непосредственно в content scope и адресуется exact
`EntryId`. `NestedRecompositionIsolationRegressionTest` доказывает, что local nested update и
nested destination replacement не увеличивают parent counter. Tab-level
`nestedLocalChangeDoesNotCommitShellTabBarStableParentOrInactiveTab` дополнительно удерживает
неизменными shell, tab bar, stable parent и inactive tab. Positive controls изменяют parent/tab
inputs и подтверждают, что probe действительно замечает ожидаемую recomposition.

## UI smoke

Maestro выполнил семантический путь без координат и sleeps: увеличил локальное значение Accounts,
перешёл в Cards, увеличил независимое значение Cards и вернулся в Accounts с прежним значением.

- [Accounts before switch](accounts-portrait.png)
- [Cards independent history/state](cards-portrait.png)
- [Accounts after A → B → A](accounts-return-portrait.png)
- [The same Accounts state after landscape reprojection](accounts-return-landscape.png)

## Граница доказательства

`ActivityScenario.recreate()` доказывает recreation с тем же ViewModel owner. Отдельный
`Bundle`/`Parcel` round trip доказывает restoration wiring в fresh owner. Это не реальный Android
OS process-kill E2E и не доказательство поддержки всех API levels.
