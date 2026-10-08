package com.shilapi.xcertplay.navigation

/**
 * One route update in LivanDim's units: the maneuver by name, meters, seconds. LivanDim maps the
 * name to an instrument-cluster arrow and shortens the street itself.
 */
internal data class LivanDimMessage(
    val action: String,
    val toManeuverMeters: Int,
    val toFinishMeters: Int,
    val etaSeconds: Int,
    val street: String,
) {
    companion object {
        fun from(guidance: RouteGuidance, nowEpochSeconds: Long): LivanDimMessage {
            val toManeuver = guidance.distanceMeters.coerceAtLeast(0)
            // LivanDim can hide the route near the finish; an unknown remaining distance must not
            // read as "arrived", so fall back to the distance that is known.
            val toFinish = guidance.remainingMeters?.coerceIn(0L, Int.MAX_VALUE.toLong())?.toInt() ?: toManeuver
            val eta = guidance.remainingSeconds
                ?: guidance.arrivalEpochSeconds?.let { it - nowEpochSeconds }
                ?: 0L
            return LivanDimMessage(
                action = maneuverName(guidance.type, guidance.drivingSide),
                toManeuverMeters = toManeuver,
                toFinishMeters = toFinish,
                etaSeconds = eta.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt(),
                street = guidance.road,
            )
        }

        /**
         * Apple's RouteGuidanceManeuverType as a name from LivanDim's maneuver table. The cluster
         * has one arrow for keeping to a side, so ramps and highway changes share it with slight
         * turns. [drivingSide] 1 is left-hand traffic, where a U-turn goes right.
         */
        fun maneuverName(appleType: Int, drivingSide: Int): String = when (appleType) {
            1, 20 -> "LEFT"
            2, 21 -> "RIGHT"
            47 -> "HARD_LEFT"
            48 -> "HARD_RIGHT"
            13, 49 -> "SLIGHT_LEFT"
            14, 50 -> "SLIGHT_RIGHT"
            22 -> "EXIT_LEFT"
            23 -> "EXIT_RIGHT"
            52 -> "FORK_LEFT"
            53 -> "FORK_RIGHT"
            4, 18, 19, 26 -> if (drivingSide == 1) "UTURN_RIGHT" else "UTURN_LEFT"
            // "Take the Nth exit" is announced before the roundabout, like entering it.
            6, in 28..46 -> "ENTER_ROUNDABOUT"
            7 -> "LEAVE_ROUNDABOUT"
            10, 12, 24, 25, 27 -> "FINISH"
            15, 17 -> "BOARD_FERRY"
            16 -> "LEAVE_FERRY"
            else -> "STRAIGHT"
        }
    }
}
