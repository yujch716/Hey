package com.choi.nawa.trip

import com.choi.nawa.data.CreateTripRequest
import com.choi.nawa.data.CreateTripResponse
import com.choi.nawa.data.DriverChannel
import com.choi.nawa.data.EventBody
import com.choi.nawa.data.LatLng
import com.choi.nawa.data.LocationBody
import com.choi.nawa.data.PassengerReply
import com.choi.nawa.data.Place
import com.choi.nawa.data.PlannedStop
import com.choi.nawa.data.Share
import com.choi.nawa.data.StopDraft
import com.choi.nawa.data.StopKind
import com.choi.nawa.data.TripApi
import com.choi.nawa.data.BodyType
import com.choi.nawa.data.VehicleProfile
import com.choi.nawa.port.Door
import com.choi.nawa.port.Fix
import com.choi.nawa.port.Gear
import com.choi.nawa.port.LocationPort
import com.choi.nawa.port.NaviDestination
import com.choi.nawa.port.NaviPort
import com.choi.nawa.port.Ports
import com.choi.nawa.port.TtsPort
import com.choi.nawa.port.VehiclePort
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// 출발 → 1 카페(경유) → 2 픽업 → 3 픽업, roughly 1 km apart going north
private val ORIGIN = LatLng(37.4900, 127.0300)
private val CAFE = LatLng(37.4990, 127.0300)
private val PICKUP_2 = LatLng(37.5080, 127.0300)
private val PICKUP_3 = LatLng(37.5170, 127.0300)

/** ~10 m north of [p] */
private fun near(p: LatLng, metersNorth: Double = 10.0) = LatLng(p.lat + metersNorth / 111_000, p.lng)

private val VEHICLE = VehicleProfile("아반떼", "흰색", "#f4f5f7", BodyType.SEDAN, "123가 4567")

@OptIn(ExperimentalCoroutinesApi::class)
class TripControllerTest {
    private class RecordingApi : TripApi {
        var created: CreateTripRequest? = null
        val events = mutableListOf<EventBody>()
        val locations = mutableListOf<LocationBody>()
        var failNext = false

        override suspend fun searchPlaces(query: String, near: LatLng?) = emptyList<Place>()

        override suspend fun createTrip(request: CreateTripRequest): CreateTripResponse {
            created = request
            return CreateTripResponse(
                tripId = "trip-1",
                driverToken = "driver-token",
                shares = listOf(Share("s2", "tok2", "http://web/t/tok2"), Share("s3", "tok3", "http://web/t/tok3")),
                route = listOf(ORIGIN, CAFE, PICKUP_2, PICKUP_3),
                stops = request.stops.mapIndexed { i, s -> PlannedStop(s.order, s.kind, s.label, s.position, i + 1, 120, 120 * (i + 1)) },
            )
        }

        override suspend fun sendLocation(tripId: String, driverToken: String, body: LocationBody) {
            maybeFail()
            locations += body
        }

        override suspend fun sendEvent(tripId: String, driverToken: String, body: EventBody) {
            maybeFail()
            events += body
        }

        private fun maybeFail() {
            if (failNext) {
                failNext = false
                throw java.io.IOException("server down")
            }
        }
    }

    private class RecordingChannel : DriverChannel {
        var listener: DriverChannel.Listener? = null
        var closed = false
        override fun connect(tripId: String, driverToken: String, listener: DriverChannel.Listener) {
            this.listener = listener
        }

        override fun close() {
            closed = true
        }
    }

    private class TestNavi : NaviPort {
        override val arrivals = MutableSharedFlow<String>(extraBufferCapacity = 4)
        val routed = mutableListOf<Pair<String, LatLng>>()
        var cancelled = false
        var destination: NaviDestination? = null

        override suspend fun start() = Unit
        override fun requestRoute(name: String, position: LatLng) {
            routed += name to position
        }

        override fun cancelRoute() {
            cancelled = true
        }

        override suspend fun destination() = destination
        override suspend fun currentLocation(): Fix? = null
        override fun release() = Unit
    }

    private class TestLocation : LocationPort {
        override val fixes = MutableSharedFlow<Fix>(extraBufferCapacity = 16)
        override suspend fun start() = Unit
        override suspend fun current() = Fix(ORIGIN, null, 0)
        override fun stop() = Unit
    }

    private class TestVehicle : VehiclePort {
        override val gear = MutableStateFlow(Gear.PARK)
        override val doors = MutableStateFlow(Door.entries.associateWith { false })
        override suspend fun start() = Unit
        override fun release() = Unit
        fun door(d: Door, open: Boolean) {
            doors.value = doors.value + (d to open)
        }
    }

    /** Records what was said; each sentence takes 2 s, like real speech */
    private class RecordingTts : TtsPort {
        val spoken = mutableListOf<String>()
        override suspend fun start() = Unit
        override suspend fun speak(text: String) {
            spoken += text
            kotlinx.coroutines.delay(2_000)
        }

        override fun release() = Unit
    }

    private val api = RecordingApi()
    private val channel = RecordingChannel()
    private val navi = TestNavi()
    private val location = TestLocation()
    private val vehicle = TestVehicle()
    private val tts = RecordingTts()

    private fun TestScope.controller() = TripController(api, channel, Ports(navi, location, vehicle, tts), clock = { currentTime })

    private val drafts = listOf(
        StopDraft(1, Place("스타벅스 강남", "주소1", CAFE.lat, CAFE.lng), StopKind.WAYPOINT, "카페"),
        StopDraft(2, Place("강남역 11번 출구", "주소2", PICKUP_2.lat, PICKUP_2.lng), StopKind.PICKUP, "무시되는 이름"),
        StopDraft(3, Place("교보타워", "주소3", PICKUP_3.lat, PICKUP_3.lng), StopKind.PICKUP, ""),
    )

    /** Created, departed, loop running. Fixes and arrivals flow from here on. */
    private suspend fun TestScope.underway(): TripController {
        val c = controller()
        c.create(VEHICLE, drafts)
        c.depart()
        backgroundScope.launch { c.runLoop() }
        runCurrent()
        return c
    }

    private suspend fun TestScope.fix(p: LatLng, heading: Double? = null) {
        location.fixes.emit(Fix(p, heading, currentTime))
        runCurrent()
    }

    @Test
    fun `create sends the car position and neutral labels, and keeps the share links`() = runTest {
        val c = controller()
        c.create(VEHICLE, drafts)

        val req = api.created!!
        assertEquals(ORIGIN, req.origin)
        assertEquals(listOf("카페", "픽업", "픽업"), req.stops.map { it.label }) // pickups never carry a name
        assertEquals(listOf(1, 2, 3), req.stops.map { it.order })
        val s = c.state.value!!
        assertEquals(TripPhase.PLANNED, s.phase)
        assertEquals(listOf(null, "tok2", "tok3"), s.stops.map { it.share?.token })
        assertEquals("http://web/send/tok2", s.stops[1].share!!.sendUrl)
    }

    @Test
    fun `depart routes the navi to the first stop only and listens for replies`() = runTest {
        val c = controller()
        c.create(VEHICLE, drafts)
        c.depart()

        assertEquals(listOf("카페" to CAFE), navi.routed)
        assertEquals(StopStatus.CURRENT, c.state.value!!.stops[0].status)
        assertTrue(channel.listener != null)
    }

    @Test
    fun `every 3 s sends the position with the navi ETA to the current stop`() = runTest {
        underway()
        navi.destination = NaviDestination("카페", CAFE, remainSeconds = 420, remainMeters = 1000)
        fix(near(ORIGIN))
        advanceTimeBy(3_001)

        // The tick at 0 s had no fix yet, so the first send is at 3 s
        assertEquals(1, api.locations.size)
        val last = api.locations.last()
        assertEquals(420, last.currentLegEtaSeconds)
        assertEquals(1, last.currentStopOrder)

        advanceTimeBy(3_000)
        assertEquals(2, api.locations.size)
    }

    @Test
    fun `ignores the navi ETA while the navi is still routed elsewhere and estimates instead`() = runTest {
        underway()
        navi.destination = NaviDestination("이전 목적지", PICKUP_3, remainSeconds = 60, remainMeters = 300)
        fix(near(ORIGIN))
        advanceTimeBy(3_001)

        val eta = api.locations.last().currentLegEtaSeconds
        // ~1 km × 1.3 at 25 km/h ≈ 187 s, certainly not the navi's 60
        assertTrue("eta was $eta", eta in 150..220)
    }

    @Test
    fun `within 80 m of the stop counts as arrived, once`() = runTest {
        val c = underway()
        fix(near(CAFE, 120.0))
        assertTrue(api.events.isEmpty())

        fix(near(CAFE, 50.0))
        fix(near(CAFE, 20.0))

        assertEquals(listOf(EventBody("stopArrived", 1)), api.events)
        assertEquals(StopStatus.ARRIVED, c.state.value!!.current!!.status)
    }

    @Test
    fun `the navi arrival callback also counts as arrived`() = runTest {
        val c = underway()
        navi.arrivals.emit("카페")
        runCurrent()

        assertEquals(listOf(EventBody("stopArrived", 1)), api.events)
        assertEquals(StopStatus.ARRIVED, c.state.value!!.current!!.status)
    }

    @Test
    fun `an arrived stop reports ETA 0`() = runTest {
        val c = underway()
        fix(near(CAFE, 10.0))
        advanceTimeBy(3_001)

        assertEquals(StopStatus.ARRIVED, c.state.value!!.current!!.status)
        assertEquals(0, api.locations.last().currentLegEtaSeconds)
    }

    @Test
    fun `completing a stop moves on and routes the navi to the next one`() = runTest {
        val c = underway()
        c.arrive()
        c.complete()

        assertEquals(EventBody("stopCompleted", 1, "button"), api.events.last())
        val s = c.state.value!!
        assertEquals(1, s.currentIndex)
        assertEquals(listOf(StopStatus.COMPLETED, StopStatus.CURRENT, StopStatus.UPCOMING), s.stops.map { it.status })
        assertEquals("2번 정류지" to PICKUP_2, navi.routed.last()) // a pickup's navi name is its order, not a person
    }

    @Test
    fun `completing before arriving does nothing`() = runTest {
        val c = underway()
        c.complete()

        assertTrue(api.events.isEmpty())
        assertEquals(0, c.state.value!!.currentIndex)
    }

    @Test
    fun `after the last stop the trip ends, the navi is cancelled and the loop stops`() = runTest {
        val c = controller()
        c.create(VEHICLE, drafts)
        c.depart()
        val loop = backgroundScope.launch { c.runLoop() }
        runCurrent()

        repeat(3) {
            c.arrive()
            c.complete()
        }
        advanceTimeBy(3_001)

        assertEquals(EventBody("tripEnded"), api.events.last())
        assertEquals(TripPhase.ENDED, c.state.value!!.phase)
        assertTrue(navi.cancelled)
        assertTrue(channel.closed)
        assertTrue(loop.isCompleted)
    }

    @Test
    fun `a failed server call leaves the stop as it was so the button can be pressed again`() = runTest {
        val c = underway()
        api.failNext = true
        c.arrive()

        assertEquals(StopStatus.CURRENT, c.state.value!!.current!!.status)
        assertEquals("server down", c.state.value!!.error)

        c.arrive()
        assertEquals(StopStatus.ARRIVED, c.state.value!!.current!!.status)
        assertNull(c.state.value!!.error)
    }

    @Test
    fun `speed and heading come from consecutive fixes`() = runTest {
        val c = underway()
        fix(ORIGIN)
        advanceTimeBy(1_000)
        fix(near(ORIGIN, 10.0)) // 10 m north in 1 s

        val s = c.state.value!!
        assertEquals(36.0, s.speedKmh, 1.0)
        assertEquals(0.0, s.lastFix!!.heading!!, 1.0)
    }

    @Test
    fun `passenger replies and shared positions show up in the state`() = runTest {
        val c = underway()
        channel.listener!!.onReply(PassengerReply(2, "late"))
        channel.listener!!.onPassengerLocation(2, PICKUP_2)

        val s = c.state.value!!
        assertEquals("late", s.replies.first().type)
        assertEquals(PICKUP_2, s.stops[1].passengerLocation)
    }

    // ── 6단계: messages, door boarding, waypoint departure ──

    /** Underway with the car standing at stop 2 (the first pickup), arrived */
    private suspend fun TestScope.atPickup2(): TripController {
        val c = underway()
        c.arrive()
        c.complete() // cafe done by button
        fix(near(PICKUP_2, 10.0))
        assertEquals(StopStatus.ARRIVED, c.state.value!!.current!!.status)
        api.events.clear()
        navi.routed.clear()
        return c
    }

    private fun TestScope.door(d: Door, open: Boolean) {
        vehicle.door(d, open)
        runCurrent()
    }

    @Test
    fun `a passenger door opened and closed for 3 s boards the pickup and routes on`() = runTest {
        val c = atPickup2()
        door(Door.REAR_RIGHT, true)
        door(Door.REAR_RIGHT, false)
        advanceTimeBy(2_900)
        assertTrue(api.events.isEmpty())

        advanceTimeBy(200)
        assertEquals(listOf(EventBody("stopCompleted", 2, "door")), api.events)
        assertEquals(3, c.state.value!!.current!!.order)
        assertEquals("3번 정류지" to PICKUP_3, navi.routed.last())
        advanceTimeBy(100)
        assertEquals("2번 탑승 완료. 다음 정류지로 안내할게요", tts.spoken.last())
    }

    @Test
    fun `the driver's door is ignored`() = runTest {
        val c = atPickup2()
        door(Door.DRIVER, true)
        door(Door.DRIVER, false)
        advanceTimeBy(10_000)

        assertTrue(api.events.none { it.type == "stopCompleted" })
        assertEquals(2, c.state.value!!.current!!.order)
    }

    @Test
    fun `doors far from the pickup are ignored`() = runTest {
        val c = underway()
        c.arrive()
        c.complete() // heading to stop 2, still ~1 km away
        fix(near(CAFE, 10.0))
        api.events.clear()
        door(Door.FRONT_PASSENGER, true)
        door(Door.FRONT_PASSENGER, false)
        advanceTimeBy(10_000)

        assertTrue(api.events.none { it.type == "stopCompleted" })
        assertEquals(StopStatus.CURRENT, c.state.value!!.current!!.status)
    }

    @Test
    fun `reopening before 3 s starts the wait over`() = runTest {
        val c = atPickup2()
        door(Door.FRONT_PASSENGER, true)
        door(Door.FRONT_PASSENGER, false)
        advanceTimeBy(2_000)
        door(Door.REAR_LEFT, true) // someone else gets in
        advanceTimeBy(1_500)
        assertTrue(api.events.isEmpty())

        door(Door.REAR_LEFT, false)
        advanceTimeBy(2_900)
        assertTrue(api.events.isEmpty())
        advanceTimeBy(200)
        assertEquals(EventBody("stopCompleted", 2, "door"), api.events.single())
        assertEquals(3, c.state.value!!.current!!.order)
    }

    @Test
    fun `a door closed within 80 m before the arrival event also arrives and boards`() = runTest {
        val c = underway()
        c.arrive()
        c.complete()
        // Within 80 m of stop 2 but the car hasn't been marked arrived (e.g. the arrival call failed)
        api.failNext = true
        fix(near(PICKUP_2, 40.0))
        assertEquals(StopStatus.CURRENT, c.state.value!!.current!!.status)
        api.events.clear()

        door(Door.FRONT_PASSENGER, true)
        door(Door.FRONT_PASSENGER, false)
        advanceTimeBy(3_100)
        assertEquals(listOf(EventBody("stopArrived", 2), EventBody("stopCompleted", 2, "door")), api.events)
    }

    @Test
    fun `a waypoint is not completed by doors, but by driving 150 m away`() = runTest {
        val c = underway()
        fix(near(CAFE, 10.0)) // arrived at the cafe
        api.events.clear()
        door(Door.FRONT_PASSENGER, true)
        door(Door.FRONT_PASSENGER, false)
        advanceTimeBy(5_000)
        assertTrue(api.events.isEmpty())

        fix(near(CAFE, 120.0))
        assertTrue(api.events.isEmpty())
        fix(near(CAFE, 170.0))
        assertEquals(listOf(EventBody("stopCompleted", 1, "departed")), api.events)
        assertEquals(2, c.state.value!!.current!!.order)
    }

    @Test
    fun `boarding the last pickup by door ends the trip and says so`() = runTest {
        val c = atPickup2()
        door(Door.REAR_RIGHT, true)
        door(Door.REAR_RIGHT, false)
        advanceTimeBy(3_100)
        fix(near(PICKUP_3, 10.0))
        door(Door.REAR_RIGHT, true)
        door(Door.REAR_RIGHT, false)
        advanceTimeBy(3_100)
        advanceTimeBy(10_000)

        assertEquals(TripPhase.ENDED, c.state.value!!.phase)
        assertEquals("운행을 마쳤어요", tts.spoken.last())
    }

    @Test
    fun `replies are read one after another, and only spoken, never routed`() = runTest {
        val c = underway()
        val routedBefore = navi.routed.toList()
        channel.listener!!.onReply(PassengerReply(2, "out"))
        channel.listener!!.onReply(PassengerReply(3, "late"))
        channel.listener!!.onReply(PassengerReply(2, "waitHere", PICKUP_2))
        runCurrent()
        assertEquals(listOf("2번 탑승자가 나왔대요"), tts.spoken) // the next waits for this one to finish

        advanceTimeBy(4_100)
        assertEquals(
            listOf("2번 탑승자가 나왔대요", "3번 탑승자가 5분 늦는대요", "2번 탑승자가 지금 있는 곳에서 기다린대요"),
            tts.spoken,
        )
        assertEquals(routedBefore, navi.routed)
        assertEquals(StopStatus.CURRENT, c.state.value!!.current!!.status)
        assertEquals(3, c.state.value!!.replies.size)
    }

    @Test
    fun `the same message from the same passenger within 10 s is read once`() = runTest {
        underway()
        channel.listener!!.onReply(PassengerReply(2, "out"))
        advanceTimeBy(5_000)
        channel.listener!!.onReply(PassengerReply(2, "out"))
        channel.listener!!.onReply(PassengerReply(3, "out")) // different passenger: read
        advanceTimeBy(5_000)
        assertEquals(listOf("2번 탑승자가 나왔대요", "3번 탑승자가 나왔대요"), tts.spoken)

        advanceTimeBy(6_000) // 11 s after the first
        channel.listener!!.onReply(PassengerReply(2, "out"))
        runCurrent()
        assertEquals(3, tts.spoken.size)
    }
}
