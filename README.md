# Italiano

A personal, offline Android app for learning Italian from **CEFR B1 to C1**, with remedial
**A2 refresher** units you can drop into at any time. Built for one learner (Richard), sideloaded
and updated through [Obtainium](https://github.com/ImranR98/Obtainium).

- No accounts, no ads, no tracking, **no network permission** — everything ships inside the APK.
- Kotlin · Jetpack Compose · Material 3 · Room · DataStore · minSdk 26 / target SDK 37.

## Features

- **Today** — greeting, the next lesson on your path, review cards due, your streak and progress per level.
- **Course map** — levels A2R (Ripasso), B1, B2, C1 with units that unlock in order; "coming soon" units
  are shown but closed; refresher units are always open and linked from each unit ("Need a refresher?").
- **Lesson player** — one exercise at a time with a progress bar, feedback and explanation after each answer,
  and an end-of-lesson summary. Supports every exercise type in [`docs/lesson-format.md`](docs/lesson-format.md):
  grammar notes, flashcards, multiple choice, gap fill, conjugation tables, sentence building, translation
  (both directions), match pairs, error correction, listen & transcribe, listen & choose, reading,
  speak & repeat, speak & respond, role-play and free writing with a self-check.
- **Answer checking** — case, extra spaces, final punctuation, commas, apostrophe style and a typed subject
  pronoun ("io ero") are forgiven; a missing or wrong accent counts as **almost** rather than wrong.
- **Listening & speaking** — Italian text-to-speech (`it-IT`) for listening exercises, role-play lines and a
  speaker button next to Italian text; speech recognition (`it-IT`, offline preferred) for speaking exercises.
  The microphone permission is asked the first time you tap the mic. If you decline, or the device has no
  recogniser, you can always **type instead** or **skip**.
- **Review** — spaced repetition (SM-2 with Again / Hard / Good / Easy) over unit vocabulary and lesson
  flashcards, with a daily limit for new cards.
- **Reminders** — as many as you like, each with its own time and days of the week, individually switchable,
  plus a global pause. Friendly, varied copy (a short Italian phrase plus English, never guilt-tripping).
  If you haven't opened the app for 2+ days, reminders drop to at most one a day until you come back.
  Reminders rotate between a **grammar tip with a quick challenge** (two in three; title = rule, body = tip
  and example, button *Prova la sfida* opens a one-card challenge), **vocabulary review** when cards are due,
  and the **next lesson**. Tips come from units you have started (plus refreshers you opened) and none repeats
  until the others have been shown. Each kind can be switched off in Settings. Every unit page lists its
  grammar tips with their challenges.
- **CELI 3 exam track** — exam-preparation units after C1 on the course map, always open.

## Install on your phone (Obtainium)

1. Install Obtainium on the phone.
2. **Add app** → paste `https://github.com/Gleaped/ItalianLearning` → **Add**.
3. Obtainium picks the `italiano-<version>.apk` attached to the latest GitHub Release and installs it.
   Every push to `main` produces a new release (`v0.1.<run number>`), which Obtainium offers as an update.

Releases are always signed with the same key (see *Signing*), so updates install over the previous version.

## Build locally

Prerequisites: JDK 17 or 21 and the Android SDK (Android Studio installs it; set `ANDROID_HOME` or
create `local.properties` with `sdk.dir=...`). The Gradle wrapper downloads Gradle 9.5.0 itself.

```bash
./gradlew testDebugUnitTest   # unit tests (grading, SRS, reminders, content parsing)
./gradlew lintDebug           # Android lint
./gradlew assembleDebug       # app/build/outputs/apk/debug/   (id com.gleaped.italianlearning.debug)
./gradlew assembleRelease     # app/build/outputs/apk/release/ (minified; signed if the signing env vars are set)
```

Versioning: `versionCode` = `GITHUB_RUN_NUMBER` (1 locally), `versionName` = `0.1.<run number>`.

### Toolchain

| Component | Version |
|---|---|
| Android Gradle Plugin | 9.3.1 (built-in Kotlin support) |
| Gradle | 9.5.0 |
| Kotlin (+ Compose compiler & serialization plugins) | 2.4.20 |
| KSP (Room) | 2.3.12 |
| Compose BOM | 2026.09.00 |
| Room | 2.8.5 · DataStore 1.2.0 |

All versions live in [`gradle/libs.versions.toml`](gradle/libs.versions.toml).

## CI

[`.github/workflows/android.yml`](.github/workflows/android.yml) runs on every push, pull request and manual
dispatch: `./gradlew testDebugUnitTest lintDebug assembleRelease`, then uploads the APK as a workflow
artifact. On pushes to `main` it also creates the GitHub Release that Obtainium reads.

## Signing

The release signing key is **not** in the repository. CI reads it from two repository secrets:

- `ITALIANO_KEYSTORE_BASE64`: the PKCS12 keystore, base64-encoded (`base64 -w0 italiano-sideload.jks`)
- `ITALIANO_KEYSTORE_PASSWORD`: its store/key password (alias `italiano`)

Optional overrides: `ITALIANO_KEY_ALIAS`, `ITALIANO_KEY_PASSWORD` (environment variables read by `app/build.gradle.kts`).

Without the secrets, CI still runs tests and lint and builds an **unsigned** APK artifact, but publishes
no Release. Obtainium can only update the app if every release carries the same signature, so keep a
backup of the keystore: losing it means uninstalling and reinstalling the app.

To sign locally, export `ITALIANO_KEYSTORE_PATH` and `ITALIANO_KEYSTORE_PASSWORD` before `./gradlew assembleRelease`.

## Adding content

Course content is plain JSON under `app/src/main/assets/course/` (`course.json` plus one file per unit in
`units/`). The format, including every exercise type, is documented in
[`docs/lesson-format.md`](docs/lesson-format.md). No code changes are needed: mark a unit
`"available": true` in `course.json` and add `units/<unitId>.json`. The unit test
`ContentParserTest.shippedContentParses` parses all shipped content on every CI run, so a broken file fails
the build instead of the app. Unknown exercise types are shown as "needs a newer version" and can be skipped.

## Architecture

```
app/src/main/java/com/gleaped/italianlearning/
  content/      JSON models + parser (pure Kotlin, unit-tested)
  domain/       grading, SM-2, reminder planning, streak/unlock rules (pure Kotlin, unit-tested)
  data/         Room database, DataStore settings, asset loader, LearningRepository
  reminders/    AlarmManager scheduling, notification, boot/time-change receivers
  speech/       TextToSpeech wrapper, SpeechRecognizer composable
  ui/           Compose screens: home, course, lesson (+ exercise views), review, settings, navigation
```

Reminder scheduling uses a single inexact `AlarmManager.setAndAllowWhileIdle` alarm for the next reminder
rather than WorkManager: reminders are tied to wall-clock times the user chose, which WorkManager cannot
target, and inexact alarms need no exact-alarm permission. Alarms are re-armed on app start, after each
reminder, and on boot, time and time-zone changes.

## Licence

See [LICENSE](LICENSE).
