# לוז שיעורים

A personal Android app that opens on today's university classes, lets each lesson be ticked off once
attended or watched, and lists everything still pending, oldest first. Hebrew, right-to-left.

A long press on a lesson offers a reminder, editing and deleting, each for that one occurrence or
for every week.

It is offline by construction: its only permissions are the ones reminders need (notifications,
exact alarms, restart after boot), so it has no network, Bluetooth or NFC access; other apps cannot
hand it data; there is no cloud backup. `OfflineTest` fails if any other permission ever appears in
the final manifest.

## Build

```
build.cmd
```

produces `timetable.apk` in this folder. It expects the portable toolchain (JDK, Android SDK) in
`C:\Dev\android-toolchain`; set `TIMETABLE_TOOLCHAIN` to use another location. Other Gradle
tasks can be passed through, e.g. `build.cmd testDebugUnitTest`, which also renders screenshots of the
screens into the toolchain's `build\app\screenshots`.

`keystore.properties` and `keystore/` hold the signing key. Keep them: an APK signed with a different
key cannot be installed over the existing app without losing its data.

## Updating the app on the phone

Each new version is sent to the phone as `timetable.apk` and installed by hand. It installs over the
old one and keeps its ticks, because it is signed with the same key. Every version is also kept as a
release, with its APK, in the public GitHub repository `oriel-rosenberg/Uni_Timetable_App`.

**Optional, not set up: installing over Wi-Fi from this PC.** On the phone, turn on Developer
options → Wireless debugging and pair it with this PC once. After that,
`C:\Dev\android-toolchain\sdk\platform-tools\adb.exe install -r timetable.apk` installs a new
version directly. The catch, and why it isn't set up: a paired PC can do far more than install apps
(read data, run commands), so wireless debugging should be on only while updating and the pairing
revoked when no longer wanted.

Other ways considered, also not set up:
- The Obtainium app on the phone, updating from the GitHub releases.
- A OneDrive folder the APK is dropped into, installed from the phone.
- Google Play internal testing (needs a paid developer account).

## Changing the schedule

See [docs/schedule-format.md](docs/schedule-format.md). A new semester is a new JSON file opened on the
phone; no rebuild.

## Layout of the code

| Where | What |
|---|---|
| `data/model` | The schedule file's shape |
| `data` | Reading, validating and storing the schedule; storing ticks |
| `domain` | Which lessons fall on which dates; what is pending; edits; when reminders fall; grid geometry. Plain Kotlin, unit-tested |
| `reminders` | The alarm and the notification |
| `ui/day` | The day view: day strip, pager, zoomable hour grid, a lesson's menu and pop-ups |
| `ui/backlog` | The pending list |
| `ui/importing` | Replacing the schedule from a file |
