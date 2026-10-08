package com.shilapi.xcertplay.platform

import android.os.Build

/** What differs on the ECARX IHU601/IHU602 head units (Livan, Android 9) this app targets. */
object VehiclePlatform {
    /** The in-app updater downloads upstream DiPlay releases, which cannot update this package. */
    const val UPSTREAM_UPDATES = false

    private val ECARX_IHU = Regex("""IHU60[12]""", RegexOption.IGNORE_CASE)

    fun isEcarxIhu(product: String?, device: String?, fingerprint: String?): Boolean =
        listOf(product, device, fingerprint).any { it != null && ECARX_IHU.containsMatchIn(it) }

    /** IHU601/IHU602: no system VPN dialog, Bluetooth owned by the Goodocom daemon. */
    val isEcarxIhu: Boolean by lazy { isEcarxIhu(Build.PRODUCT, Build.DEVICE, Build.FINGERPRINT) }

    /**
     * Wi-Fi Direct must not be offered here. On IHU602G firmware (build 42) an iPhone joining the
     * group as an ordinary Wi-Fi client makes WifiP2pServiceImpl's GroupCreatedState write to a
     * null WifiP2pDevice, which kills system_server and restarts the whole head unit UI.
     */
    val WIFI_DIRECT_CRASHES_SYSTEM: Boolean get() = isEcarxIhu
}
