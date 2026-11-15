package com.choi.nawa.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.toColorInt
import com.choi.nawa.data.BodyType
import com.choi.nawa.data.VehicleCatalog
import com.choi.nawa.data.VehicleProfile

/** First run (and 설정 > 차량 정보): what the passenger looks for at the curb. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun VehicleScreen(initial: VehicleProfile?, onSave: (VehicleProfile) -> Unit, onCancel: (() -> Unit)?) {
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
        Column(Modifier.widthIn(max = 760.dp).verticalScroll(rememberScrollState())) {
            Text("탑승자가 길에서 이 차를 찾을 때 보는 정보예요", fontSize = 18.sp, color = Muted)

            SectionTitle("차종")
            ExposedDropdownMenuBox(expanded = menuOpen, onExpandedChange = { menuOpen = it }) {
                OutlinedTextField(
                    value = model,
                    onValueChange = {},
                    readOnly = true,
                    placeholder = { Text("차종을 고르세요") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = menuOpen) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 20.sp),
                )
                ExposedDropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    for (m in VehicleCatalog.models) {
                        DropdownMenuItem(
                            text = { Text("${m.brand} ${m.name}", fontSize = 18.sp) },
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
                for ((value, label) in listOf(BodyType.SEDAN to "세단", BodyType.SUV to "SUV")) {
                    FilterChip(selected = body == value, onClick = { body = value }, label = { Text(label, fontSize = 18.sp) })
                }
            }

            SectionTitle("색상")
            FlowRow(horizontalArrangement = Gap, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for (c in VehicleCatalog.colors) {
                    val selected = c == color
                    Row(
                        Modifier
                            .border(BorderStroke(if (selected) 3.dp else 1.dp, if (selected) Ink else Color(0x33000000)), RoundedCornerShape(24.dp))
                            .clickable { color = c }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Spacer(Modifier.size(22.dp).background(Color(c.hex.toColorInt()), CircleShape).border(1.dp, Ink, CircleShape))
                        Text(c.name, Modifier.padding(start = 8.dp), fontSize = 18.sp)
                    }
                }
            }

            SectionTitle("차량번호")
            OutlinedTextField(
                value = plate,
                onValueChange = { plate = it.take(12) },
                placeholder = { Text("예: 123가 4567") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 20.sp),
            )

            Spacer(Modifier.height(24.dp))
            BigButton("저장", enabled = model.isNotBlank() && plate.isNotBlank()) {
                onSave(VehicleProfile(model = model, colorName = color.name, colorHex = color.hex, bodyType = body, plate = plate.trim()))
            }
        }
    }
}
