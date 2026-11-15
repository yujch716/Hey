package com.choi.nawa.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val Ink = Color(0xFF1A2433)
val Signal = Color(0xFFFFB020)
val Muted = Color(0xFF6B7684)
val Paper = Color(0xFFF2F4F6)

/** Screen with a title row and an optional action on the right (usually 설정). */
@Composable
fun Screen(
    title: String,
    actionLabel: String? = null,
    onAction: () -> Unit = {},
    backLabel: String? = null,
    onBack: () -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(horizontal = 32.dp, vertical = 20.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (backLabel != null) TextButton(onClick = onBack) { Text(backLabel, fontSize = 18.sp) }
            Text(title, Modifier.weight(1f), fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, color = Ink)
            if (actionLabel != null) TextButton(onClick = onAction) { Text(actionLabel, fontSize = 18.sp) }
        }
        Spacer(Modifier.height(16.dp))
        content()
    }
}

/** The one action that matters on this screen. Large: the driver taps it in the car. */
@Composable
fun BigButton(label: String, modifier: Modifier = Modifier, enabled: Boolean = true, color: Color = Ink, onClick: () -> Unit) =
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().height(72.dp),
        colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = if (color == Signal) Ink else Color.White),
    ) { Text(label, fontSize = 24.sp, fontWeight = FontWeight.Bold) }

@Composable
fun SecondaryButton(label: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) =
    OutlinedButton(onClick = onClick, enabled = enabled, modifier = modifier.height(56.dp)) { Text(label, fontSize = 18.sp) }

@Composable
fun SectionTitle(text: String) =
    Text(text, Modifier.padding(top = 12.dp, bottom = 8.dp), fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Muted)

@Composable
fun ErrorText(message: String?) {
    if (message != null) Text(message, Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.error, fontSize = 16.sp)
}

val Gap = Arrangement.spacedBy(12.dp)
