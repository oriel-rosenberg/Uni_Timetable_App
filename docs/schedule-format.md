# The schedule file

The app shows whatever one JSON file describes: a semester, its weekly lessons, and the academic
year's special days. The file bundled with the app is
[`app/src/main/assets/schedule.json`](../app/src/main/assets/schedule.json); use it as the template.

## Loading a new semester

1. Make a `schedule.json` for the new semester (for example, give Claude the timetable PDF and this
   document and ask for the file).
2. Get the file onto the phone (WhatsApp to yourself, Drive, USB) and save it, e.g. to Downloads.
3. In the app go to **להשלמה → החלפת מערכת שעות** and pick the file.
4. Confirm. The app checks the file first and refuses one it can't use.

That link is the only way a file gets in: the app does not register itself to open or receive files
from other apps. No rebuild is needed. To change what a fresh install starts with, replace the bundled file and rebuild.

Ticks are stored per `term.id`. A new semester needs a new id; re-importing a corrected file with the
same `term.id` and the same lesson ids keeps the ticks.

## Format

```json
{
  "formatVersion": 1,
  "calendar": { "name": "תשפ״ז", "days": [ … ] },
  "term": { "id": "huji-5787-a", "name": "סמסטר א׳ תשפ״ז", "start": "2026-10-11", "end": "2027-01-15" },
  "courses": [ { "id": "67504", "name": "אלגוריתמים" } ],
  "lessons": [ … ]
}
```

Dates are `YYYY-MM-DD`, times are 24-hour `HH:MM`, weekdays are English names (`SUNDAY` … `SATURDAY`).
Unknown fields are ignored.

### `term`

| Field | Meaning |
|---|---|
| `id` | Any unique text. Identifies the semester for ticks. |
| `name` | Shown when confirming an import. |
| `start`, `end` | First and last day of teaching, inclusive. No lessons exist outside them. |

### `lessons`

One entry per weekly slot. A course that meets twice a week has two entries.

| Field | Meaning |
|---|---|
| `id` | Unique within the file and stable between versions of it. |
| `course` | The `id` of an entry in `courses`. |
| `type` | `lecture` (הרצאה), `recitation` (תרגול) or `other`. |
| `day` | The weekday. |
| `start`, `end` | Both, or neither. |
| `teachers` | List of names, optional. |
| `place` | Optional. |
| `from`, `until` | Optional first and last date, inclusive, for a lesson that doesn't run all term. Equal for a lesson that happens once (on that date, even a day off). The app sets these for lessons added on the phone. |

A lesson **without `start` and `end`** is one with no fixed hour, such as a recorded online lecture.
It never appears on the day grid. It appears in the pending list once per teaching week, dated `day`,
or the next teaching day of that week if `day` is a day off.

### `calendar.days`

Dates on which the weekly timetable doesn't simply repeat. It can cover the whole year; entries
outside the term do no harm.

| Field | Meaning |
|---|---|
| `from`, `to` | The date, or first and last date. `to` is optional. |
| `label` | Shown on that day. |
| `off` | `true`: no lessons. |
| `classesEndAt` | Lessons starting at or after this time are dropped. |
| `followsDay` | The day runs another weekday's timetable. |

An entry with only a label is just a note on that day.

### What the app adds

Editing or deleting a lesson for every week changes `lessons` and `courses` themselves. The rest of
what is done on the phone is kept in three more fields, which a hand-made file leaves out:

| Field | Meaning |
|---|---|
| `changes` | One entry per occurrence that differs from its lesson: `cancelled`, or any of `day`, `start`, `end`, `name`, `type`, `teachers`, `place`. |
| `lessonReminders` | Lesson id → minutes before the start, for a reminder before every occurrence. |
| `occurrenceReminders` | Occurrence key → minutes before, for one occurrence; `null` silences a repeating reminder for that one. |

An occurrence's key is `term id|lesson id|Sunday of its week`. Importing a file replaces all of this
along with the schedule.
