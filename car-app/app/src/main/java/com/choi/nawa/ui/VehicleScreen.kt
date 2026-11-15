package com.choi.nawa.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.graphics.toColorInt
import com.choi.nawa.data.BodyType
import com.choi.nawa.data.VehicleCatalog
import com.choi.nawa.data.VehicleProfile
import com.choi.nawa.ui.theme.Palette

/** First run (and 설정 > 차량 정보): what the passenger looks for at the curb. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun VehicleScreen(initial: VehicleProfile?, onSave: (VehicleProfile) -> Unit, onCancel: (() -> Unit)?, locked: Boolean = false) {
    var model by remember { mutableStateOf(initial?.model ?: "") }
    var body by remember { mutableStateOf(initial?.bodyType ?: BodyType.SEDAN) }
    var color by remember { mutableStateOf(VehicleCatalog.colors.find { it.name == initial?.colorName } ?: VehicleCatalog.colors.first()) }
    var plate by remember { mutableStateOf(initial?.plate ?: "") }
    var menuOpen by remember { mutableStateOf(false) }

    Screen(
        title = if (initial == null) "차량 등록" else "차량 정보",
        backLabel = onCancel?.let { "← 뒤로" },
        onBack = { onCancel?.invoke() },
    ) {
        Card(Modifier.widthIn(max = 980.dp).weight(1f, fill = false).verticalScroll(rememberScrollState())) {
            DrivingLockBanner(locked)
            Text("탑승자가 길에서 이 차를 찾을 때 보는 정보예요", style = MaterialTheme.typography.bodyLarge, color = Palette.Muted)

            SectionTitle("차종")
            ExposedDropdownMenuBox(expanded = menuOpen && !locked, onExpandedChange = { menuOpen = it && !locked }) {
                NawaTextField(
                    value = model,
                    onValueChange = {},
                    readOnly = true,
                    enabled = !locked,
                    placeholder = "차종을 고르세요",
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = menuOpen) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                )
                ExposedDropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }, containerColor = Palette.Card) {
                    for (m in VehicleCatalog.models) {
                        DropdownMenuItem(
                            text = { Text(m.name, style = MaterialTheme.typography.bodyLarge, color = Palette.Ink) },
                            modifier = Modifier.heightIn(min = TouchHeight),
                            onClick = {
                                model = m.name
                                body = m.body
                                menuOpen = false
                            },
                        )
                    }
                }
            }

            SectionTitle("차체")
            Row(horizontalArrangement = Gap) {
                for ((value, label) in listOf(BodyType.SEDAN to "세단", BodyType.SUV to "SUV", BodyType.TRUCK to "트럭")) {
                    ChoiceChip(label, selected = body == value, enabled = !locked) { body = value }
                }
            }

            SectionTitle("색상")
            FlowRow(horizontalArrangement = Gap, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                for (c in VehicleCatalog.colors) {
                    val selected = c == color
                    val shape = RoundedCornerShape(18.dp)
                    Row(
                        Modifier
                            .heightIn(min = TouchHeight)
                            .background(if (selected) Palette.SignalSoft else Palette.Card, shape)
                            .border(if (selected) 3.dp else 2.dp, if (selected) Palette.Signal else Palette.Line, shape)
                            .clickable(enabled = !locked) { color = c }
                            .padding(horizontal = 20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // The swatch is the car's paint (catalog data), not a UI color
                        Spacer(Modifier.size(32.dp).background(Color(c.hex.toColorInt()), CircleShape).border(2.dp, Palette.Line, CircleShape))
                        Text(
                            c.name,
                            Modifier.padding(start = 12.dp),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            color = Palette.Ink,
                        )
                    }
                }
            }

            SectionTitle("차량번호")
            NawaTextField(
                value = plate,
                onValueChange = { plate = it.take(12) },
                placeholder = "예: 123가 4567",
                enabled = !locked,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(28.dp))
            BigButton("저장", enabled = !locked && model.isNotBlank() && plate.isNotBlank()) {
                onSave(VehicleProfile(model = model, colorName = color.name, colorHex = color.hex, bodyType = body, plate = plate.trim()))
            }
        }
    }
}
