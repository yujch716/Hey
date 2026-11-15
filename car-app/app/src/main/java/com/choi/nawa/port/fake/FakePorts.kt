package com.choi.nawa.port.fake

import android.util.Log
import com.choi.nawa.data.Geo
import com.choi.nawa.data.LatLng
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

    private val _fix = MutableStateFlow(Fix(DEMO_ORIGIN, null, System.currentTimeMillis()))
    val fix: StateFlow<Fix> = _fix.asStateFlow()

    private val _arrivals = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val arrivals: SharedFlow<String> = _arrivals

    val isMoving get() = target != null && !arrived

    /** Remaining metres to the current destination, null when there is none */
    val remainingMeters get() = target?.let { (it - meters).coerceAtLeast(0.0) }

    fun load(route: List<LatLng>) {
        if (route.size < 2) return
        this.route = route
        cum = Geo.cumulative(route)
        meters = 0.0
        target = null
        arrived = false
        publish()
    }

    fun driveTo(name: String, position: LatLng) {
        // Stops sit on the route vertex closest to them; never drive backwards
        target = maxOf(cum[Geo.nearestIndex(route, position)], meters)
        targetName = name
        arrived = false
    }

    fun cancel() {
        target = null
        arrived = false
    }

    fun step(seconds: Double) {
        val t = target ?: return
        if (arrived) return
        meters = min(t, meters + DEMO_SPEED_MPS * speedMultiplier * seconds)
        publish()
        if (meters >= t) {
            arrived = true
            _arrivals.tryEmit(targetName)
        }
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

/** Gear follows the demo car (D while moving, P when stopped); doors are toggled from the panel's debug buttons. */
class FakeVehicle(private val drive: DemoDrive, private val scope: CoroutineScope) : VehiclePort {
    private val _gear = MutableStateFlow(Gear.PARK)
    private val _doors = MutableStateFlow(Door.entries.associateWith { false })
    override val gear: StateFlow<Gear> = _gear.asStateFlow()
    override val doors: StateFlow<Map<Door, Boolean>> = _doors.asStateFlow()
    private var job: Job? = null

    override suspend fun start() {
        if (job != null) return
        job = scope.launch {
            while (isActive) {
                _gear.value = if (drive.isMoving) Gear.DRIVE else Gear.PARK
                delay(500)
            }
        }
    }

    fun toggleDoor(door: Door) {
        _doors.value = _doors.value + (door to !(_doors.value[door] ?: false))
    }

    override fun release() {
        job?.cancel()
        job = null
    }
}

class FakeTts : TtsPort {
    override suspend fun start() = Unit
    override fun speak(text: String) {
        Log.i("NAWA_PORT", "FakeTts: $text")
    }

    override fun release() = Unit
}
