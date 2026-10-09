package com.shilapi.xcertplay

import android.app.AlertDialog
import android.os.Looper
import android.provider.Settings
import com.shilapi.xcertplay.adb.LocalAdb
import com.shilapi.xcertplay.host.R
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.CopyOnWriteArrayList
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlertDialog
import org.robolectric.shadows.ShadowSettings

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], manifest = Config.NONE, qualifiers = "en")
class UsbPermissionSetupTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private val permissions get() = UsbPermissionSetup.Permission.entries

    @Before fun reset() {
        Settings.Secure.putString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, null)
        Settings.Secure.putInt(context.contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, 0)
        ShadowSettings.setCanDrawOverlays(false)
    }

    @Test fun listedServiceWithoutGlobalAccessibilityIsNotVerified() {
        Settings.Secure.putString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            "${context.packageName}/com.shilapi.xcertplay.UsbAutoConfirmService")
        assertFalse(UsbPermissionSetup.Permission.ACCESSIBILITY.granted(context))
        Settings.Secure.putInt(context.contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, 1)
        assertTrue(UsbPermissionSetup.Permission.ACCESSIBILITY.granted(context))
    }

    @Test fun unapprovedConnectionDoesNotRunAnyGrant() {
        val client = FakeClient().apply { access = LocalAdb.Access.NOT_APPROVED }
        val result = operation(client).run()
        assertEquals(LocalAdb.Access.NOT_APPROVED, result.access)
        assertTrue(client.commands.isEmpty())
        assertFalse(result.complete)
        assertTrue(client.closed)
    }

    @Test fun transportFailureAndEmptyOutputNeverClaimSuccess() {
        for (reply in listOf(null, "", "DIPLAY_PERMISSION_EXIT:1")) {
            val client = FakeClient().apply { output = reply }
            val result = operation(client).run()
            assertEquals(permissions.toSet(), result.commandsFailed)
            assertFalse(result.complete)
        }
    }

    @Test fun successfulShellWithoutRuntimePermissionDoesNotClaimSuccess() {
        val result = operation(FakeClient()).run()
        assertTrue(result.commandsFailed.isEmpty())
        assertTrue(result.verified.values.none { it })
        assertFalse(result.complete)
    }

    @Test fun preexistingOverlayPermissionDoesNotHideOtherFailures() {
        val client = FakeClient().apply { output = null }
        val result = operation(client) { it == UsbPermissionSetup.Permission.OVERLAY }.run()
        assertTrue(result.verified.getValue(UsbPermissionSetup.Permission.OVERLAY))
        assertFalse(result.verified.getValue(UsbPermissionSetup.Permission.ACCESSIBILITY))
        assertEquals(setOf(UsbPermissionSetup.Permission.ACCESSIBILITY), result.commandsFailed)
        assertEquals(1, client.commands.size)
        assertFalse(result.complete)
    }

    @Test fun successRequiresEachRuntimeGrantAndSuccessfulCommand() {
        val allowed = mutableSetOf<UsbPermissionSetup.Permission>()
        val client = FakeClient().apply {
            onCommand = { allowed += permissions[commands.size - 1] }
        }
        val result = operation(client) { it in allowed }.run()
        assertTrue(result.complete)
        assertEquals(2, client.commands.size)
    }

    @Test fun cancelBeforeClientCreationDoesNoWork() {
        var created = false
        val operation = UsbPermissionSetup.Operation(context, { created = true; FakeClient() }) { false }
        operation.cancel()
        assertFalse(operation.run().complete)
        assertFalse(created)
    }

    @Test fun cancelDuringHandshakeStopsAllGrants() {
        val client = FakeClient()
        val operation = operation(client)
        client.onConnect = operation::cancel
        assertFalse(operation.run().complete)
        assertTrue(client.cancelled)
        assertTrue(client.commands.isEmpty())
    }

    @Test fun cancelDuringFirstGrantPreventsTheOverlayRequest() {
        val client = FakeClient()
        val operation = operation(client)
        client.onCommand = { operation.cancel() }
        val result = operation.run()
        assertEquals(1, client.commands.size)
        assertTrue(client.cancelled)
        assertTrue(result.verified.isEmpty())
        assertFalse(result.complete)
    }

    @Test fun accessibilityCommandPreservesExistingServicesAndAvoidsDuplicates() {
        val target = "${context.packageName}/com.shilapi.xcertplay.UsbAutoConfirmService"
        for (initial in listOf("other.package/.Service", "other.package/.Service:$target", "null", "")) {
            val actual = runAccessibilityCommand(initial)
            val expected = if (initial.isBlank() || initial == "null") target
                else if (initial.contains(target)) initial else "$initial:$target"
            assertEquals(expected, actual.trim())
        }
    }

    @Test fun failedOrMalformedServiceReadNeverReplacesExistingList() {
        assertEquals("UNCHANGED", runAccessibilityCommand("other.package/.Service", readFails = true).trim())
        assertEquals("UNCHANGED", runAccessibilityCommand("Permission denied").trim())
    }

    @Test fun manualCommandUsesTheSamePreservingGrantAndIncludesOverlay() {
        val command = UsbPermissionSetup.manualCommand(context.packageName)
        assertTrue(command.contains("settings get secure enabled_accessibility_services"))
        assertTrue(command.contains("SYSTEM_ALERT_WINDOW allow"))
        // Execute the exact copied command using an adb shell fixture, including its outer quoting.
        val target = "${context.packageName}/com.shilapi.xcertplay.UsbAutoConfirmService"
        val script = shellFixture("other.package/.Service") +
            "\nadb() { shift; eval \"\$1\"; }; $command; printf '%s' \"\$saved\""
        assertEquals("other.package/.Service:$target", runShell(script).trim())
    }

    @Test fun manualCommandsAreShortLiteralLinesForMissingPermissionsOnly() {
        val target = "${context.packageName}/com.shilapi.xcertplay.UsbAutoConfirmService"
        assertEquals(listOf(
            "adb shell settings put secure enabled_accessibility_services $target",
            "adb shell settings put secure accessibility_enabled 1",
            "adb shell appops set ${context.packageName} SYSTEM_ALERT_WINDOW allow",
        ), UsbPermissionSetup.manualCommands(context))

        ShadowSettings.setCanDrawOverlays(true)
        Settings.Secure.putString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            "other.package/.Service")
        assertEquals(listOf(
            "adb shell settings put secure enabled_accessibility_services other.package/.Service:$target",
            "adb shell settings put secure accessibility_enabled 1",
        ), UsbPermissionSetup.manualCommands(context))

        // Listed already, only the global switch is off: the list is repeated unchanged.
        Settings.Secure.putString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, target)
        assertEquals("adb shell settings put secure enabled_accessibility_services $target",
            UsbPermissionSetup.manualCommands(context).first())

        Settings.Secure.putInt(context.contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, 1)
        assertTrue(UsbPermissionSetup.manualCommands(context).isEmpty())
    }

    @Test fun manualCommandsKeepTheDeviceShellScriptForAListThatIsUnsafeToRetype() {
        Settings.Secure.putString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            "other.package/.Outer\$Inner")
        val command = UsbPermissionSetup.manualCommands(context).first()
        assertTrue(command.contains("settings get secure enabled_accessibility_services"))
        assertFalse(command.contains("Outer"))
    }

    @Test fun manualDialogOffersNoButtonForASettingsScreenTheFirmwareLacks() {
        val activity = Robolectric.buildActivity(DiPlayActivity::class.java).get()
        activity.setTheme(android.R.style.Theme_Material_NoActionBar)
        DiPlayActivity::class.java.getDeclaredMethod("showManualPermissionDialog", String::class.java)
            .apply { isAccessible = true }.invoke(activity, "reason")
        val texts = mutableListOf<String>()
        fun collect(view: android.view.View) {
            if (view is android.widget.TextView) texts += view.text.toString()
            if (view is android.view.ViewGroup) for (i in 0 until view.childCount) collect(view.getChildAt(i))
        }
        collect(ShadowAlertDialog.getLatestAlertDialog().window!!.decorView)
        assertFalse(context.getString(R.string.btn_open_accessibility_setting) in texts)
        assertFalse(context.getString(R.string.btn_open_overlay_setting) in texts)
        assertTrue(context.getString(R.string.center_map_no_permission_screen) in texts)
        assertTrue(UsbPermissionSetup.manualCommands(context).joinToString("\n") in texts)
    }

    @Test fun progressCancelUnblocksWorkerAndSuppressesLateDialogs() = cancelledUiAttempt {
        it.getButton(AlertDialog.BUTTON_NEGATIVE).performClick()
    }

    @Test fun activityStopCancelsWorkerAndSuppressesLateDialogs() = cancelledUiAttempt { _, activity ->
        DiPlayActivity::class.java.getDeclaredMethod("onStop").apply { isAccessible = true }.invoke(activity)
    }

    @Test fun activityDestroyCancelsWorkerAndSuppressesLateDialogs() = cancelledUiAttempt { _, activity ->
        DiPlayActivity::class.java.getDeclaredMethod("onDestroy").apply { isAccessible = true }.invoke(activity)
    }

    private fun cancelledUiAttempt(cancel: (AlertDialog) -> Unit) = cancelledUiAttempt { dialog, _ -> cancel(dialog) }

    private fun cancelledUiAttempt(cancel: (AlertDialog, DiPlayActivity) -> Unit) {
        val activity = Robolectric.buildActivity(DiPlayActivity::class.java).get()
        activity.setTheme(android.R.style.Theme_Material_NoActionBar)
        val client = FakeClient().apply { block = true }
        activity.usbPermissionOperationFactory = { operation(client) }
        DiPlayActivity::class.java.getDeclaredMethod("autoApplyPermissions").apply { isAccessible = true }.invoke(activity)
        val progress = ShadowAlertDialog.getLatestAlertDialog()
        try {
            assertTrue(client.entered.await(3, TimeUnit.SECONDS))
            cancel(progress, activity)
            shadowOf(Looper.getMainLooper()).idle()
            assertTrue(client.cancelled)
            assertFalse(progress.isShowing)
            assertTrue(client.finished.await(3, TimeUnit.SECONDS))
            shadowOf(Looper.getMainLooper()).idle()
            assertSame(progress, ShadowAlertDialog.getLatestAlertDialog())
            assertEquals(1, client.commands.size)
        } finally {
            client.cancel()
        }
    }

    private fun operation(client: FakeClient, granted: (UsbPermissionSetup.Permission) -> Boolean = { false }) =
        UsbPermissionSetup.Operation(context, { client }, granted)

    private fun shellFixture(initial: String, readFails: Boolean = false): String =
        """current='$initial'; saved=UNCHANGED;
            settings() {
                if [ "${'$'}1" = get ]; then ${if (readFails) "return 1" else "printf '%s' \"${'$'}current\""};
                elif [ "${'$'}3" = enabled_accessibility_services ]; then saved="${'$'}4"; fi;
            };
            appops() { return 0; };""".trimIndent()

    private fun runAccessibilityCommand(initial: String, readFails: Boolean = false): String = runShell(
        shellFixture(initial, readFails) + "\n( " + UsbPermissionSetup.command(
            UsbPermissionSetup.Permission.ACCESSIBILITY, context.packageName) +
            "; printf '%s' \"\$saved\" ); result=\$?; if [ \"\$result\" != 0 ]; then printf UNCHANGED; fi;"
    )

    private fun runShell(script: String): String {
        val process = ProcessBuilder("sh", "-c", script).redirectErrorStream(true).start()
        assertTrue(process.waitFor(3, TimeUnit.SECONDS))
        return process.inputStream.bufferedReader().readText()
    }

    private class FakeClient : UsbPermissionSetup.Client {
        var access = LocalAdb.Access.READY
        var output: String? = "DIPLAY_PERMISSION_EXIT:0"
        var onConnect: () -> Unit = {}
        var onCommand: (String) -> Unit = {}
        var block = false
        @Volatile var cancelled = false
        @Volatile var closed = false
        val commands = CopyOnWriteArrayList<String>()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val finished = CountDownLatch(1)
        override fun connect(): LocalAdb.Access { onConnect(); return access }
        override fun shell(command: String): String? {
            commands += command
            entered.countDown()
            onCommand(command)
            if (block) assertTrue(release.await(3, TimeUnit.SECONDS))
            return if (cancelled) null else output
        }
        override fun cancel() { cancelled = true; release.countDown() }
        override fun close() { closed = true; finished.countDown() }
    }
}
