# לוז שיעורים

A personal Android app (Kotlin, Jetpack Compose) that shows one student's university timetable day
by day. Hebrew, right-to-left, offline. README.md has the code layout.

## Build and test

- `.\build.cmd` builds the signed `timetable.apk` into this folder. `.\build.cmd testDebugUnitTest`
  runs the tests and writes pictures of the screens to
  `C:\Dev\android-toolchain\build\app\screenshots`.
- The toolchain (JDK, Android SDK, Gradle cache) lives in `C:\Dev\android-toolchain`. Nothing
  is on PATH; `build.cmd` sets the environment.
- There is no phone or emulator on this PC. Check UI changes by reading the screenshots.
  `ScreenshotTest` renders at phone size; `LessonMenuTest` and `AddLessonDialogTest` drive the
  pop-ups. `DialogLayoutTest` checks a pop-up stays put while typing; pop-ups with text fields
  set their own width (`PopUpProperties` in `LessonDialogs.kt`), as the platform's sizing made them jump.
- The app must stay offline: no new permissions (`OfflineTest` fails otherwise) and no component
  that other apps can reach besides the launcher activity.
- Raise `versionCode` in `app/build.gradle.kts` for every APK handed to the user.
- After changing code and before handing over an APK, have the `code-reviewer` agent review the
  changed files, and deal with what it finds.
- `keystore.properties` and `keystore/` sign the APK. Without them an update cannot install over
  the app already on the phone. They must never be committed; `.gitignore` excludes them.

## Publishing a version

The code lives in the public GitHub repository `oriel-rosenberg/Uni_Timetable_App` (`git` and `gh`
are installed and signed in). Each APK handed to the user is also published there:

1. Commit the changes with a message saying what changed for the user, and push to `main`.
2. Tag and release it with the APK attached, named after `versionName`:
   `gh release create v1.4.3 timetable.apk --title "1.4.3" --notes "<what changed>"`.

Check `git status` before committing: nothing from the signing key, no APK in the tree.

## Making the schedule for a new semester

The user sends the timetable PDF from the Hebrew University's student system (orbitlive.huji.ac.il,
"מערכת שעות"). The result is a `schedule.json` in the format of `docs/schedule-format.md`, modelled
on `app/src/main/assets/schedule.json`. No rebuild is needed.

1. **Read the PDF.** It has no text layer, so render its pages to images and read those. Days run
   ראשון to שישי from right to left; a "המשך" cell continues the lesson above it.
2. **One `lessons` entry per weekly slot.** In a group code `course-1-nn` is a lecture and
   `course-2-nn` a recitation; check that against the teachers' titles and confirm with the user.
   Use the group code as the lesson id, with a weekday suffix when one group meets twice a week
   (`67504-1-01-sun`). Copy teacher and place text as printed.
3. **Courses outside the grid.** "קבוצות קורס עם מועדים מיוחדים" at the bottom lists them; ask the
   user what each one is. A recorded, watch-any-time lecture is a lesson with no `start` and `end`,
   which shows only in the pending list. The safety course (67003) is left out.
4. **`term`.** A new unique `id` (e.g. `huji-5787-b`), a name, and the first and last teaching day.
5. **`calendar.days`.** The whole academic year from
   https://academic-secretary.huji.ac.il/לוח-שנה-אקדמי : days off, early closing (`classesEndAt`),
   days that run another weekday's timetable (`followsDay`). The bundled file already has תשפ"ז;
   within the same year copy it as it is.
6. **Check.** Every calendar date falls on the weekday the university lists, no two lessons share
   an id, and `ScheduleParser.parse` accepts the file.
7. **Hand over.** Show the lessons as a table for the user to confirm, then give them the file.
   They load it on the phone with להשלמה → החלפת מערכת שעות. Importing replaces the lessons along
   with reminders and per-lesson changes; ticks are kept per `term.id`.

Leave the bundled `schedule.json` alone unless the user wants a fresh install to start on the new
semester. The tests are written against it, so replacing it means first keeping the old file as a
test fixture and pointing `bundledSchedule()` in `TestData.kt` at that.
