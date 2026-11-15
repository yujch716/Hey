package com.choi.nawa.trip

import com.choi.nawa.data.CreateStop
import com.choi.nawa.data.CreateTripRequest
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
import com.choi.nawa.port.Fix
import com.choi.nawa.port.Ports
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
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
)

data class ReplyLog(val stopOrder: Int, val type: String, val atMs: Long, val position: LatLng?)

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
    /** The demo car needs the planned route to drive along */
    private val onRoutePlanned: (List<LatLng>) -> Unit = {},
) {
    private val _state = MutableStateFlow<TripState?>(null)
    val state: StateFlow<TripState?> = _state.asStateFlow()

    // Buttons, arrival detection and the tick all touch the current stop; one at a time
    private val stopLock = Mutex()

    suspend fun create(vehicle: VehicleProfile, drafts: List<StopDraft>) {
        require(drafts.isNotEmpty()) { "정류지를 하나 이상 넣어 주세요" }
        val origin = ports.location.current()?.position
            ?: ports.navi.currentLocation()?.position
            ?: error("현재 위치를 알 수 없어요")
        val stops = drafts.mapIndexed { i, d ->
            CreateStop(order = i + 1, kind = d.kind, label = labelFor(d), position = d.place.position)
        }
        val res = api.createTrip(CreateTripRequest(vehicle, origin, stops))
        onRoutePlanned(res.route)
        _state.value = TripState(
            tripId = res.tripId,
            driverToken = res.driverToken,
            route = res.route,
            stops = res.stops.map { p ->
                StopProgress(p.order, p.kind, p.label, p.position, StopStatus.UPCOMING, res.shares.find { it.stopId == "s${p.order}" })
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
        val fixes = launch { ports.location.fixes.collect { onFix(it) } }
        val arrivals = launch { ports.navi.arrivals.collect { arrive() } }
        while (isActive && _state.value?.phase == TripPhase.DRIVING) {
            sendTick()
            delay(SEND_INTERVAL_MS)
        }
        fixes.cancel()
        arrivals.cancel()
    }

    /** [도착 처리], the navi's arrival callback, or the 80 m check */
    suspend fun arrive() = stopLock.withLock {
        val s = _state.value ?: return
        val stop = s.current ?: return
        if (s.phase != TripPhase.DRIVING || stop.status != StopStatus.CURRENT) return
        call { api.sendEvent(s.tripId, s.driverToken, EventBody("stopArrived", stop.order)) } ?: return
        _state.update { it?.copy(stops = it.stops.withStatus(it.currentIndex, StopStatus.ARRIVED), legEtaSeconds = 0, error = null) }
    }

    /** [탑승 완료] (pickup) / [다음으로] (waypoint) */
    suspend fun complete() = stopLock.withLock {
        val s = _state.value ?: return
        val stop = s.current ?: return
        if (s.phase != TripPhase.DRIVING || stop.status != StopStatus.ARRIVED) return
        call { api.sendEvent(s.tripId, s.driverToken, EventBody("stopCompleted", stop.order, "button")) } ?: return
        val done = s.stops.withStatus(s.currentIndex, StopStatus.COMPLETED)

        if (s.isLastStop) {
            finish(s.copy(stops = done))
            return
        }
        val next = s.currentIndex + 1
        _state.value = s.copy(currentIndex = next, stops = done.withStatus(next, StopStatus.CURRENT), legEtaSeconds = null, error = null)
        ports.navi.requestRoute(naviName(s.stops[next]), s.stops[next].position)
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
        _state.update { s ->
            s ?: return@update null
            val prev = s.lastFix
            val moved = prev?.let { Geo.distance(it.position, fix.position) } ?: 0.0
            val dt = prev?.let { (fix.timeMs - it.timeMs) / 1000.0 } ?: 0.0
            val speed = if (dt > 0) moved / dt * 3.6 else s.speedKmh
            val heading = fix.heading
                ?: if (prev != null && moved > MIN_MOVE_FOR_HEADING_M) Geo.bearing(prev.position, fix.position) else prev?.heading
            val current = s.current
            arrivedNow = current?.status == StopStatus.CURRENT && Geo.distance(fix.position, current.position) <= ARRIVAL_RADIUS_M
            s.copy(lastFix = fix.copy(heading = heading), speedKmh = speed)
        }
        if (arrivedNow) arrive()
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

        override fun onReply(reply: PassengerReply) = _state.update {
            it?.copy(replies = (listOf(ReplyLog(reply.stopOrder, reply.type, clock(), reply.position)) + it.replies).take(MAX_REPLIES))
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
