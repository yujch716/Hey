package com.choi.nawa.port.fake

import android.util.Log
import com.choi.nawa.data.Geo
import com.choi.nawa.data.LatLng
import com.choi.nawa.data.PlannedStop
import com.choi.nawa.data.StopKind
import com.choi.nawa.port.Door
import com.choi.nawa.port.Fix
import com.choi.nawa.port.Gear
import com.choi.nawa.port.LocationPort
import com.choi.nawa.port.NaviDestination
import com.choi.nawa.port.NaviPort
import com.choi.nawa.port.TtsPort
import com.choi.nawa.port.VehiclePort
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.min
import kotlin.math.roundToInt

/** Where the demo car waits before a trip: same spot as the web demo trip's start (강남). */
val DEMO_ORIGIN = LatLng(37.4905, 127.012)

/** 25 km/h, the speed the server's straight-line fallback assumes */
const val DEMO_SPEED_MPS = 25 / 3.6

/** Demo only: how long the car waits at a waypoint before driving off on its own */
const val DEMO_WAYPOINT_WAIT_S = 10.0

/** How far it rolls on after that wait: well past the 150 m "left the waypoint" check, never past the next stop */
const val DEMO_ROLL_M = 300.0

/**
 * A simulated car for the demo mode. It follows the route the server planned and drives
 * toward whatever stop the (fake) navi was asked to route to, then stops and reports arrival.
 * [step] is public so tests can drive it without real time.
 */
class DemoDrive(private val scope: CoroutineScope) {
    @Volatile var speedMultiplier: Int = 1

    private var route: List<LatLng> = listOf(DEMO_ORIGIN)
    private var cum: List<Double> = listOf(0.0)
    private var meters = 0.0
    private var target: Double? = null
    private var targetName = ""
    private var arrived = false
    private var job: Job? = null

    // Waypoints: after arriving, wait DEMO_WAYPOINT_WAIT_S then roll on, like a driver leaving the cafe
    private var waypointMeters: List<Double> = emptyList()
    private var stopMeters: List<Double> = emptyList()
    private var waitLeft: Double? = null
    private var rolling = false

    private val _fix = MutableStateFlow(Fix(DEMO_ORIGIN, null, System.currentTimeMillis()))
    val fix: StateFlow<Fix> = _fix.asStateFlow()

    private val _arrivals = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val arrivals: SharedFlow<String> = _arrivals

    val isMoving get() = target != null && !arrived

    /**
     * Put the parked car at [p] before a trip is planned, e.g. where the built-in navi thinks the car is,
     * so the server's route and the navi's route start from the same point. Ignored once a route is loaded.
     */
    fun placeAt(p: LatLng) {
        if (route.size > 1) return
        route = listOf(p)
        cum = listOf(0.0)
        meters = 0.0
        publish()
    }

    /** Remaining metres to the current destination, null when there is none */
    val remainingMeters get() = target?.let { (it - meters).coerceAtLeast(0.0) }

    /** [stops] tell the car which stops are waypoints (it drives off from those by itself) */
    fun load(route: List<LatLng>, stops: List<PlannedStop> = emptyList()) {
        if (route.size < 2) return
        this.route = route
        cum = Geo.cumulative(route)
        stopMeters = stops.map { cum[it.routeIndex.coerceIn(0, cum.lastIndex)] }.sorted()
        waypointMeters = stops.filter { it.kind == StopKind.WAYPOINT }.map { cum[it.routeIndex.coerceIn(0, cum.lastIndex)] }
        meters = 0.0
        target = null
        arrived = false
        waitLeft = null
        rolling = false
        publish()
    }

    fun driveTo(name: String, position: LatLng) {
        // Stops sit on the route vertex closest to them; never drive backwards
        target = maxOf(cum[Geo.nearestIndex(route, position)], meters)
        targetName = name
        arrived = false
        waitLeft = null
        rolling = false
    }

    fun cancel() {
        target = null
        arrived = false
        waitLeft = null
        rolling = false
    }

    /** [seconds] is real time: the waypoint wait is real seconds; driving is sped up by [speedMultiplier] */
    fun step(seconds: Double) {
        waitLeft?.let { left ->
            if (left > seconds) {
                waitLeft = left - seconds
            } else {
                waitLeft = null
                rollOn()
            }
            return
        }
        val t = target ?: return
        if (arrived) return
        meters = min(t, meters + DEMO_SPEED_MPS * speedMultiplier * seconds)
        publish()
        if (meters >= t) {
            arrived = true
            if (rolling) {
                rolling = false // just stopped after rolling on; not a navi destination
                return
            }
            _arrivals.tryEmit(targetName)
            if (waypointMeters.any { kotlin.math.abs(it - t) < 1.0 }) waitLeft = DEMO_WAYPOINT_WAIT_S
        }
    }

    private fun rollOn() {
        val nextStop = stopMeters.firstOrNull { it > meters + 1.0 } ?: cum.last()
        target = min(meters + DEMO_ROLL_M, nextStop)
        arrived = false
        rolling = true
    }

    fun start() {
        if (job != null) return
        job = scope.launch {
            while (isActive) {
                delay(TICK_MS)
                step(TICK_MS / 1000.0)
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    private fun publish() {
        val (p, heading) = Geo.pointAlong(route, cum, meters)
        _fix.value = Fix(p, heading, System.currentTimeMillis())
    }

    private companion object {
        const val TICK_MS = 200L
    }
}

/** ETA from the remaining distance at 25 km/h; reports arrival when the demo car reaches the stop. */
class FakeNavi(private val drive: DemoDrive) : NaviPort {
    override val arrivals = drive.arrivals
    private var name = ""
    private var position: LatLng? = null

    override suspend fun start() = drive.start()

    override fun requestRoute(name: String, position: LatLng) {
        this.name = name
        this.position = position
        drive.driveTo(name, position)
    }

    override fun cancelRoute() {
        position = null
        drive.cancel()
    }

    override suspend fun destination(): NaviDestination? {
        val p = position ?: return null
        val meters = drive.remainingMeters ?: return null
        return NaviDestination(name, p, (meters / DEMO_SPEED_MPS).roundToInt(), meters.roundToInt())
    }

    override suspend fun currentLocation(): Fix = drive.fix.value
    override fun release() = drive.stop()
}

/**
 * Demo mode with the built-in navi on screen: the simulated car still decides position, ETA and arrival,
 * and every destination change (and the cancel at the end) is also sent to the real navi so a demo video shows it.
 * The real navi's own arrival callbacks are ignored: its car does not move in the emulator.
 */
class DemoNaviWithBuiltIn(private val fake: FakeNavi, private val builtIn: NaviPort) : NaviPort {
    override val arrivals = fake.arrivals

    override suspend fun start() {
        fake.start()
        builtIn.start()
    }

    override fun requestRoute(name: String, position: LatLng) {
        fake.requestRoute(name, position)
        runCatching { builtIn.requestRoute(name, position) }.onFailure { Log.w(TAG, "built-in navi requestRoute failed", it) }
    }

    override fun cancelRoute() {
        fake.cancelRoute()
        runCatching { builtIn.cancelRoute() }.onFailure { Log.w(TAG, "built-in navi cancelRoute failed", it) }
    }

    override suspend fun destination() = fake.destination()
    override suspend fun currentLocation() = fake.currentLocation()

    /** Where the built-in navi thinks the car is, or null if it doesn't answer */
    suspend fun builtInLocation(): Fix? = builtIn.currentLocation()

    override fun release() {
        fake.release()
        runCatching { builtIn.release() }
    }

    private companion object {
        const val TAG = "NAWA_PORT"
    }
}

/** One fix per second from the demo car */
class FakeLocation(private val drive: DemoDrive, private val scope: CoroutineScope) : LocationPort {
    private var job: Job? = null
    override val fixes = MutableSharedFlow<Fix>(extraBufferCapacity = 8)

    override suspend fun start() {
        if (job != null) return
        job = scope.launch {
            while (isActive) {
                fixes.tryEmit(drive.fix.value.copy(timeMs = System.currentTimeMillis()))
                delay(1_000)
            }
        }
    }

    override suspend fun current(): Fix = drive.fix.value

    override fun stop() {
        job?.cancel()
        job = null
    }
}

/**
 * Gear follows the demo car (D while moving, P when stopped) unless forced with [toggleGear];
 * doors are toggled from the panel's debug buttons and drive the real boarding detection.
 */
class FakeVehicle(private val drive: DemoDrive, private val scope: CoroutineScope) : VehiclePort {
    private val _gear = MutableStateFlow(Gear.PARK)
    @Volatile private var forcedGear: Gear? = null
    private val _doors = MutableStateFlow(Door.entries.associateWith { false })
    override val gear: StateFlow<Gear> = _gear.asStateFlow()
    override val doors: StateFlow<Map<Door, Boolean>> = _doors.asStateFlow()
    private var job: Job? = null

    override suspend fun start() {
        if (job != null) return
        job = scope.launch {
            while (isActive) {
                _gear.value = forcedGear ?: if (drive.isMoving) Gear.DRIVE else Gear.PARK
                delay(500)
            }
        }
    }

    /** Debug: force P ↔ D (overrides the automatic gear until toggled back to auto) */
    fun toggleGear() {
        forcedGear = when (forcedGear) {
            null -> if (_gear.value == Gear.PARK) Gear.DRIVE else Gear.PARK
            Gear.PARK -> Gear.DRIVE
            else -> null // back to automatic
        }
        _gear.value = forcedGear ?: if (drive.isMoving) Gear.DRIVE else Gear.PARK
    }

    /** null = automatic */
    val gearMode get() = forcedGear

    fun toggleDoor(door: Door) {
        _doors.value = _doors.value + (door to !(_doors.value[door] ?: false))
    }

    override fun release() {
        job?.cancel()
        job = null
    }
}

/** Silent stand-in (tests, devices without Pleos TTS): logs and takes about as long as speaking would. */
class FakeTts : TtsPort {
    override suspend fun start() = Unit
    override suspend fun speak(text: String) {
        Log.i("NAWA_PORT", "FakeTts: $text")
        delay(text.length * 80L)
    }

    override fun release() = Unit
}
