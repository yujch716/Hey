package com.choi.nawa.port.pleos

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import ai.pleos.playground.location.fused.FusedLocation
import ai.pleos.playground.location.fused.listener.FusedLocationListener
import ai.pleos.playground.location.fused.model.LocationData
import ai.pleos.playground.navi.data.CurrentLocationInfo
import ai.pleos.playground.navi.data.DestinationArrivedInfo
import ai.pleos.playground.navi.data.DestinationInfo
import ai.pleos.playground.navi.data.RouteInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import ai.pleos.playground.tts.TextToSpeech
import ai.pleos.playground.tts.constant.ErrorType
import ai.pleos.playground.tts.constant.Mode
import ai.pleos.playground.tts.listener.EventListener
import ai.pleos.playground.tts.listener.OnInitializeListener
import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.DoorArea
import ai.pleos.playground.vehicle.constant.area.DoorValue
import ai.pleos.playground.vehicle.constant.control.VehicleGear
import ai.pleos.playground.vehicle.listener.DoorPositionListener
import ai.pleos.playground.vehicle.listener.GearSelectionListener
import com.choi.nawa.data.LatLng
import com.choi.nawa.port.Door
import com.choi.nawa.port.Fix
import com.choi.nawa.port.Gear
import com.choi.nawa.port.LocationPort
import com.choi.nawa.port.NaviDestination
import com.choi.nawa.port.NaviPort
import com.choi.nawa.port.TtsPort
import com.choi.nawa.port.VehiclePort
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

private const val TAG = "NAWA_PORT"
private const val ANSWER_TIMEOUT_MS = 2_000L

/**
 * NaviHelper. Queries never return values: the answer arrives on the listener, so each query
 * waits for its matching callback. requestRoute brings the navi app to the front.
 */
@SuppressLint("MissingPermission") // NAVI_ROUTE / NAVI_ROUTE_SEARCH are declared and auto-granted
class PleosNavi(context: Context) : NaviPort {
    private val navi = NaviHelper(context.applicationContext)
    private val queryLock = Mutex()
    @Volatile private var pendingDestination: CompletableDeferred<DestinationInfo>? = null
    @Volatile private var pendingLocation: CompletableDeferred<CurrentLocationInfo>? = null

    override val arrivals = MutableSharedFlow<String>(extraBufferCapacity = 4)

    private val listener = object : NaviHelperEventListener {
        override fun onDestinationArrived(destinationArrivedInfo: DestinationArrivedInfo) {
            arrivals.tryEmit(destinationArrivedInfo.name)
        }

        override fun onDestinationInfo(destinationInfo: DestinationInfo) {
            pendingDestination?.complete(destinationInfo)
        }

        override fun onCurrentLocationInfo(currentLocationInfo: CurrentLocationInfo) {
            pendingLocation?.complete(currentLocationInfo)
        }
    }

    override suspend fun start() = suspendCancellableCoroutine { cont ->
        navi.initialize(
            onConnected = {
                navi.addListener(listener)
                if (cont.isActive) cont.resume(Unit)
            },
            onFailed = {
                Log.w(TAG, "NaviHelper connection failed", it)
                if (cont.isActive) cont.resume(Unit) // the trip can still run on location + estimates
            },
        )
    }

    override fun requestRoute(name: String, position: LatLng) {
        // No POI id: the navi accepts coordinates + name (verified in stage 3)
        navi.requestRoute(RouteInfo(latitude = position.lat, longitude = position.lng, poiName = name, poiId = "", address = "", poiSubId = "0"))
    }

    override fun cancelRoute() = navi.cancelRoute()

    override suspend fun destination(): NaviDestination? = queryLock.withLock {
        val answer = CompletableDeferred<DestinationInfo>().also { pendingDestination = it }
        navi.getDestinationInfo()
        val info = withTimeoutOrNull(ANSWER_TIMEOUT_MS) { answer.await() }
        pendingDestination = null
        info?.takeIf { it.name.isNotEmpty() }?.let {
            NaviDestination(it.name, LatLng(it.latitude, it.longitude), it.remainTime, it.remainDistance)
        }
    }

    override suspend fun currentLocation(): Fix? = queryLock.withLock {
        val answer = CompletableDeferred<CurrentLocationInfo>().also { pendingLocation = it }
        navi.getCurrentLocationInfo()
        val info = withTimeoutOrNull(ANSWER_TIMEOUT_MS) { answer.await() }
        pendingLocation = null
        info?.let { Fix(LatLng(it.latitude, it.longitude), it.heading.takeIf { h -> h != 0.0 }, System.currentTimeMillis()) }
    }

    override fun release() {
        runCatching {
            navi.removeListener(listener)
            navi.release()
        }
    }
}

/** FusedLocation (1 s updates). If the environment doesn't support it, poll the navi's position instead. */
@SuppressLint("MissingPermission") // FUSED_LOCATION is requested at runtime before a trip starts
class PleosLocation(context: Context, private val fallback: NaviPort) : LocationPort {
    private val fused = FusedLocation(context.applicationContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val latest = MutableStateFlow<Fix?>(null)
    private var pollJob: Job? = null
    private var usingFused = false

    override val fixes = MutableSharedFlow<Fix>(extraBufferCapacity = 8)

    private val listener = object : FusedLocationListener {
        override fun onChanged(location: LocationData?) {
            location ?: return
            publish(Fix(LatLng(location.latitude, location.longitude), location.heading.toDouble().takeIf { it != 0.0 }, location.timestampMillis))
        }

        override fun onError(exception: Exception) {
            Log.w(TAG, "FusedLocation error", exception)
        }
    }

    override suspend fun start() {
        if (usingFused || pollJob != null) return
        usingFused = runCatching { fused.isSupported() }.getOrDefault(false)
        if (usingFused) {
            fused.initialize()
            fused.registerFusedLocationCallback(listener)
        } else {
            Log.i(TAG, "FusedLocation unsupported, falling back to NaviHelper location")
            pollJob = scope.launch {
                while (isActive) {
                    fallback.currentLocation()?.let(::publish)
                    delay(1_000)
                }
            }
        }
    }

    override suspend fun current(): Fix? = latest.value ?: withTimeoutOrNull(3_000) { latest.filterNotNull().first() }

    override fun stop() {
        pollJob?.cancel()
        pollJob = null
        if (usingFused) runCatching {
            fused.unregisterFusedLocationCallback(listener)
            fused.release()
        }
        usingFused = false
    }

    private fun publish(fix: Fix) {
        latest.value = fix
        fixes.tryEmit(fix)
    }
}

/**
 * Vehicle SDK. initialize() has no completion callback and calls fail with "Service connection failed"
 * until the service binds, so registration retries briefly until it goes through.
 */
@SuppressLint("MissingPermission") // CAR_DOORS / CAR_DRIVE are declared and auto-granted
class PleosVehicle(context: Context) : VehiclePort {
    private val vehicle = Vehicle(context.applicationContext)
    private val _gear = MutableStateFlow(Gear.UNKNOWN)
    private val _doors = MutableStateFlow(Door.entries.associateWith { false })
    override val gear: StateFlow<Gear> = _gear.asStateFlow()
    override val doors: StateFlow<Map<Door, Boolean>> = _doors.asStateFlow()

    private val areas = mapOf(
        Door.DRIVER to DoorValue.ROW_1_LEFT,
        Door.FRONT_PASSENGER to DoorValue.ROW_1_RIGHT,
        Door.REAR_LEFT to DoorValue.ROW_2_LEFT,
        Door.REAR_RIGHT to DoorValue.ROW_2_RIGHT,
    )
    private val doorListeners = areas.keys.associateWith { door ->
        object : DoorPositionListener {
            override fun onPositionUpdated(pos: Int?) {
                _doors.value = _doors.value + (door to ((pos ?: 0) > 0))
            }

            override fun onFailed(e: Exception) {
                Log.w(TAG, "door $door listener failed", e)
            }
        }
    }
    private val gearListener = object : GearSelectionListener {
        override fun onSelected(gear: VehicleGear?) {
            _gear.value = gear.toGear()
        }

        override fun onFailed(e: Exception) {
            Log.w(TAG, "gear listener failed", e)
        }
    }

    override suspend fun start() {
        vehicle.initialize()
        if (!awaitConnected()) {
            Log.w(TAG, "Vehicle service never connected")
            return
        }
        for ((door, value) in areas) vehicle.getDoor().registerDoorPosition(DoorArea(value), doorListeners.getValue(door))
        vehicle.getGear().registerGearSelection(gearListener)
    }

    /** Polls a cheap read until the service answers instead of failing with a connection error. */
    private suspend fun awaitConnected(): Boolean {
        repeat(CONNECT_ATTEMPTS) {
            val ok = suspendCancellableCoroutine { cont ->
                vehicle.getGear().getCurrentGearSelectionState(
                    { g -> _gear.value = g.toGear(); if (cont.isActive) cont.resume(true) },
                    { if (cont.isActive) cont.resume(false) },
                )
            }
            if (ok) return true
            delay(CONNECT_RETRY_MS)
        }
        return false
    }

    override fun release() {
        runCatching {
            for ((door, value) in areas) vehicle.getDoor().unregisterDoorPosition(DoorArea(value), doorListeners.getValue(door))
            vehicle.getGear().unregisterGearSelection(gearListener)
            vehicle.release()
        }
    }

    private fun VehicleGear?.toGear() = when (this) {
        VehicleGear.GEAR_PARK -> Gear.PARK
        VehicleGear.GEAR_REVERSE -> Gear.REVERSE
        VehicleGear.GEAR_NEUTRAL -> Gear.NEUTRAL
        null, VehicleGear.GEAR_UNKNOWN -> Gear.UNKNOWN
        else -> Gear.DRIVE
    }

    private companion object {
        const val CONNECT_ATTEMPTS = 20
        const val CONNECT_RETRY_MS = 250L
    }
}

/** ON_DEVICE TextToSpeech: no Client ID/Secret needed. Korean (ko-KR) verified on the emulator. */
class PleosTts(context: Context) : TtsPort {
    private val tts = TextToSpeech(context.applicationContext, Mode.ON_DEVICE)
    private val ready = MutableStateFlow(false)
    @Volatile private var speaking: CompletableDeferred<Unit>? = null

    private val events = object : EventListener {
        override fun onStart() = Unit
        override fun onReady() = Unit
        override fun onDone() = finish()
        override fun onError(errorType: ErrorType) {
            Log.w(TAG, "TTS error: $errorType")
            finish()
        }

        override fun onStop() = finish()
        override fun onUpdatedRms(rms: Double) = Unit
    }

    override suspend fun start() {
        tts.initialize(object : OnInitializeListener {
            override fun onSuccess() {
                tts.addEventListener(events)
                tts.setLocale(java.util.Locale.KOREA)
                ready.value = true
            }

            override fun onFailure(errorType: ErrorType) {
                Log.w(TAG, "TTS init failed: $errorType")
            }
        })
    }

    override suspend fun speak(text: String) {
        if (withTimeoutOrNull(3_000) { ready.first { it } } == null) {
            Log.w(TAG, "TTS not ready, dropped: $text")
            return
        }
        val done = CompletableDeferred<Unit>().also { speaking = it }
        tts.speak(text, true)
        // onDone normally ends it; never let one lost callback block the queue
        withTimeoutOrNull(SPEAK_TIMEOUT_MS) { done.await() }
        speaking = null
    }

    private fun finish() {
        speaking?.complete(Unit)
    }

    override fun release() {
        runCatching {
            tts.removeEventListener(events)
            tts.release()
        }
    }

    private companion object {
        const val SPEAK_TIMEOUT_MS = 15_000L
    }
}
