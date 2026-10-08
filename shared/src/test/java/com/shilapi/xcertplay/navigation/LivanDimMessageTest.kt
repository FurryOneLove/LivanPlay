package com.shilapi.xcertplay.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

class LivanDimMessageTest {
    private fun guidance(type: Int, drivingSide: Int = 0, distance: Int = 120) =
        RouteGuidance(distanceMeters = distance, type = type, drivingSide = drivingSide, road = "Тверская улица")

    @Test
    fun namesTheManeuversLivanDimKnows() {
        val names = mapOf(
            1 to "LEFT", 20 to "LEFT", 2 to "RIGHT", 21 to "RIGHT",
            47 to "HARD_LEFT", 48 to "HARD_RIGHT",
            13 to "SLIGHT_LEFT", 49 to "SLIGHT_LEFT", 14 to "SLIGHT_RIGHT", 50 to "SLIGHT_RIGHT",
            22 to "EXIT_LEFT", 23 to "EXIT_RIGHT", 52 to "FORK_LEFT", 53 to "FORK_RIGHT",
            6 to "ENTER_ROUNDABOUT", 28 to "ENTER_ROUNDABOUT", 46 to "ENTER_ROUNDABOUT", 7 to "LEAVE_ROUNDABOUT",
            10 to "FINISH", 12 to "FINISH", 24 to "FINISH", 25 to "FINISH", 27 to "FINISH",
            15 to "BOARD_FERRY", 16 to "LEAVE_FERRY",
            3 to "STRAIGHT", 5 to "STRAIGHT", 0 to "STRAIGHT", 99 to "STRAIGHT",
        )
        names.forEach { (type, name) -> assertEquals("type $type", name, LivanDimMessage.maneuverName(type, 0)) }
    }

    @Test
    fun aUTurnFollowsTheDrivingSide() {
        assertEquals("UTURN_LEFT", LivanDimMessage.maneuverName(4, 0))
        assertEquals("UTURN_RIGHT", LivanDimMessage.maneuverName(4, 1))
        assertEquals("UTURN_LEFT", LivanDimMessage.maneuverName(26, 0))
    }

    @Test
    fun carriesMetersSecondsAndTheStreet() {
        val message = LivanDimMessage.from(
            guidance(type = 2).copy(remainingSeconds = 600, remainingMeters = 5400), nowEpochSeconds = 1_000,
        )
        assertEquals(LivanDimMessage("RIGHT", 120, 5400, 600, "Тверская улица"), message)
    }

    @Test
    fun anUnknownRemainingDistanceIsNotAnArrival() {
        // LivanDim may hide the route close to the finish, so zero would blank it for the whole trip.
        val message = LivanDimMessage.from(guidance(type = 1, distance = 800), nowEpochSeconds = 1_000)
        assertEquals(800, message.toFinishMeters)
        assertEquals(0, message.etaSeconds)
    }

    @Test
    fun theArrivalTimeStandsInForAMissingRemainingTime() {
        val message = LivanDimMessage.from(guidance(type = 1).copy(arrivalEpochSeconds = 1_900), nowEpochSeconds = 1_000)
        assertEquals(900, message.etaSeconds)
        val late = LivanDimMessage.from(guidance(type = 1).copy(arrivalEpochSeconds = 900), nowEpochSeconds = 1_000)
        assertEquals(0, late.etaSeconds)
    }
}
