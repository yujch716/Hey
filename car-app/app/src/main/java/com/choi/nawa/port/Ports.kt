package com.choi.nawa.port

import com.choi.nawa.data.LatLng
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * The car's hardware and built-in apps, behind interfaces. Pleos* talk to the Pleos SDKs,
 * Fake* drive a simulated car for the demo mode and for tests. See docs/sdk-findings.md.
 */

/** A position fix. `heading` is null when the source has none (FusedLocation reports 0 in the emulator). */
data class Fix(val position: LatLng, val heading: Double?, val timeMs: Long)

data class NaviDestination(val name: String, val position: LatLng, val remainSeconds: Int, val remainMeters: Int)

/** The built-in navi. Only ever routed to one stop at a time (no waypoints). */
interface NaviPort {
    /** Fires when the navi decides the car reached its destination */
    val arrivals: Flow<String>

    suspend fun start()
    fun requestRoute(name: String, position: LatLng)
    fun cancelRoute()

    /** Current destination and remaining time/distance. Pleos rounds these to 1 min / 1 km. Null if unknown. */
    suspend fun destination(): NaviDestination?

    /** The navi's own idea of where the car is (fallback when FusedLocation is unsupported) */
    suspend fun currentLocation(): Fix?

    fun release()
}

interface LocationPort {
    /** About one fix per second while started */
    val fixes: Flow<Fix>

    suspend fun start()

    /** Latest fix, waiting up to a couple of seconds for the first one */
    suspend fun current(): Fix?
    fun stop()
}

enum class Door { DRIVER, FRONT_PASSENGER, REAR_LEFT, REAR_RIGHT }

enum class Gear { PARK, REVERSE, NEUTRAL, DRIVE, UNKNOWN }

interface VehiclePort {
    val gear: StateFlow<Gear>

    /** true = open */
    val doors: StateFlow<Map<Door, Boolean>>

    suspend fun start()
    fun release()
}

interface TtsPort {
    suspend fun start()

    /** Speaks and returns when done (or failed), so messages can be read one after another */
    suspend fun speak(text: String)
    fun release()
}

/** The four ports a trip runs on */
class Ports(val navi: NaviPort, val location: LocationPort, val vehicle: VehiclePort, val tts: TtsPort) {
    suspend fun startAll() {
        navi.start()
        location.start()
        vehicle.start()
        tts.start()
    }

    fun releaseAll() {
        location.stop()
        navi.release()
        vehicle.release()
        tts.release()
    }
}
