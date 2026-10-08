package com.shilapi.xcertplay.navigation

import android.content.Context
import android.content.Intent
import android.util.Log
import com.shilapi.xcertplay.iap2.wire.Iap2Frame
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/**
 * Sends the iPhone's next maneuver to LivanDim (ru.who.livansetting), which draws it on the Livan
 * instrument cluster. LivanDim takes broadcasts addressed to its package: `DATA` with the maneuver
 * name, meters and seconds, or `live=false` when the route is gone. It removes a route that stays
 * silent for five seconds, so a live one is repeated.
 *
 * Nothing is sent while CarPlay has no route, so another navigation source keeps the cluster.
 * Every message names its `source`: LivanDim uses it to let one source hold the cluster at a time.
 */
object LivanDimNavigation {
    const val TARGET_PACKAGE = "ru.who.livansetting"
    internal const val ACTION_DATA = "ru.who.livandim.navi.DATA"
    internal const val ACTION_HELLO = "ru.who.livandim.navi.HELLO"

    private const val TAG = "LivanPlay-Dim"
    private const val TICK_MILLIS = 1_000L
    private const val REPEAT_MILLIS = 2_000L

    private val worker: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor { task ->
        Thread(task, "livandim-navigation").apply { isDaemon = true }
    }

    // Worker thread only.
    private val route = RouteGuidanceState()
    private var context: Context? = null
    private var ticker: ScheduledFuture<*>? = null
    private var shown: LivanDimMessage? = null
    private var shownAtMillis = 0L

    /** A CarPlay session is starting; safe to call again. */
    internal fun start(context: Context) {
        val app = context.applicationContext
        worker.execute {
            this.context = app
            if (ticker != null) return@execute
            send(app, Intent(ACTION_HELLO).putExtra("package", app.packageName))
            ticker = worker.scheduleWithFixedDelay(::tick, TICK_MILLIS, TICK_MILLIS, TimeUnit.MILLISECONDS)
        }
    }

    internal fun onFrame(frame: Iap2Frame) {
        if (frame.messageId != RouteGuidanceState.ROUTE_GUIDANCE_UPDATE &&
            frame.messageId != RouteGuidanceState.ROUTE_GUIDANCE_MANEUVER_UPDATE) return
        worker.execute {
            if (runCatching { route.accept(frame.messageId, frame.payload) }.getOrNull() != RouteGuidanceChange.NONE) tick()
        }
    }

    /** The session ended: take the route off the cluster. */
    internal fun end() {
        worker.execute {
            ticker?.cancel(false)
            ticker = null
            route.clear()
            tick()
        }
    }

    private fun tick() {
        val app = context ?: return
        val message = route.current()?.let { LivanDimMessage.from(it, System.currentTimeMillis() / 1000) }
        val now = android.os.SystemClock.elapsedRealtime()
        if (message == null) {
            if (shown != null) {
                shown = null
                send(app, Intent(ACTION_DATA).putExtra("live", false))
            }
            return
        }
        if (message == shown && now - shownAtMillis < REPEAT_MILLIS) return
        shown = message
        shownAtMillis = now
        send(app, Intent(ACTION_DATA)
            .putExtra("live", true)
            .putExtra("action", message.action)
            .putExtra("to_maneuver", message.toManeuverMeters)
            .putExtra("to_finish", message.toFinishMeters)
            .putExtra("eta", message.etaSeconds)
            .putExtra("street", message.street)
            // CarPlay route guidance carries no speed limit; 0 is LivanDim's "unknown".
            .putExtra("speed_limit", 0))
    }

    private fun send(context: Context, intent: Intent) {
        // Addressed to LivanDim alone, as its own navigator module does.
        runCatching { context.sendBroadcast(intent.setPackage(TARGET_PACKAGE).putExtra("source", context.packageName)) }
            .onFailure { Log.w(TAG, "broadcast to LivanDim failed", it) }
    }
}
