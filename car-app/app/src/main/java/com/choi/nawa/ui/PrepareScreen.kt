package com.choi.nawa.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.choi.nawa.data.LatLng
import com.choi.nawa.data.MAX_LABEL_LENGTH
import com.choi.nawa.data.Place
import com.choi.nawa.data.StopDraft
import com.choi.nawa.data.StopKind
import com.choi.nawa.data.TripApi
import kotlinx.coroutines.launch

/**
 * 운행 준비: search places, pick each stop's kind, reorder, then [운행 시작].
 * Planned while stopped; nothing here is meant to be touched while driving.
 */
@Composable
fun PrepareScreen(
    api: TripApi,
    near: suspend () -> LatLng?,
    demo: Boolean,
    onStart: suspend (List<StopDraft>) -> Unit,
    onSettings: () -> Unit,
    locked: Boolean = false,
) {
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Place>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var starting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val stops = remember { mutableStateListOf<StopDraft>() }
    var nextId by remember { mutableStateOf(1L) }

    fun search() {
        if (query.isBlank() || searching || locked) return
        searching = true
        error = null
        scope.launch {
            try {
                results = api.searchPlaces(query.trim(), near())
                if (results.isEmpty()) error = "검색 결과가 없어요"
            } catch (e: Exception) {
                error = "장소를 찾지 못했어요: ${e.message}"
            } finally {
                searching = false
            }
        }
    }

    Screen(title = if (demo) "운행 준비 · 데모" else "운행 준비", actionLabel = "설정", onAction = onSettings) {
        DrivingLockBanner(locked)
        Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(32.dp)) {
            // Left: search
            Column(Modifier.weight(1f).fillMaxHeight()) {
                SectionTitle("장소 검색")
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Gap) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text("예: 강남역 카페") },
                        singleLine = true,
                        enabled = !locked,
                        modifier = Modifier.weight(1f),
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 20.sp),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { search() }),
                    )
                    SecondaryButton(if (searching) "검색 중…" else "검색", enabled = !searching && !locked) { search() }
                }
                ErrorText(error)
                LazyColumn(Modifier.weight(1f)) {
                    items(results) { place ->
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clickable(enabled = !locked) {
                                    stops.add(StopDraft(nextId++, place, StopKind.PICKUP, ""))
                                }
                                .padding(vertical = 14.dp),
                        ) {
                            Text(place.name, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                            Text(place.address, fontSize = 16.sp, color = Muted)
                        }
                        HorizontalDivider()
                    }
                }
            }

            // Right: the stops, in visiting order
            Column(Modifier.weight(1f).fillMaxHeight()) {
                SectionTitle("정류지 ${stops.size}곳 (방문 순서)")
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    itemsIndexed(stops, key = { _, s -> s.id }) { i, stop ->
                        StopCard(
                            locked = locked,
                            index = i,
                            count = stops.size,
                            stop = stop,
                            onChange = { stops[i] = it },
                            onMove = { to -> stops.add(to, stops.removeAt(i)) },
                            onRemove = { stops.removeAt(i) },
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                val missingName = stops.any { it.kind == StopKind.WAYPOINT && it.waypointName.isBlank() }
                if (missingName) Text("경유지 이름을 넣어 주세요", color = Muted, fontSize = 16.sp)
                BigButton(
                    if (starting) "운행 만드는 중…" else "운행 시작",
                    enabled = !locked && stops.isNotEmpty() && stops.any { it.kind == StopKind.PICKUP } && !missingName && !starting,
                ) {
                    starting = true
                    error = null
                    scope.launch {
                        try {
                            onStart(stops.toList())
                        } catch (e: Exception) {
                            error = "운행을 만들지 못했어요: ${e.message}"
                        } finally {
                            starting = false
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StopCard(locked: Boolean, index: Int, count: Int, stop: StopDraft, onChange: (StopDraft) -> Unit, onMove: (Int) -> Unit, onRemove: () -> Unit) {
    Column(Modifier.fillMaxWidth().background(Paper, RoundedCornerShape(12.dp)).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${index + 1}", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = Ink)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(stop.place.name, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
                Text(stop.place.address, fontSize = 15.sp, color = Muted)
            }
            TextButton(onClick = { onMove(index - 1) }, enabled = !locked && index > 0) { Text("▲", fontSize = 20.sp) }
            TextButton(onClick = { onMove(index + 1) }, enabled = !locked && index < count - 1) { Text("▼", fontSize = 20.sp) }
            TextButton(onClick = onRemove, enabled = !locked) { Text("삭제", fontSize = 17.sp) }
        }
        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Gap) {
            FilterChip(selected = stop.kind == StopKind.PICKUP, onClick = { onChange(stop.copy(kind = StopKind.PICKUP)) }, enabled = !locked, label = { Text("픽업", fontSize = 17.sp) })
            FilterChip(
                selected = stop.kind == StopKind.WAYPOINT,
                onClick = { onChange(stop.copy(kind = StopKind.WAYPOINT, waypointName = stop.waypointName.ifBlank { stop.place.name.take(MAX_LABEL_LENGTH) })) },
                enabled = !locked,
                label = { Text("경유지", fontSize = 17.sp) },
            )
            if (stop.kind == StopKind.WAYPOINT) {
                OutlinedTextField(
                    value = stop.waypointName,
                    onValueChange = { onChange(stop.copy(waypointName = it.take(MAX_LABEL_LENGTH))) },
                    placeholder = { Text("이름 (예: 카페)") },
                    singleLine = true,
                    enabled = !locked,
                    modifier = Modifier.weight(1f),
                )
            } else {
                // Pickups carry no name: the pin shows only the order
                Text("탑승자 이름은 넣지 않아요", color = Muted, fontSize = 15.sp)
            }
        }
    }
}
