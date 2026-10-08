package com.shilapi.xcertplay

import android.content.Context
import android.util.Log
import com.shilapi.xcertplay.adb.AdbKeys
import com.shilapi.xcertplay.adb.LocalAdb

/**
 * ECARX IHU601/IHU602 firmware ships no system VPN dialog, so the wired link's local VPN can only
 * be approved from the shell. Network ADB on the head unit lets the app do that itself.
 */
internal object VpnConsentViaAdb {
    fun command(packageName: String) = "appops set $packageName ACTIVATE_VPN allow"

    fun grant(context: Context, adb: LocalAdb = LocalAdb(AdbKeys.load(context))): Boolean = runCatching {
        adb.use {
            val access = it.connect(mayAsk = true)
            Log.i("DiPlay-ADB", "vpn consent connection: $access")
            if (access != LocalAdb.Access.READY) return@use false
            it.shell(command(context.packageName)) != null
        }
    }.onFailure { Log.w("DiPlay-ADB", "vpn consent grant failed", it) }.getOrDefault(false)
}
