package com.choi.nawa

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.choi.nawa.data.HttpTripApi
import com.choi.nawa.data.SettingsStore
import com.choi.nawa.data.SocketIoDriverChannel
import com.choi.nawa.data.TripApi
import com.choi.nawa.port.AskFirstNavi
import com.choi.nawa.port.Ports
import com.choi.nawa.port.fake.DemoDrive
import com.choi.nawa.port.fake.DemoNaviWithBuiltIn
import com.choi.nawa.port.fake.FakeLocation
import com.choi.nawa.port.fake.FakeNavi
import com.choi.nawa.port.fake.FakeVehicle
import com.choi.nawa.port.pleos.PleosLocation
import com.choi.nawa.port.pleos.PleosNavi
import com.choi.nawa.port.pleos.PleosTts
import com.choi.nawa.port.pleos.PleosVehicle
import com.choi.nawa.trip.TripController
import com.choi.nawa.trip.TripPhase
import com.choi.nawa.trip.TripService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class NawaApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

val Context.container get() = (applicationContext as NawaApp).container

/** The ports actually in use and, in demo mode, the simulated car behind them. */
class ActivePorts(
    val ports: Ports,
    val demo: Boolean,
    val drive: DemoDrive?,
    val fakeVehicle: FakeVehicle?,
    /** Demo mode only: the built-in navi is also routed (settings: 데모 모드에서 내장 내비 실행) */
    val builtInNavi: Boolean = false,
    /** The built-in navi's destinations wait for the driver's 예 (the app shows the question) */
    val routePrompt: AskFirstNavi? = null,
)

/**
 * App-wide objects. The trip outlives the activity (the navi takes the screen), so the controller
 * lives here and the foreground service only keeps its loop running.
 */
class AppContainer(private val app: Application) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val settings = SettingsStore(app)
    val api: TripApi = HttpTripApi(BuildConfig.SERVER_URL)

    private val portsLock = Mutex()
    private val _active = MutableStateFlow<ActivePorts?>(null)
    val active: StateFlow<ActivePorts?> = _active.asStateFlow()

    private val _trip = MutableStateFlow<TripController?>(null)
    val trip: StateFlow<TripController?> = _trip.asStateFlow()

    init {
        // The demo speed can change mid-trip from the settings screen
        scope.launch { settings.demoSpeed.collect { x -> _active.value?.drive?.speedMultiplier = x } }
    }

    /** Ports for the current demo setting, (re)built and started when the setting changed. Call after permissions. */
    suspend fun ports(): ActivePorts = portsLock.withLock {
        val demo = settings.demoMode.first()
        val builtInNavi = demo && settings.demoBuiltInNavi.first()
        _active.value?.takeIf { it.demo == demo && it.builtInNavi == builtInNavi }?.let { return it }
        _active.value?.ports?.releaseAll()

        val built = if (demo) {
            val drive = DemoDrive(scope).apply { speedMultiplier = settings.demoSpeed.first() }
            val vehicle = FakeVehicle(drive, scope)
            val prompt = if (builtInNavi) AskFirstNavi(PleosNavi(app)) else null
            val navi = if (prompt != null) DemoNaviWithBuiltIn(FakeNavi(drive), prompt) else FakeNavi(drive)
            // Speech is real even in demo mode: it doesn't depend on the (simulated) car
            ActivePorts(Ports(navi, FakeLocation(drive, scope), vehicle, PleosTts(app)), true, drive, vehicle, builtInNavi, prompt)
        } else {
            val navi = PleosNavi(app)
            val prompt = AskFirstNavi(navi)
            ActivePorts(Ports(prompt, PleosLocation(app, navi), PleosVehicle(app), PleosTts(app)), false, null, null, routePrompt = prompt)
        }
        built.ports.startAll()
        placeDemoCarAtNavi(built)
        _active.value = built
        built
    }

    /**
     * Demo with the built-in navi: park the demo car where the navi thinks the car is, so the trip's origin
     * (and so the server's route) starts where the navi's route starts. Keeps DEMO_ORIGIN if the navi doesn't answer.
     */
    private suspend fun placeDemoCarAtNavi(active: ActivePorts) {
        val navi = active.ports.navi as? DemoNaviWithBuiltIn ?: return
        val here = navi.builtInLocation()?.position ?: return
        active.drive?.placeAt(here)
    }

    /** A controller for a new trip on the current ports */
    suspend fun newTrip(): TripController {
        val active = ports()
        placeDemoCarAtNavi(active)
        val controller = TripController(
            api = api,
            channel = SocketIoDriverChannel(BuildConfig.SERVER_URL),
            ports = active.ports,
            // Demo videos run at up to 5x: send every second so the map doesn't lag behind the car
            sendIntervalMs = if (active.demo) DEMO_SEND_INTERVAL_MS else TripController.SEND_INTERVAL_MS,
            onRoutePlanned = { planned -> active.drive?.load(planned.route, planned.stops) },
        )
        _trip.value = controller
        return controller
    }

    /** Drop a finished (or never started) trip so the planning screen shows again */
    fun discardTrip() {
        val t = _trip.value ?: return
        if (t.state.value?.phase == TripPhase.DRIVING) return
        t.clear()
        _trip.value = null
    }

    fun startTripService() = ContextCompat.startForegroundService(app, Intent(app, TripService::class.java))

    private companion object {
        const val DEMO_SEND_INTERVAL_MS = 1_000L
    }
}
