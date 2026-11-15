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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.choi.nawa.data.LatLng
import com.choi.nawa.data.MAX_LABEL_LENGTH
import com.choi.nawa.data.Place
import com.choi.nawa.data.StopDraft
import com.choi.nawa.data.StopKind
import com.choi.nawa.data.TripApi
import com.choi.nawa.ui.theme.Palette
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
    val focus = LocalFocusManager.current
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Place>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var starting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val stops = remember { mutableStateListOf<StopDraft>() }
    var nextId by remember { mutableStateOf(1L) }

    fun search() {
        if (query.isBlank() || searching || locked) return
        focus.clearFocus() // close the keyboard so the results and stops are visible
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
        Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            // Left: search
            Card(Modifier.weight(1f).fillMaxHeight()) {
                SectionTitle("장소 검색", Modifier.padding(top = 0.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Gap) {
                    NawaTextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = "예: 강남역 카페",
                        enabled = !locked,
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { search() }),
                    )
                    PrimaryButton(if (searching) "검색 중…" else "검색", enabled = !searching && !locked) { search() }
                }
                ErrorText(error)
                LazyColumn(Modifier.weight(1f).padding(top = 8.dp)) {
                    items(results) { place ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = TouchHeight)
                                .clickable(enabled = !locked) {
                                    stops.add(StopDraft(nextId++, place, StopKind.PICKUP, ""))
                                }
                                .padding(vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(place.name, style = MaterialTheme.typography.titleMedium, color = Palette.Ink)
                                Text(place.address, style = MaterialTheme.typography.bodyMedium, color = Palette.Muted)
                            }
                            Text("+ 추가", style = MaterialTheme.typography.titleMedium, color = if (locked) Palette.Line else Palette.Route)
                        }
                        HorizontalDivider(color = Palette.Line)
                    }
                }
            }

            // Right: the stops, in visiting order
            Card(Modifier.weight(1f).fillMaxHeight()) {
                SectionTitle("정류지 ${stops.size}곳 (방문 순서)", Modifier.padding(top = 0.dp))
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
                if (missingName) Text("경유지 이름을 넣어 주세요", Modifier.padding(bottom = 8.dp), style = MaterialTheme.typography.bodyLarge, color = Palette.Danger)
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
    Column(Modifier.fillMaxWidth().background(Palette.Ground, RoundedCornerShape(16.dp)).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StopNumber(index + 1)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(stop.place.name, style = MaterialTheme.typography.titleMedium, color = Palette.Ink)
                Text(stop.place.address, style = MaterialTheme.typography.bodyMedium, color = Palette.Muted, maxLines = 1)
            }
            GhostButton("▲", enabled = !locked && index > 0) { onMove(index - 1) }
            GhostButton("▼", enabled = !locked && index < count - 1) { onMove(index + 1) }
            GhostButton("삭제", enabled = !locked, onClick = onRemove)
        }
        Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Gap) {
            ChoiceChip("픽업", selected = stop.kind == StopKind.PICKUP, enabled = !locked) { onChange(stop.copy(kind = StopKind.PICKUP)) }
            ChoiceChip("경유지", selected = stop.kind == StopKind.WAYPOINT, enabled = !locked) {
                onChange(stop.copy(kind = StopKind.WAYPOINT, waypointName = stop.waypointName.ifBlank { stop.place.name.take(MAX_LABEL_LENGTH) }))
            }
            if (stop.kind == StopKind.WAYPOINT) {
                NawaTextField(
                    value = stop.waypointName,
                    onValueChange = { onChange(stop.copy(waypointName = it.take(MAX_LABEL_LENGTH))) },
                    placeholder = "이름 (예: 카페)",
                    enabled = !locked,
                    modifier = Modifier.weight(1f),
                )
            } else {
                // Pickups carry no name: the pin shows only the order
                Text("탑승자 이름은 넣지 않아요", style = MaterialTheme.typography.bodyMedium, color = Palette.Muted)
            }
        }
    }
}
