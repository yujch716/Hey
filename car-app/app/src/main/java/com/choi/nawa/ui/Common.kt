package com.choi.nawa.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.choi.nawa.ui.theme.Palette

/** Smallest touch target in the car: buttons, chips, fields, list rows */
val TouchHeight = 76.dp
val CardShape = RoundedCornerShape(20.dp)
val Gap = Arrangement.spacedBy(16.dp)

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
    Column(Modifier.fillMaxSize().background(Palette.Ground).padding(horizontal = 32.dp, vertical = 16.dp)) {
        Row(Modifier.fillMaxWidth().heightIn(min = TouchHeight), verticalAlignment = Alignment.CenterVertically) {
            if (backLabel != null) GhostButton(backLabel, onClick = onBack)
            Text(title, Modifier.weight(1f).padding(start = if (backLabel != null) 8.dp else 0.dp), style = MaterialTheme.typography.headlineMedium, color = Palette.Ink)
            if (actionLabel != null) GhostButton(actionLabel, onClick = onAction)
        }
        Spacer(Modifier.height(16.dp))
        content()
    }
}

/** White rounded panel on the grey ground */
@Composable
fun Card(modifier: Modifier = Modifier, padding: PaddingValues = PaddingValues(24.dp), content: @Composable ColumnScope.() -> Unit) =
    Column(modifier.background(Palette.Card, CardShape).padding(padding), content = content)

/** The one action that matters on this screen: yellow, full width. The driver taps it in the car. */
@Composable
fun BigButton(label: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) =
    PrimaryButton(label, modifier.fillMaxWidth().height(88.dp), enabled, onClick)

@Composable
fun PrimaryButton(label: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) =
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = TouchHeight),
        shape = RoundedCornerShape(18.dp),
        contentPadding = PaddingValues(horizontal = 32.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Palette.Signal,
            contentColor = Palette.Ink,
            disabledContainerColor = Palette.Line,
            disabledContentColor = Palette.Muted,
        ),
    ) { Text(label, style = MaterialTheme.typography.titleLarge) }

@Composable
fun SecondaryButton(label: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) =
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = TouchHeight),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(2.dp, Palette.Line),
        contentPadding = PaddingValues(horizontal = 24.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = Palette.Card,
            contentColor = Palette.Ink,
            disabledContainerColor = Palette.Ground,
            disabledContentColor = Palette.Muted,
        ),
    ) { Text(label, style = MaterialTheme.typography.titleMedium) }

/** Text-only button for the title row (← 뒤로, 설정) and small row actions, still a full touch target */
@Composable
fun GhostButton(label: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) =
    Box(
        modifier
            .heightIn(min = TouchHeight)
            .widthIn(min = TouchHeight)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.titleMedium, color = if (enabled) Palette.Route else Palette.Line)
    }

/** One choice of a few (픽업/경유지, 세단/SUV, 1x/5x) */
@Composable
fun ChoiceChip(label: String, selected: Boolean, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) =
    Box(
        modifier
            .heightIn(min = TouchHeight)
            .widthIn(min = 120.dp)
            .background(if (selected) Palette.SignalSoft else Palette.Card, RoundedCornerShape(18.dp))
            .border(if (selected) 3.dp else 2.dp, if (selected) Palette.Signal else Palette.Line, RoundedCornerShape(18.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (enabled) Palette.Ink else Palette.Muted,
        )
    }

@Composable
fun NawaTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    trailingIcon: (@Composable () -> Unit)? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
) = OutlinedTextField(
    value = value,
    onValueChange = onValueChange,
    modifier = modifier.heightIn(min = TouchHeight),
    enabled = enabled,
    readOnly = readOnly,
    singleLine = true,
    textStyle = MaterialTheme.typography.bodyLarge,
    placeholder = { Text(placeholder, style = MaterialTheme.typography.bodyLarge) },
    trailingIcon = trailingIcon,
    keyboardOptions = keyboardOptions,
    keyboardActions = keyboardActions,
    shape = RoundedCornerShape(16.dp),
    colors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = Palette.Route,
        unfocusedBorderColor = Palette.Line,
        disabledBorderColor = Palette.Line,
        cursorColor = Palette.Route,
        focusedContainerColor = Palette.Card,
        unfocusedContainerColor = Palette.Card,
        disabledContainerColor = Palette.Ground,
        focusedTextColor = Palette.Ink,
        unfocusedTextColor = Palette.Ink,
        disabledTextColor = Palette.Muted,
        focusedPlaceholderColor = Palette.Muted,
        unfocusedPlaceholderColor = Palette.Muted,
        disabledPlaceholderColor = Palette.Muted,
    ),
)

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) =
    Text(text, modifier.padding(top = 12.dp, bottom = 10.dp), style = MaterialTheme.typography.titleMedium, color = Palette.Muted)

@Composable
fun ErrorText(message: String?) {
    if (message != null) Text(message, Modifier.padding(vertical = 8.dp), style = MaterialTheme.typography.bodyLarge, color = Palette.Danger)
}

/** Shown on input screens while the gear is not P: planning happens only while stopped. */
@Composable
fun DrivingLockBanner(locked: Boolean) {
    if (!locked) return
    Text(
        "정차 중에만 입력할 수 있어요",
        Modifier.fillMaxWidth().padding(bottom = 16.dp).background(Palette.Signal, RoundedCornerShape(16.dp)).padding(20.dp),
        style = MaterialTheme.typography.titleLarge,
        color = Palette.Ink,
    )
}

/** Fills the whole screen behind a centered message (e.g. the map's error state) */
@Composable
fun CenteredMessage(text: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit = {}) =
    Column(
        modifier.fillMaxSize().background(Palette.Ground),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text, style = MaterialTheme.typography.titleLarge, color = Palette.Ink)
        content()
    }
