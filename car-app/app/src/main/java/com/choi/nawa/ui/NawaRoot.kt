package com.choi.nawa.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.choi.nawa.container
import com.choi.nawa.data.VehicleProfile
import com.choi.nawa.sdkcheck.LocationProbe
import com.choi.nawa.sdkcheck.NaviProbe
import com.choi.nawa.sdkcheck.Probes
import com.choi.nawa.sdkcheck.SdkCheckScreen
import com.choi.nawa.sdkcheck.TtsProbe
import com.choi.nawa.sdkcheck.VehicleProbe
import com.choi.nawa.trip.TripPhase
import kotlinx.coroutines.launch

private sealed interface Overlay {
    data object Settings : Overlay
    data object EditVehicle : Overlay
    data object SdkCheck : Overlay
}

/**
 * Which screen shows is derived from saved data and the trip, not from a back stack:
 *   no vehicle → 차량 등록, no trip → 운행 준비, trip planned → 공유, trip underway/ended → 운행 패널.
 * 설정 and its sub-screens sit on top.
 */
@Composable
fun NawaRoot(permissionsReady: Boolean, onRequestPermissions: () -> Unit) {
    val context = LocalContext.current
    val container = context.container
    val scope = rememberCoroutineScope()

    val vehicleLoad by container.settings.vehicle.collectAsState(initial = Loading)
    val demo by container.settings.demoMode.collectAsState(initial = false)
    val demoSpeed by container.settings.demoSpeed.collectAsState(initial = 1)
    val controller by container.trip.collectAsState()
    val tripState = controller?.state?.collectAsState()?.value
    val active by container.active.collectAsState()
    var overlay by remember { mutableStateOf<Overlay?>(null) }

    // Ports start once location permissions are settled (and again when demo mode flips)
    LaunchedEffect(permissionsReady, demo) {
        if (permissionsReady && controller == null) container.ports()
    }

    BackHandler(enabled = overlay != null) {
        overlay = if (overlay == Overlay.Settings) null else Overlay.Settings
    }

    when (overlay) {
        Overlay.Settings -> {
            SettingsScreen(
                demo = demo,
                demoSpeed = demoSpeed,
                tripRunning = tripState?.phase == TripPhase.DRIVING || tripState?.phase == TripPhase.PLANNED,
                onDemo = { scope.launch { container.settings.setDemoMode(it) } },
                onSpeed = { scope.launch { container.settings.setDemoSpeed(it) } },
                onEditVehicle = { overlay = Overlay.EditVehicle },
                onSdkCheck = { overlay = Overlay.SdkCheck },
                onPermissions = onRequestPermissions,
                onBack = { overlay = null },
            )
            return
        }
        Overlay.EditVehicle -> {
            VehicleScreen(
                initial = vehicleLoad as? VehicleProfile,
                onSave = { v -> scope.launch { container.settings.saveVehicle(v); overlay = Overlay.Settings } },
                onCancel = { overlay = Overlay.Settings },
            )
            return
        }
        Overlay.SdkCheck -> {
            val probes = remember { Probes(NaviProbe(context), LocationProbe(context), VehicleProbe(context), TtsProbe(context)) }
            Column {
                Screen(title = "SDK 진단", backLabel = "← 설정", onBack = { overlay = Overlay.Settings }) {
                    SdkCheckScreen(probes, onRequestPermissions = onRequestPermissions)
                }
            }
            return
        }
        null -> Unit
    }

    val vehicle = vehicleLoad
    when {
        vehicle === Loading -> Unit
        vehicle == null -> VehicleScreen(
            initial = null,
            onSave = { v -> scope.launch { container.settings.saveVehicle(v) } },
            onCancel = null,
        )
        controller == null || tripState == null -> PrepareScreen(
            api = container.api,
            near = { if (permissionsReady) container.ports().ports.location.current()?.position else null },
            demo = demo,
            onStart = { drafts ->
                val c = container.newTrip()
                try {
                    c.create(vehicle as VehicleProfile, drafts)
                } catch (e: Exception) {
                    container.discardTrip()
                    throw e
                }
            },
            onSettings = { overlay = Overlay.Settings },
        )
        tripState.phase == TripPhase.PLANNED -> ShareScreen(
            state = tripState,
            onDepart = {
                controller!!.depart()
                container.startTripService()
            },
            onCancel = {
                controller!!.end()
                container.discardTrip()
            },
        )
        else -> PanelScreen(
            controller = controller!!,
            state = tripState,
            fakeVehicle = active?.fakeVehicle,
            onSettings = { overlay = Overlay.Settings },
            onNewTrip = { container.discardTrip() },
        )
    }
}

/** Marker for "DataStore hasn't answered yet" */
private object Loading

@Composable
private fun SettingsScreen(
    demo: Boolean,
    demoSpeed: Int,
    tripRunning: Boolean,
    onDemo: (Boolean) -> Unit,
    onSpeed: (Int) -> Unit,
    onEditVehicle: () -> Unit,
    onSdkCheck: () -> Unit,
    onPermissions: () -> Unit,
    onBack: () -> Unit,
) {
    Screen(title = "설정", backLabel = "← 뒤로", onBack = onBack) {
        Column(Modifier.widthIn(max = 760.dp)) {
            Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("데모 모드", fontSize = 22.sp)
                    Text(
                        if (tripRunning) "운행 중에는 바꿀 수 없어요" else "가상 차량이 경로를 따라 달려요 (시연 영상용)",
                        fontSize = 16.sp,
                        color = Muted,
                    )
                }
                Switch(checked = demo, onCheckedChange = onDemo, enabled = !tripRunning)
            }
            if (demo) {
                Row(horizontalArrangement = Gap, verticalAlignment = Alignment.CenterVertically) {
                    Text("데모 속도", fontSize = 18.sp)
                    for (x in listOf(1, 5)) FilterChip(selected = demoSpeed == x, onClick = { onSpeed(x) }, label = { Text("${x}x", fontSize = 18.sp) })
                }
            }
            Spacer(Modifier.height(24.dp))
            SecondaryButton("차량 정보 수정", Modifier.fillMaxWidth(), onClick = onEditVehicle)
            Spacer(Modifier.height(12.dp))
            SecondaryButton("위치 권한 다시 요청", Modifier.fillMaxWidth(), onClick = onPermissions)
            Spacer(Modifier.height(12.dp))
            SecondaryButton("SDK 진단", Modifier.fillMaxWidth(), onClick = onSdkCheck)
        }
    }
}
