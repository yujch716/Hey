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

    private fun planned(order: Int, kind: com.choi.nawa.data.StopKind, routeIndex: Int, p: LatLng) =
        com.choi.nawa.data.PlannedStop(order, kind, "", p, routeIndex, 0, 0)

    @Test
    fun `at a waypoint it waits 10 s, then drives off on its own without passing the next stop`() = runTest {
        val far = LatLng(37.5100, 127.03) // ~1.2 km past the cafe
        val d = DemoDrive(TestScope()).apply {
            load(
                listOf(start, middle, end, far),
                listOf(planned(1, com.choi.nawa.data.StopKind.WAYPOINT, 1, middle), planned(2, com.choi.nawa.data.StopKind.PICKUP, 3, far)),
            )
        }
        d.driveTo("카페", middle)
        while (d.isMoving) d.step(0.5) // drive to the cafe
        assertEquals(0.0, Geo.distance(middle, d.fix.value.position), 1.0)

        repeat(18) { d.step(0.5) } // 9 s later: still parked
        assertEquals(0.0, Geo.distance(middle, d.fix.value.position), 1.0)
        assertFalse(d.isMoving)

        repeat(4) { d.step(0.5) } // past 10 s: rolls on
        repeat(60) { d.step(1.0) }
        val rolled = Geo.distance(middle, d.fix.value.position)
        assertTrue("rolled $rolled m", rolled > 150 && rolled <= DEMO_ROLL_M + 1)
    }

    @Test
    fun `a pickup does not drive off by itself`() = runTest {
        val d = DemoDrive(TestScope()).apply {
            load(listOf(start, middle, end), listOf(planned(1, com.choi.nawa.data.StopKind.PICKUP, 1, middle)))
        }
        d.driveTo("1번 정류지", middle)
        repeat(200) { d.step(1.0) }
        assertEquals(0.0, Geo.distance(middle, d.fix.value.position), 1.0)
    }

    @Test
    fun `rolling on stops short of a nearby next stop`() = runTest {
        // Next stop only ~100 m after the cafe
        val close = LatLng(37.4954, 127.03)
        val d = DemoDrive(TestScope()).apply {
            load(
                listOf(start, middle, close, end),
                listOf(planned(1, com.choi.nawa.data.StopKind.WAYPOINT, 1, middle), planned(2, com.choi.nawa.data.StopKind.PICKUP, 2, close)),
            )
        }
        d.driveTo("카페", middle)
        repeat(200) { d.step(1.0) }
        assertEquals(0.0, Geo.distance(close, d.fix.value.position), 1.0)
    }

    @Test
    fun `parks where the built-in navi is, but not once a route is loaded`() = runTest {
        val navi = LatLng(37.5, 127.02)
        val d = DemoDrive(TestScope())
        d.placeAt(navi)
        assertEquals(0.0, Geo.distance(navi, d.fix.value.position), 0.5)

        d.load(listOf(start, middle, end))
        d.placeAt(navi)
        assertEquals(0.0, Geo.distance(start, d.fix.value.position), 0.5)
    }

    /** Records what the built-in navi was asked to do */
    private class RecordingNavi : com.choi.nawa.port.NaviPort {
        val calls = mutableListOf<String>()
        override val arrivals = kotlinx.coroutines.flow.MutableSharedFlow<String>()
        override suspend fun start() {}
        override fun requestRoute(name: String, position: LatLng) { calls += "route $name" }
        override fun cancelRoute() { calls += "cancel" }
        override suspend fun destination(): com.choi.nawa.port.NaviDestination? = null
        override suspend fun currentLocation(): com.choi.nawa.port.Fix? = null
        override fun release() {}
    }

    @Test
    fun `demo navi drives the demo car and mirrors destinations to the built-in navi`() = runTest {
        val d = DemoDrive(TestScope()).apply { load(listOf(start, middle, end)) }
        val builtIn = RecordingNavi()
        val navi = DemoNaviWithBuiltIn(FakeNavi(d), builtIn)

        navi.requestRoute("1번 정류지", middle)
        assertTrue(d.isMoving)
        navi.requestRoute("2번 정류지", end)
        navi.cancelRoute()

        assertFalse(d.isMoving)
        assertEquals(listOf("route 1번 정류지", "route 2번 정류지", "cancel"), builtIn.calls)
    }
}
