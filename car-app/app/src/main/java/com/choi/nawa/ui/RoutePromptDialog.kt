package com.choi.nawa.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.choi.nawa.port.RouteRequest
import com.choi.nawa.ui.theme.Palette

/**
 * "내장 내비로 길안내를 시작할까요?" in the middle of the app, each time the trip moves to a new stop.
 * 예 routes the built-in navi; 아니오 (or back) keeps the driver in the app.
 */
@Composable
fun RoutePromptDialog(request: RouteRequest, address: String, onYes: () -> Unit, onNo: () -> Unit) {
    Dialog(onDismissRequest = onNo, properties = DialogProperties(dismissOnClickOutside = false, usePlatformDefaultWidth = false)) {
        Card(Modifier.width(760.dp), padding = PaddingValues(36.dp)) {
            Text(request.name, style = MaterialTheme.typography.headlineMedium, color = Palette.Ink)
            if (address.isNotBlank()) {
                Text(address, Modifier.padding(top = 4.dp), style = MaterialTheme.typography.labelLarge, color = Palette.Muted)
            }
            Text(
                "내장 내비로 길안내를 시작할까요?",
                Modifier.padding(top = 12.dp),
                style = MaterialTheme.typography.bodyLarge,
                color = Palette.Muted,
            )
            Spacer(Modifier.height(32.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                SecondaryButton("아니오", Modifier.weight(1f).height(88.dp), onClick = onNo)
                PrimaryButton("예", Modifier.weight(1f).height(88.dp), onClick = onYes)
            }
        }
    }
}
