package com.choi.nawa.port.fake

import com.choi.nawa.data.Geo
import com.choi.nawa.data.LatLng
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DemoDriveTest {
    // ~1 km straight north, with a stop half way
    private val start = LatLng(37.49, 127.03)
    private val middle = LatLng(37.4945, 127.03)
    private val end = LatLng(37.499, 127.03)

    private fun drive() = DemoDrive(TestScope()).apply { load(listOf(start, middle, end)) }

    @Test
    fun `drives at 25 km per hour toward the requested stop`() {
        val d = drive()
        d.driveTo("카페", middle)
        d.step(10.0)

        assertEquals(DEMO_SPEED_MPS * 10, Geo.distance(start, d.fix.value.position), 1.0)
        assertEquals(0.0, d.fix.value.heading!!, 1.0) // heading north
        assertTrue(d.isMoving)
    }

    @Test
    fun `5x covers five times the distance`() {
        val d = drive().apply { speedMultiplier = 5 }
        d.driveTo("카페", middle)
        d.step(10.0)

        assertEquals(DEMO_SPEED_MPS * 50, Geo.distance(start, d.fix.value.position), 1.0)
    }

    @Test
    fun `stops at the stop and reports the arrival`() = runTest {
        val d = drive()
        d.driveTo("카페", middle)
        val arrival = backgroundScope.async(start = CoroutineStart.UNDISPATCHED) { withTimeout(1_000) { d.arrivals.first() } }
        repeat(200) { d.step(1.0) }

        assertEquals("카페", arrival.await())
        assertEquals(0.0, Geo.distance(middle, d.fix.value.position), 1.0)
        assertFalse(d.isMoving)
        assertEquals(0.0, d.remainingMeters!!, 0.1)
    }

    @Test
    fun `does not move without a destination`() {
        val d = drive()
        d.step(60.0)
        assertEquals(start, d.fix.value.position)
    }
}

