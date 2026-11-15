package com.choi.nawa.trip

import com.choi.nawa.data.CreateStop
import com.choi.nawa.data.CreateTripRequest
import com.choi.nawa.data.CreateTripResponse
import com.choi.nawa.data.DriverChannel
import com.choi.nawa.data.EventBody
import com.choi.nawa.data.Geo
import com.choi.nawa.data.LatLng
import com.choi.nawa.data.LocationBody
import com.choi.nawa.data.MAX_LABEL_LENGTH
import com.choi.nawa.data.PICKUP_LABEL
import com.choi.nawa.data.PassengerReply
import com.choi.nawa.data.Share
import com.choi.nawa.data.StopDraft
import com.choi.nawa.data.StopKind
import com.choi.nawa.data.TripApi
import com.choi.nawa.data.VehicleProfile
import com.choi.nawa.port.Door
import com.choi.nawa.port.Fix
import com.choi.nawa.port.Ports
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.roundToInt

enum class TripPhase { PLANNED, DRIVING, ENDED }

enum class StopStatus { UPCOMING, CURRENT, ARRIVED, COMPLETED }

data class StopProgress(
    val order: Int,
    val kind: StopKind,
    val label: String,
    val position: LatLng,
    val status: StopStatus,
    /** Pickups only */
    val share: Share?,
    /** While that passenger shares their position */
    val passengerLocation: LatLng? = null,
    /** Planned driving time from the previous stop (the server's route), for the panel's later-stop ETAs */
    val legSeconds: Int = 0,
    /** The searched place's address, shown to the driver under 픽업. Stays in the car: never sent to the server. */
    val address: String = "",
)

data class ReplyLog(val stopOrder: Int, val type: String, val atMs: Long, val position: LatLng?) {
    /** What the car reads out and lists on the panel */
    val text get() = replyMessage(stopOrder, type)
}

/** Passenger quick replies, as spoken in the car. Messages only: they never change the trip. */
fun replyMessage(stopOrder: Int, type: String) = when (type) {
    "out" -> "${stopOrder}번 탑승자가 나왔대요"
    "late" -> "${stopOrder}번 탑승자가 5분 늦는대요"
    "waitHere" -> "${stopOrder}번 탑승자가 지금 있는 곳에서 기다린대요"
    else -> "${stopOrder}번 탑승자가 메시지를 보냈어요"
}

data class TripState(
    val tripId: String,
    val driverToken: String,
    val route: List<LatLng>,
    val stops: List<StopProgress>,
    val phase: TripPhase,
    val currentIndex: Int = 0,
    /** What was last sent as currentLegEtaSeconds */
    val legEtaSeconds: Int? = null,
    val lastFix: Fix? = null,
    /** From consecutive fixes: FusedLocation has no speed field */
    val speedKmh: Double = 0.0,
    val connected: Boolean = false,
    val replies: List<ReplyLog> = emptyList(),
    val lastSentAt: Long? = null,
    val error: String? = null,
) {
    val current: StopProgress? get() = stops.getOrNull(currentIndex)
    val isLastStop get() = currentIndex == stops.lastIndex
}

/**
 * Runs one pickup trip, one stop at a time:
 *  - the navi only ever gets the current stop (requestRoute), never waypoints
 *  - every 3 s: position + the navi's ETA to the current stop go to the server
 *  - arrival = the navi's arrival callback, or within 80 m of the stop → stopArrived
 *  - completion (stage 4: buttons only) → stopCompleted(by=button) → route to the next stop,
 *    or tripEnded after the last one
 * Server calls happen before local state changes, so a failed call can simply be retried.
 */
class TripController(
    private val api: TripApi,
    private val channel: DriverChannel,
    private val ports: Ports,
    private val clock: () -> Long = System::currentTimeMillis,
    /** The demo car needs the planned route (and which stops are waypoints) to drive along. No-op in real mode. */
    private val onRoutePlanned: (CreateTripResponse) -> Unit = {},
    /** How often position and ETA go to the server (shorter in demo mode, see AppContainer) */
    private val sendIntervalMs: Long = SEND_INTERVAL_MS,
) {
    private val _state = MutableStateFlow<TripState?>(null)
    val state: StateFlow<TripState?> = _state.asStateFlow()

    // Buttons, arrival detection and the tick all touch the current stop; one at a time
    private val stopLock = Mutex()

    /** Each passenger reply as it arrives, so the app can come to the front (TripService) */
    private val _replyEvents = MutableSharedFlow<ReplyLog>(extraBufferCapacity = 8)
    val replyEvents: SharedFlow<ReplyLog> = _replyEvents.asSharedFlow()

    // Speech is queued and read one at a time by the loop, so messages never talk over each other
    private val speech = Channel<String>(Channel.UNLIMITED)
    private val lastSpokenAt = mutableMapOf<Pair<Int, String>, Long>()

    // Door-based boarding: a passenger door opened, then everything closed for DOOR_CLOSED_HOLD_MS
    private var doorStopOrder: Int? = null
    private var passengerDoorOpened = false
    private var doorHold: Job? = null

    suspend fun create(vehicle: VehicleProfile, drafts: List<StopDraft>) {
        require(drafts.isNotEmpty()) { "정류지를 하나 이상 넣어 주세요" }
        val origin = ports.location.current()?.position
            ?: ports.navi.currentLocation()?.position
            ?: error("현재 위치를 알 수 없어요")
        val stops = drafts.mapIndexed { i, d ->
            CreateStop(order = i + 1, kind = d.kind, label = labelFor(d), position = d.place.position)
        }
        val res = api.createTrip(CreateTripRequest(vehicle, origin, stops))
        onRoutePlanned(res)
        _state.value = TripState(
            tripId = res.tripId,
            driverToken = res.driverToken,
            route = res.route,
            stops = res.stops.map { p ->
                StopProgress(p.order, p.kind, p.label, p.position, StopStatus.UPCOMING, res.shares.find { it.stopId == "s${p.order}" }, legSeconds = p.legSeconds, address = drafts.getOrNull(p.order - 1)?.place?.address.orEmpty())
            },
            phase = TripPhase.PLANNED,
        )
    }

    /** [출발]: route the navi to the first stop and start listening for passenger replies. */
    fun depart() {
        val s = _state.value ?: return
        if (s.phase != TripPhase.PLANNED) return
        val first = s.stops.first()
        _state.value = s.copy(phase = TripPhase.DRIVING, currentIndex = 0, stops = s.stops.withStatus(0, StopStatus.CURRENT))
        ports.navi.requestRoute(naviName(first), first.position)
        channel.connect(s.tripId, s.driverToken, channelListener)
    }

    /** The foreground service runs this while the trip is underway. Returns when the trip ends. */
    suspend fun runLoop() = coroutineScope {
        val reader = launch { for (text in speech) ports.tts.speak(text) }
        val watchers = listOf(
            launch { ports.location.fixes.collect { onFix(it) } },
            launch { ports.navi.arrivals.collect { arrive() } },
            launch { ports.vehicle.doors.collect { onDoors(it, this@coroutineScope) } },
        )
        while (isActive && _state.value?.phase == TripPhase.DRIVING) {
            sendTick()
            delay(sendIntervalMs)
        }
        watchers.forEach { it.cancel() }
        doorHold?.cancel()
        // Let the last announcements ("운행을 마쳤어요") finish before the service stops
        speech.close()
        reader.join()
    }

    /** [도착 처리], the navi's arrival callback, or the 80 m check */
    suspend fun arrive() = stopLock.withLock {
        val s = _state.value ?: return
        val stop = s.current ?: return
        if (s.phase != TripPhase.DRIVING || stop.status != StopStatus.CURRENT) return
        call { api.sendEvent(s.tripId, s.driverToken, EventBody("stopArrived", stop.order)) } ?: return
        _state.update { it?.copy(stops = it.stops.withStatus(it.currentIndex, StopStatus.ARRIVED), legEtaSeconds = 0, error = null) }
    }

    /** [탑승 완료] (pickup) / [다음으로] (waypoint): the fallback when doors or departure aren't detected */
    suspend fun complete() = stopLock.withLock { completeCurrent("button") }

    /**
     * Completes the current (arrived) stop and routes the navi to the next one, or ends the trip.
     * Returns true if it went through. Call with [stopLock] held.
     */
    private suspend fun completeCurrent(by: String): Boolean {
        val s = _state.value ?: return false
        val stop = s.current ?: return false
        if (s.phase != TripPhase.DRIVING || stop.status != StopStatus.ARRIVED) return false
        call { api.sendEvent(s.tripId, s.driverToken, EventBody("stopCompleted", stop.order, by)) } ?: return false
        val done = s.stops.withStatus(s.currentIndex, StopStatus.COMPLETED)

        if (s.isLastStop) {
            finish(s.copy(stops = done))
            return true
        }
        val next = s.currentIndex + 1
        _state.value = s.copy(currentIndex = next, stops = done.withStatus(next, StopStatus.CURRENT), legEtaSeconds = null, error = null)
        ports.navi.requestRoute(naviName(s.stops[next]), s.stops[next].position)
        return true
    }

    /** Ends the trip early (or after the last stop) */
    suspend fun end() = stopLock.withLock {
        val s = _state.value ?: return
        if (s.phase == TripPhase.ENDED) return
        finish(s)
    }

    /** Back to planning a new trip */
    fun clear() {
        if (_state.value?.phase == TripPhase.DRIVING) return
        _state.value = null
    }

    private suspend fun finish(s: TripState) {
        call { api.sendEvent(s.tripId, s.driverToken, EventBody("tripEnded")) } ?: return
        ports.navi.cancelRoute()
        channel.close()
        _state.value = s.copy(phase = TripPhase.ENDED, legEtaSeconds = null, error = null)
    }

    // ── loop pieces ──

    private suspend fun onFix(fix: Fix) {
        var arrivedNow = false
        var leftWaypoint = false
        _state.update { s ->
            s ?: return@update null
            val prev = s.lastFix
            val moved = prev?.let { Geo.distance(it.position, fix.position) } ?: 0.0
            val dt = prev?.let { (fix.timeMs - it.timeMs) / 1000.0 } ?: 0.0
            val speed = if (dt > 0) moved / dt * 3.6 else s.speedKmh
            val heading = fix.heading
                ?: if (prev != null && moved > MIN_MOVE_FOR_HEADING_M) Geo.bearing(prev.position, fix.position) else prev?.heading
            val current = s.current
            val away = current?.let { Geo.distance(fix.position, it.position) }
            arrivedNow = current?.status == StopStatus.CURRENT && away!! <= ARRIVAL_RADIUS_M
            // A waypoint is done once the car drives off: no door rule for errands
            leftWaypoint = current?.kind == StopKind.WAYPOINT && current.status == StopStatus.ARRIVED && away!! > WAYPOINT_LEAVE_RADIUS_M
            s.copy(lastFix = fix.copy(heading = heading), speedKmh = speed)
        }
        if (arrivedNow) arrive()
        if (leftWaypoint) stopLock.withLock { completeCurrent("departed") }
    }

    /**
     * Boarding by door: at a pickup the car has reached (arrived, or within 80 m), a non-driver
     * door opens and then all of them stay closed for 3 s. The driver's door never counts:
     * drivers step out and back in.
     */
    private fun onDoors(doors: Map<Door, Boolean>, scope: CoroutineScope) {
        val s = _state.value ?: return
        val stop = s.current
        if (stop == null || stop.order != doorStopOrder) {
            // New stop: forget doors opened on the way here
            doorStopOrder = stop?.order
            passengerDoorOpened = false
            doorHold?.cancel()
            doorHold = null
        }
        if (stop == null || !doorRuleApplies(s, stop)) return

        if (PASSENGER_DOORS.any { doors[it] == true }) {
            passengerDoorOpened = true
            doorHold?.cancel() // reopened before the hold ran out: start over when it closes again
            doorHold = null
        } else if (passengerDoorOpened && doorHold == null) {
            doorHold = scope.launch {
                delay(DOOR_CLOSED_HOLD_MS)
                boardByDoor(stop.order)
            }
        }
    }

    private fun doorRuleApplies(s: TripState, stop: StopProgress): Boolean {
        if (s.phase != TripPhase.DRIVING || stop.kind != StopKind.PICKUP) return false
        val near = s.lastFix?.let { Geo.distance(it.position, stop.position) <= ARRIVAL_RADIUS_M } ?: false
        return stop.status == StopStatus.ARRIVED || (stop.status == StopStatus.CURRENT && near)
    }

    private suspend fun boardByDoor(order: Int) {
        doorHold = null
        passengerDoorOpened = false
        val s = _state.value ?: return
        val stop = s.current?.takeIf { it.order == order } ?: return
        if (!doorRuleApplies(s, stop)) return
        if (stop.status == StopStatus.CURRENT) arrive() // within 80 m but not marked arrived yet
        val wasLast = _state.value?.isLastStop == true
        val done = stopLock.withLock { completeCurrent("door") }
        if (done) say(if (wasLast) "운행을 마쳤어요" else "${order}번 탑승 완료. 다음 정류지로 안내할게요")
    }

    private fun say(text: String) {
        speech.trySend(text)
    }

    private suspend fun sendTick() {
        val s = _state.value ?: return
        val fix = s.lastFix ?: return
        val stop = s.current ?: return
        val eta = if (stop.status == StopStatus.ARRIVED) 0 else naviEta(stop) ?: estimateEta(fix.position, stop.position)
        val body = LocationBody(
            lat = fix.position.lat,
            lng = fix.position.lng,
            heading = (fix.heading ?: 0.0).coerceIn(0.0, 360.0),
            currentLegEtaSeconds = eta,
            currentStopOrder = stop.order,
            ts = clock(),
        )
        call { api.sendLocation(s.tripId, s.driverToken, body) } ?: return
        _state.update { it?.copy(legEtaSeconds = eta, lastSentAt = clock(), error = null) }
    }

    /**
     * The navi's remaining time, but only if it is routed to this stop: right after requestRoute
     * it can still report the previous (or an empty) destination.
     */
    private suspend fun naviEta(stop: StopProgress): Int? {
        val dest = ports.navi.destination() ?: return null
        if (Geo.distance(dest.position, stop.position) > NAVI_MATCH_RADIUS_M) return null
        return dest.remainSeconds.coerceAtLeast(0)
    }

    private fun estimateEta(from: LatLng, to: LatLng) =
        (Geo.distance(from, to) * DETOUR_FACTOR / FALLBACK_SPEED_MPS).roundToInt()

    private suspend fun call(block: suspend () -> Unit): Unit? = try {
        block()
    } catch (e: Exception) {
        _state.update { it?.copy(error = e.message ?: e.javaClass.simpleName) }
        null
    }

    private val channelListener = object : DriverChannel.Listener {
        override fun onConnection(connected: Boolean) = _state.update { it?.copy(connected = connected) }

        /** Read it out and list it. Never touches the navi or the stops. */
        override fun onReply(reply: PassengerReply) {
            val log = ReplyLog(reply.stopOrder, reply.type, clock(), reply.position)
            _state.update { it?.copy(replies = (listOf(log) + it.replies).take(MAX_REPLIES)) }
            _replyEvents.tryEmit(log)
            // The same passenger sending the same message again within 10 s is read once
            val repeated = synchronized(lastSpokenAt) {
                val key = reply.stopOrder to reply.type
                val last = lastSpokenAt[key]
                (last != null && log.atMs - last < REPEAT_WINDOW_MS).also { if (!it) lastSpokenAt[key] = log.atMs }
            }
            if (!repeated) say(log.text)
        }

        override fun onPassengerLocation(stopOrder: Int, position: LatLng?) = _state.update { s ->
            s?.copy(stops = s.stops.map { if (it.order == stopOrder) it.copy(passengerLocation = position) else it })
        }
    }

    companion object {
        const val SEND_INTERVAL_MS = 3_000L
        const val ARRIVAL_RADIUS_M = 80.0
        /** How close the navi's destination must be to the stop for its ETA to count */
        const val NAVI_MATCH_RADIUS_M = 150.0
        const val MAX_REPLIES = 30
        /** All passenger doors must stay closed this long after one was opened */
        const val DOOR_CLOSED_HOLD_MS = 3_000L
        /** A waypoint counts as left once the car is this far from it */
        const val WAYPOINT_LEAVE_RADIUS_M = 150.0
        /** Same passenger, same message within this window: read once */
        const val REPEAT_WINDOW_MS = 10_000L
        /** Doors that mean a passenger: never the driver's (ROW_1_LEFT) */
        val PASSENGER_DOORS = setOf(Door.FRONT_PASSENGER, Door.REAR_LEFT, Door.REAR_RIGHT)
        private const val MIN_MOVE_FOR_HEADING_M = 2.0
        private const val DETOUR_FACTOR = 1.3
        private const val FALLBACK_SPEED_MPS = 25 / 3.6

        fun labelFor(d: StopDraft) =
            if (d.kind == StopKind.PICKUP) PICKUP_LABEL else d.waypointName.trim().ifEmpty { "경유지" }.take(MAX_LABEL_LENGTH)

        /** What the navi shows as the destination name. Pickups carry no name, so use the order. */
        fun naviName(stop: StopProgress) = if (stop.kind == StopKind.PICKUP) "${stop.order}번 정류지" else stop.label
    }
}

private fun List<StopProgress>.withStatus(index: Int, status: StopStatus) =
    mapIndexed { i, s -> if (i == index) s.copy(status = status) else s }
