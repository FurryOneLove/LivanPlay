package com.shilapi.xcertplay.setup

import android.content.Context

/** Which features the setup guide offers, and how well each is proven. */
object SetupGuide {
    const val STEP_CONNECTION = 0
    const val STEP_IPHONE = 1
    const val STEP_FEATURES = 2
    const val STEP_DONE = 3
    const val STEP_COUNT = 4

    enum class Feature { AUTO_CONNECT, LOCATION }

    /** TESTED: confirmed on at least one head unit. */
    enum class Status { TESTED, EXPERIMENTAL }

    data class Entry(val feature: Feature, val status: Status, val needsAdb: Boolean)

    /** Claims follow docs/COMPATIBILITY.md: community reports, not a certified support list. */
    fun features(): List<Entry> {
        fun entry(feature: Feature, status: Status, needsAdb: Boolean = false) = Entry(feature, status, needsAdb)
        return listOf(entry(Feature.AUTO_CONNECT, Status.TESTED), entry(Feature.LOCATION, Status.TESTED))
    }

    private fun prefs(context: Context) = context.getSharedPreferences("diplay", Context.MODE_PRIVATE)

    fun seen(context: Context): Boolean = prefs(context).getBoolean("setup_guide_seen", false)

    fun markSeen(context: Context) {
        prefs(context).edit().putBoolean("setup_guide_seen", true).apply()
    }

    /** Only a first launch opens the guide by itself; anyone with a saved iPhone has already set up. */
    fun shouldOpenOnLaunch(seen: Boolean, phoneChosen: Boolean): Boolean = !seen && !phoneChosen
}
