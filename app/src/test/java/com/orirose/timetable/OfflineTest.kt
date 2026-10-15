package com.orirose.timetable

import android.Manifest
import android.content.pm.PackageManager
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * The app must stay offline. Android gives no network, Wi-Fi, Bluetooth or NFC access without the
 * matching permission, so this fails as soon as the final manifest, libraries included, asks for
 * anything beyond what lesson reminders need.
 */
@RunWith(RobolectricTestRunner::class)
class OfflineTest {

    private val neededForReminders = setOf(
        Manifest.permission.POST_NOTIFICATIONS, // show the reminder
        Manifest.permission.USE_EXACT_ALARM, // at the minute it was set for
        Manifest.permission.SCHEDULE_EXACT_ALARM, // the same, on Android 12
        Manifest.permission.RECEIVE_BOOT_COMPLETED, // and still after the phone restarts
    )

    @Test
    fun `the app asks for nothing beyond what reminders need`() {
        val app = RuntimeEnvironment.getApplication()
        val info = app.packageManager.getPackageInfo(app.packageName, PackageManager.GET_PERMISSIONS)

        // AndroidX declares one permission of the app's own, used to keep its internal broadcasts private.
        val requested = info.requestedPermissions.orEmpty().filterNot { it.startsWith(app.packageName) }

        assertEquals(emptyList<String>(), requested - neededForReminders)
    }
}
