package com.choi.nawa.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import com.choi.nawa.data.StopKind
import com.choi.nawa.port.Door
import com.choi.nawa.port.Gear
import com.choi.nawa.port.fake.FakeVehicle
import com.choi.nawa.trip.StopProgress
import com.choi.nawa.trip.StopStatus
import com.choi.nawa.trip.TripController
import com.choi.nawa.trip.TripPhase
import com.choi.nawa.trip.TripState
import com.choi.nawa.ui.theme.Palette
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.launch

/**
 * 공유: one QR per pickup stop. The driver scans it with their phone, which opens /send/{token}
 * and passes the passenger link on through the phone's share sheet (contacts/SMS may be blocked in the car).
 */
@Composable
fun ShareScreen(state: TripState, onDepart: () -> Unit, onCancel: suspend () -> Unit) {
    val scope = rememberCoroutineScope()
    Screen(title = "탑승자에게 링크 보내기", backLabel = "운행 취소", onBack = { scope.launch { onCancel() } }) {
        Text("탑승자마다 QR이 하나씩 있어요", style = MaterialTheme.typography.bodyLarge, color = Palette.Muted)
        Spacer(Modifier.height(16.dp))
        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
            // As large as the height allows: number above, guide below
            val qrSize = (maxHeight - 200.dp).coerceIn(240.dp, 560.dp)
            Row(Modifier.fillMaxSize().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                for (stop in state.stops.filter { it.kind == StopKind.PICKUP }) {
                    val share = stop.share ?: continue
                    Card(Modifier.width(qrSize + 48.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            StopNumber(stop.order)
                            Text("${stop.order}번 정류지", Modifier.padding(start = 16.dp), style = MaterialTheme.typography.headlineMedium, color = Palette.Ink)
                        }
                        Spacer(Modifier.height(16.dp))
                        val qr = remember(share.sendUrl) { qrBitmap(share.sendUrl) }
                        Image(qr, contentDescription = "${stop.order}번 정류지 공유 QR", Modifier.size(qrSize))
                        Spacer(Modifier.height(12.dp))
                        Text("폰 카메라로 찍어서 탑승자에게 보내세요", style = MaterialTheme.typography.bodyLarge, color = Palette.Muted)
                    }
                }
            }
        }
        ErrorText(state.error)
        Spacer(Modifier.height(16.dp))
        BigButton("출발", onClick = onDepart)
    }
}

/**
 * 운행 패널: what the driver sees on returning from the navi. One big button at a time:
 * [도착 처리] while heading to a stop, then [탑승 완료] (pickup) or [다음으로] (waypoint).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PanelScreen(
    controller: TripController,
    state: TripState,
    fakeVehicle: FakeVehicle?,
    driving: Boolean,
    onSettings: () -> Unit,
    onNewTrip: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    fun act(block: suspend () -> Unit) {
        if (busy) return
        busy = true
        scope.launch {
            try {
                block()
            } finally {
                busy = false
            }
        }
    }

    if (state.phase == TripPhase.ENDED) {
        Screen(title = "운행이 끝났어요") {
            Card(Modifier.fillMaxWidth()) {
                Text("모든 공유 링크가 만료됐어요", style = MaterialTheme.typography.bodyLarge, color = Palette.Muted)
            }
            Spacer(Modifier.weight(1f))
            BigButton("새 운행 준비", onClick = onNewTrip)
        }
        return
    }

    val current = state.current
    Screen(title = "운행 중", actionLabel = "설정", onAction = onSettings) {
        Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            // Map: the web's driver map (car, route, stops, passenger positions, "wait here" pins)
            val mapUrl = remember(state.tripId) { driverMapUrl(state.tripId, state.driverToken) }
            DriverMapView(mapUrl, Modifier.weight(1.15f).fillMaxHeight())

            Card(Modifier.weight(1f).fillMaxHeight(), padding = PaddingValues(horizontal = 28.dp, vertical = 24.dp)) {
                if (current != null) {
                    Text(stopTitle(current), style = MaterialTheme.typography.headlineLarge, color = Palette.Ink, maxLines = 1)
                    Text(etaLine(current, state), style = MaterialTheme.typography.headlineLarge, color = Palette.Route)
                    hintLine(current)?.let { Text(it, style = MaterialTheme.typography.bodyLarge, color = Palette.Muted) }
                }
                Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Spacer(Modifier.size(12.dp).background(if (state.connected) Palette.Ok else Palette.Danger, CircleShape))
                    Text(
                        "${if (state.connected) "서버 연결됨" else "서버 연결 중…"} · ${"%.0f".format(state.speedKmh)} km/h",
                        Modifier.padding(start = 10.dp),
                        style = MaterialTheme.typography.labelLarge,
                        color = Palette.Muted,
                    )
                }
                ErrorText(state.error)
                HorizontalDivider(Modifier.padding(vertical = 16.dp), color = Palette.Line)

                StopTimeline(state, Modifier.fillMaxWidth().weight(1f))

                // Messages only: they never change the destination or the stops
                if (state.replies.isNotEmpty()) {
                    HorizontalDivider(Modifier.padding(vertical = 8.dp), color = Palette.Line)
                    for (r in state.replies.take(3)) {
                        Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(clock(r.atMs), Modifier.width(84.dp), style = MaterialTheme.typography.labelLarge, color = Palette.Muted)
                            Text(
                                "${r.text}${if (r.position != null) " (위치 표시)" else ""}",
                                style = MaterialTheme.typography.bodyLarge,
                                color = Palette.Ink,
                                maxLines = 1,
                            )
                        }
                    }
                }

                if (fakeVehicle != null) DemoVehicleControls(fakeVehicle)
            }
        }

        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Gap, verticalAlignment = Alignment.CenterVertically) {
            // Only the one big button stays usable while driving
            SecondaryButton("운행 종료", Modifier.height(88.dp), enabled = !busy && !driving) { act { controller.end() } }
            when (current?.status) {
                StopStatus.CURRENT -> BigButton("도착 처리", Modifier.weight(1f), enabled = !busy) { act { controller.arrive() } }
                StopStatus.ARRIVED -> BigButton(
                    if (current.kind == StopKind.PICKUP) "탑승 완료" else "다음으로",
                    Modifier.weight(1f),
                    enabled = !busy,
                ) { act { controller.complete() } }
                else -> Unit
            }
        }
    }
}

/** Demo mode: the simulated car's doors and gear. Doors feed the real boarding detection. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DemoVehicleControls(fakeVehicle: FakeVehicle) {
    val doors by fakeVehicle.doors.collectAsState()
    val gear by fakeVehicle.gear.collectAsState()
    SectionTitle("데모 차량")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        for ((door, label) in listOf(Door.FRONT_PASSENGER to "조수석", Door.REAR_RIGHT to "뒷좌석", Door.DRIVER to "운전석")) {
            SecondaryButton("$label 문 ${if (doors[door] == true) "닫기" else "열기"}") { fakeVehicle.toggleDoor(door) }
        }
        SecondaryButton("기어 ${gear.name.take(1)} → ${if (fakeVehicle.gearMode == null) "고정" else if (fakeVehicle.gearMode == Gear.PARK) "D" else "자동"}") {
            fakeVehicle.toggleGear()
        }
    }
}

private fun stopTitle(stop: StopProgress) =
    if (stop.kind == StopKind.PICKUP) "${stop.order}번 · 픽업" else "${stop.order}번 · ${stop.label}"

private fun etaLine(stop: StopProgress, state: TripState) = when (stop.status) {
    StopStatus.ARRIVED -> "도착했어요"
    else -> state.legEtaSeconds?.let { "${etaText(it)} 뒤 도착" } ?: "도착 시간 계산 중…"
}

private fun hintLine(stop: StopProgress) = when (stop.status) {
    StopStatus.ARRIVED -> if (stop.kind == StopKind.PICKUP) "탑승하면 문이 닫힐 때 자동으로 넘어가요. 또는 [탑승 완료]" else "볼일을 마치고 출발하면 자동으로 넘어가요. 또는 [다음으로]"
    else -> null
}

private fun qrBitmap(text: String, size: Int = 560): ImageBitmap {
    val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size, mapOf(EncodeHintType.MARGIN to 1))
    val dark = Palette.Ink.toArgb()
    val light = Palette.Card.toArgb()
    val pixels = IntArray(size * size) { i -> if (matrix[i % size, i / size]) dark else light }
    return Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888).asImageBitmap()
}
