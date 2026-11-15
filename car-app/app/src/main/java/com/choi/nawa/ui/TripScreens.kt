package com.choi.nawa.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.choi.nawa.data.StopKind
import com.choi.nawa.port.Door
import com.choi.nawa.port.fake.FakeVehicle
import com.choi.nawa.trip.StopProgress
import com.choi.nawa.trip.StopStatus
import com.choi.nawa.trip.TripController
import com.choi.nawa.trip.TripPhase
import com.choi.nawa.trip.TripState
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.launch

/**
 * 공유: one QR per pickup stop. The driver scans it with their phone, which opens /send/{token}
 * and passes the passenger link on through the phone's share sheet (contacts/SMS may be blocked in the car).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ShareScreen(state: TripState, onDepart: () -> Unit, onCancel: suspend () -> Unit) {
    val scope = rememberCoroutineScope()
    Screen(title = "탑승자에게 링크 보내기", backLabel = "운행 취소", onBack = { scope.launch { onCancel() } }) {
        Text("폰 카메라로 QR을 찍으면 탑승자에게 보낼 링크가 열려요", fontSize = 20.sp, color = Muted)
        Spacer(Modifier.height(20.dp))
        FlowRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(28.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            for (stop in state.stops.filter { it.kind == StopKind.PICKUP }) {
                val share = stop.share ?: continue
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val qr = remember(share.sendUrl) { qrBitmap(share.sendUrl) }
                    Image(qr, contentDescription = "${stop.order}번 정류지 공유 QR", Modifier.size(260.dp).background(Color.White))
                    Text("${stop.order}번 정류지", Modifier.padding(top = 8.dp), fontSize = 22.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        ErrorText(state.error)
        BigButton("출발", color = Signal, onClick = onDepart)
    }
}

/**
 * 운행 패널: what the driver sees on returning from the navi. One big button at a time:
 * [도착 처리] while heading to a stop, then [탑승 완료] (pickup) or [다음으로] (waypoint).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PanelScreen(controller: TripController, state: TripState, fakeVehicle: FakeVehicle?, onSettings: () -> Unit, onNewTrip: () -> Unit) {
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
            Text("모든 공유 링크가 만료됐어요", fontSize = 20.sp, color = Muted)
            Spacer(Modifier.weight(1f))
            BigButton("새 운행 준비", onClick = onNewTrip)
        }
        return
    }

    val current = state.current
    Screen(title = "운행 중", actionLabel = "설정", onAction = onSettings) {
        Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
            // Map: the web's driver map (car, route, stops, passenger positions, "wait here" pins)
            val mapUrl = remember(state.tripId) { driverMapUrl(state.tripId, state.driverToken) }
            DriverMapView(mapUrl, Modifier.weight(1.1f).fillMaxHeight())

            Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState())) {
                if (current != null) {
                    Text(stopTitle(current), fontSize = 34.sp, fontWeight = FontWeight.ExtraBold, color = Ink)
                    Text(statusLine(current, state), fontSize = 22.sp, color = Muted)
                }
                Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Spacer(Modifier.size(10.dp).background(if (state.connected) Color(0xFF2E9E5B) else Color(0xFFC0392B), CircleShape))
                    Text(
                        "  ${if (state.connected) "서버 연결됨" else "서버 연결 중…"} · ${"%.0f".format(state.speedKmh)} km/h",
                        fontSize = 16.sp,
                        color = Muted,
                    )
                }
                ErrorText(state.error)

                SectionTitle("정류지")
                for (stop in state.stops) StopRow(stop)

                if (state.replies.isNotEmpty()) {
                    SectionTitle("탑승자 응답")
                    for (r in state.replies.take(6)) {
                        Text("${r.stopOrder}번 정류지 · ${replyText(r.type)}${if (r.position != null) " (위치 있음)" else ""}", fontSize = 18.sp)
                    }
                }

                if (fakeVehicle != null) {
                    SectionTitle("데모: 문 (6단계에서 탑승 판정에 사용)")
                    FlowRow(horizontalArrangement = Gap) {
                        for ((door, label) in listOf(Door.REAR_RIGHT to "뒷좌석 오른쪽", Door.FRONT_PASSENGER to "조수석")) {
                            SecondaryButton("$label 문 열기/닫기") { fakeVehicle.toggleDoor(door) }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Gap, verticalAlignment = Alignment.CenterVertically) {
            SecondaryButton("운행 종료", enabled = !busy) { act { controller.end() } }
            Spacer(Modifier.width(8.dp))
            when (current?.status) {
                StopStatus.CURRENT -> BigButton("도착 처리", Modifier.weight(1f), enabled = !busy) { act { controller.arrive() } }
                StopStatus.ARRIVED -> BigButton(
                    if (current.kind == StopKind.PICKUP) "탑승 완료" else "다음으로",
                    Modifier.weight(1f),
                    enabled = !busy,
                    color = Signal,
                ) { act { controller.complete() } }
                else -> Unit
            }
        }
    }
}

@Composable
private fun StopRow(stop: StopProgress) {
    val (mark, color) = when (stop.status) {
        StopStatus.COMPLETED -> "완료" to Muted
        StopStatus.ARRIVED -> "도착" to Ink
        StopStatus.CURRENT -> "이동 중" to Ink
        StopStatus.UPCOMING -> "대기" to Muted
    }
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("${stop.order}", Modifier.width(36.dp), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = color)
        Text(stopTitle(stop), Modifier.weight(1f), fontSize = 20.sp, color = color)
        if (stop.passengerLocation != null) Text("위치 공유 중  ", fontSize = 15.sp, color = Muted)
        Text(mark, fontSize = 18.sp, color = color, fontWeight = if (stop.status == StopStatus.CURRENT) FontWeight.Bold else FontWeight.Normal)
    }
    HorizontalDivider()
}

private fun stopTitle(stop: StopProgress) =
    if (stop.kind == StopKind.PICKUP) "${stop.order}번 정류지 (픽업)" else "${stop.order}번 정류지 · ${stop.label}"

private fun statusLine(stop: StopProgress, state: TripState) = when (stop.status) {
    StopStatus.ARRIVED -> if (stop.kind == StopKind.PICKUP) "도착했어요. 탑승하면 [탑승 완료]를 눌러 주세요" else "도착했어요. 볼일을 마치면 [다음으로]"
    StopStatus.CURRENT -> state.legEtaSeconds?.let { "${(it + 59) / 60}분 뒤 도착" } ?: "도착 시간 계산 중…"
    else -> ""
}

fun replyText(type: String) = when (type) {
    "out" -> "나왔어요"
    "late" -> "5분 늦어요"
    "waitHere" -> "여기서 기다릴게요"
    else -> type
}

private fun qrBitmap(text: String, size: Int = 520): ImageBitmap {
    val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size, mapOf(EncodeHintType.MARGIN to 1))
    val pixels = IntArray(size * size) { i -> if (matrix[i % size, i / size]) 0xFF000000.toInt() else 0xFFFFFFFF.toInt() }
    return Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888).asImageBitmap()
}
