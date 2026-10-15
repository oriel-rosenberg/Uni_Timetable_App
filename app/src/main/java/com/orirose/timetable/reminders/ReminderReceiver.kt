package com.orirose.timetable.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.orirose.timetable.TimetableApplication
import kotlinx.coroutines.launch

/**
 * Wakes the app for reminders: the reminder and refresh alarms, a reminder's snooze button, and
 * the phone restarting, the app being updated or the clock changing, all of which discard or
 * invalidate the alarms. Not exported, so only the system and the app itself can reach it.
 */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val container = (context.applicationContext as TimetableApplication).container
        val snoozedKey = intent.data?.takeIf { intent.action == ACTION_SNOOZE }?.schemeSpecificPart
        val pending = goAsync()
        container.scope.launch {
            try {
                if (snoozedKey != null) container.snoozeReminder(snoozedKey) else container.syncReminders()
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_SNOOZE = "com.orirose.timetable.SNOOZE"
    }
}
