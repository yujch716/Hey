package com.choi.nawa.ui

import com.choi.nawa.data.LatLng
import com.choi.nawa.data.StopKind
import com.choi.nawa.trip.StopProgress
import com.choi.nawa.trip.StopStatus
import com.choi.nawa.trip.TripPhase
import com.choi.nawa.trip.TripState
import org.junit.Assert.assertEquals
import org.junit.Test

class StopEtasTest {
    private fun stop(order: Int, status: StopStatus, leg: Int) =
        StopProgress(order, StopKind.PICKUP, "픽업", LatLng(37.5, 127.0), status, null, legSeconds = leg)

    private fun state(vararg stops: StopProgress, current: Int, legEta: Int?) =
        TripState("t", "d", emptyList(), stops.toList(), TripPhase.DRIVING, currentIndex = current, legEtaSeconds = legEta)

    @Test
    fun `later stops add their planned leg and a minute at each stop before`() {
        val s = state(
            stop(1, StopStatus.COMPLETED, 300),
            stop(2, StopStatus.CURRENT, 400),
            stop(3, StopStatus.UPCOMING, 200),
            stop(4, StopStatus.UPCOMING, 100),
            current = 1,
            legEta = 180,
        )
        assertEquals(mapOf(2 to 180, 3 to 180 + 60 + 200, 4 to 180 + 60 + 200 + 60 + 100), stopEtas(s))
    }

    @Test
    fun `arrived counts from zero, and nothing is shown before the navi gives an ETA`() {
        val arrived = state(stop(1, StopStatus.ARRIVED, 300), stop(2, StopStatus.UPCOMING, 200), current = 0, legEta = 999)
        assertEquals(mapOf(1 to 0, 2 to 260), stopEtas(arrived))
        val noEta = state(stop(1, StopStatus.CURRENT, 300), stop(2, StopStatus.UPCOMING, 200), current = 0, legEta = null)
        assertEquals(emptyMap<Int, Int>(), stopEtas(noEta))
    }

    @Test
    fun `eta text rounds up to whole minutes, at least one`() {
        assertEquals("약 1분", etaText(0))
        assertEquals("약 3분", etaText(121))
    }
}
