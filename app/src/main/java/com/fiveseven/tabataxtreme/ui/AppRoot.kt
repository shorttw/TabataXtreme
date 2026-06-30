// SPDX-License-Identifier: MIT
package com.fiveseven.tabataxtreme.ui

import androidx.compose.foundation.background
import android.widget.Toast
import androidx.activity.compose.BackHandler
import kotlinx.coroutines.delay
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fiveseven.tabataxtreme.TabataViewModel
import com.fiveseven.tabataxtreme.model.PhaseType
import kotlin.math.max
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.compose.animation.AnimatedContent
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.sp
import com.fiveseven.tabataxtreme.BuildConfig
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.isActive
import androidx.compose.foundation.clickable


@Composable
fun AppRoot(vm: TabataViewModel) {
    val ui by vm.ui.collectAsState()
    val tick = ui.tick
    val phase = tick?.phase
    val running = tick?.isRunning == true

    KeepScreenOn(keepOn = running)
    DoubleBackToExit()

    // If tick is null initially (before first engine tick), show a safe placeholder
    val phaseType = phase?.type ?: PhaseType.PREPARE
    val phaseLabel = phase?.label ?: "Ready"
    val setRoundText = if (phase != null && phase.type != PhaseType.BETWEEN_SETS && phase.type != PhaseType.DONE) {
        "Set ${phase.setIndex} • Round ${phase.roundIndex}"
    } else if (phase?.type == PhaseType.BETWEEN_SETS) "Set ${phase.setIndex } of ${ui.config.sets} complete."
    else ""

    val totalMs = tick?.nominalTotalMs ?: 1L
    val remainingMs = tick?.nominalRemainingMs ?: 0L
    val progress =
        if (totalMs <= 0L) 1f else (1f - (remainingMs.toFloat() / totalMs.toFloat())).coerceIn(
            0f,
            1f
        )

    val (bg, accent) = phaseColors(phaseType)

    Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .background(bg)
                .padding(WindowInsets.safeDrawing.asPaddingValues())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            HeaderCard(
                phaseLabel = phaseLabel,
                setRoundText = setRoundText,
                timeText = formatMsAsClock(remainingMs),
                accent = accent,
                progress = progress,
            )

            ControlRow(
                running = running,
                onStartPause = vm::startPauseToggle,
                onReset = vm::reset,
                onSkip = vm::skip,
                accent = accent
            )

            EditorCard(
                modifier = Modifier.weight(1f),
                ui = ui,
                running = running,
                onConfigChange = vm::updateConfig,
                onPresetNameChange = vm::onPresetNameChange,
                onSavePreset = vm::savePreset,
                onLoadPreset = vm::loadPreset,
                onDeletePreset = vm::deletePreset,
                accent = accent
            )
        }
    }
}

@Composable
fun HeaderCard(
    phaseLabel: String,
    setRoundText: String,
    timeText: String,
    accent: androidx.compose.ui.graphics.Color,
    progress: Float,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = accent.copy(alpha = 0.18f)),
        shape = RoundedCornerShape(22.dp)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            AnimatedContent(
                targetState = phaseLabel,
                label = "phaseLabel"
            ) { labelText ->
                Text(
                    text = labelText,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Black
                )
            }
            if (setRoundText.isNotBlank()) {
                Text(
                    setRoundText,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Text(
                text = timeText,
                style = MaterialTheme.typography.displayLarge.copy(
                    fontFeatureSettings = "tnum",
                    letterSpacing = 1.sp
                ),
                fontWeight = FontWeight.Black
            )

            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(RoundedCornerShape(999.dp)),
            )
            Text("Tabata Xtreme, v" + BuildConfig.VERSION_NAME + " " + BuildConfig.BUILD_TYPE, style = MaterialTheme.typography.bodySmall)
            //  Text("Jason Factor: ${"%.2f".format(multiplier)}x (virtual sec / real sec)", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
fun ControlRow(
    running: Boolean,
    onStartPause: () -> Unit,
    onReset: () -> Unit,
    onSkip: () -> Unit,
    accent: androidx.compose.ui.graphics.Color
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {

        Button(
            onClick = onStartPause,
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.buttonColors(containerColor = accent.copy(alpha = 0.75f))
        ) {
            Text(if (running) "Pause" else "Start")
        }

        OutlinedButton(onClick = onReset) {
            Text("Reset")
        }

        OutlinedButton(onClick = onSkip) {
            Text("Skip")
        }
    }
}

@Composable
fun EditorCard(
    modifier: Modifier,
    ui: com.fiveseven.tabataxtreme.UiState,
    running: Boolean,
    onConfigChange: ((com.fiveseven.tabataxtreme.model.WorkoutConfig) -> com.fiveseven.tabataxtreme.model.WorkoutConfig) -> Unit,
    onPresetNameChange: (String) -> Unit,
    onSavePreset: () -> Unit,
    onLoadPreset: (com.fiveseven.tabataxtreme.model.Preset) -> Unit,
    onDeletePreset: (String) -> Unit,
    accent: androidx.compose.ui.graphics.Color
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .alpha(if (running) 0.55f else 1f),
        colors = CardDefaults.cardColors(
            containerColor = if (running) {
                MaterialTheme.colorScheme.surfaceVariant
            } else {
                accent.copy(alpha = 0.12f)
            }
        ),
        shape = RoundedCornerShape(22.dp)
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Text(
                    "Workout Editor",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                if (running) {
                    Text(
                        "Workout editor is locked while the timer is running",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            item {
                NumberRow(
                    label = "Prepare (sec)",
                    value = ui.config.prepareSec,
                    enabled = !running,
                    onValueChange = { v -> onConfigChange { it.copy(prepareSec = v) } }
                )
            }

            item {
                NumberRow(
                    label = "Work (sec)",
                    value = ui.config.workSec,
                    enabled = !running,
                    onValueChange = { v -> onConfigChange { it.copy(workSec = v) } }
                )
            }

            item {
                NumberRow(
                    label = "Rest (sec)",
                    value = ui.config.restSec,
                    enabled = !running,
                    onValueChange = { v -> onConfigChange { it.copy(restSec = v) } }
                )
            }

            item {
                NumberRow(
                    label = "Rounds / set",
                    value = ui.config.roundsPerSet,
                    enabled = !running,
                    onValueChange = { v -> onConfigChange { it.copy(roundsPerSet = v) } }
                )
            }

            item {
                NumberRow(
                    label = "Sets",
                    value = ui.config.sets,
                    enabled = !running,
                    onValueChange = { v -> onConfigChange { it.copy(sets = v) } }
                )
            }

            item {
                NumberRow(
                    label = "Rest between sets (sec)",
                    value = ui.config.restBetweenSetsSec,
                    enabled = !running,
                    onValueChange = { v -> onConfigChange { it.copy(restBetweenSetsSec = v) } }
                )
            }

            item {
                Text(
                    "The Xtreme Factor",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "Lower = slower real time, Higher = faster real time",
                    style = MaterialTheme.typography.bodySmall
                )
                Slider(
                    value = ui.config.timeMultiplier,
                    onValueChange = { v -> onConfigChange { it.copy(timeMultiplier = v) } },
                    valueRange = 0.7f..1.3f,
                    steps = 0,
                    enabled = !running,
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("0.70x")
                    val isDefault = ui.config.timeMultiplier == 1.0f
                    Text(
                        text = if (isDefault) "1.00x" else "${"%.2f".format(ui.config.timeMultiplier)}x (tap)",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable(enabled = !running) {
                            onConfigChange { it.copy(timeMultiplier = 1.0f) }
                        }
                    )
                    Text("1.30x")
                }
            }

            item {
                HorizontalDivider(Modifier, DividerDefaults.Thickness, DividerDefaults.color)
                Text(
                    "Presets",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = ui.editPresetName,
                        onValueChange = onPresetNameChange,
                        label = { Text("Preset name") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        enabled = !running,
                    )
                    Button(
                        onClick = onSavePreset,
                        enabled = !running
                    ) { Text("Save") }
                }
            }

            if (ui.presets.isEmpty()) {
                item {
                    Text(
                        "No presets yet. Save one above.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            } else {
                items(ui.presets, key = { it.name.lowercase() }) { preset ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface.copy(
                                alpha = 0.65f
                            )
                        ),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(preset.name, fontWeight = FontWeight.Bold)
                                Text(
                                    "Prep ${preset.config.prepareSec}s • Work ${preset.config.workSec}s / Rest ${preset.config.restSec}s • " +
                                            "${preset.config.roundsPerSet} rounds • ${preset.config.sets} sets • " +
                                            "Between ${preset.config.restBetweenSetsSec}s • ${
                                                "%.2f".format(
                                                    preset.config.timeMultiplier
                                                )
                                            }x",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            OutlinedButton(
                                onClick = { onLoadPreset(preset) },
                                enabled = !running
                            ) { Text("Load") }

                            OutlinedButton(
                                onClick = { onDeletePreset(preset.name) },
                                enabled = !running
                            ) { Text("Del") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun NumberRow(
    label: String,
    value: Int,
    enabled: Boolean,
    onValueChange: (Int) -> Unit
) {
    val contentColor = if (enabled) {
        LocalContentColor.current
    } else {
        LocalContentColor.current.copy(alpha = 0.4f)
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = label,
            color = contentColor
        )

        Stepper(
            value = value,
            enabled = enabled,
            onDecrement = { onValueChange(max(0, value - 1)) },
            onIncrement = { onValueChange(value + 1) }
        )
    }
}

@Composable
private fun Stepper(
    value: Int,
    enabled: Boolean,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit
) {
    val contentColor = if (enabled) {
        LocalContentColor.current
    } else {
        LocalContentColor.current.copy(alpha = 0.6f)
    }

    Surface(
        shape = RoundedCornerShape(50),
        tonalElevation = 1.dp,
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            RepeatTextButton(
                text = "–",
                enabled = enabled,
                onClick = onDecrement,
                onRepeat = onDecrement
            )

            Text(
                text = value.toString(),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = contentColor
            )

            RepeatTextButton(
                text = "+",
                enabled = enabled,
                onClick = onIncrement,
                onRepeat = onIncrement
            )
        }
    }
}

@Composable
private fun RepeatTextButton(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit,
    onRepeat: () -> Unit
) {
    var pressed by remember { mutableStateOf(false) }

    val latestOnRepeat by rememberUpdatedState(onRepeat)

    LaunchedEffect(pressed, enabled) {
        if (pressed && enabled) {
            delay(600) // hold time before repeating starts

            var repeatDelay = 200L
            while (pressed && isActive) {
                latestOnRepeat()
                delay(repeatDelay)
                repeatDelay = max(40, repeatDelay - 8)
            }
        }
    }

    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.pointerInput(enabled) {
            if (!enabled) return@pointerInput

            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                pressed = true

                do {
                    val event = awaitPointerEvent()
                } while (event.changes.any { it.pressed })

                pressed = false
            }
        }
    ) {
        Text(text, style = MaterialTheme.typography.titleMedium)
    }
}


fun formatMsAsClock(ms: Long): String {
    val totalSec = (ms / 1000).toInt()
    val m = totalSec / 60
    val s = totalSec % 60
    return "%d:%02d".format(m, s)
}

fun phaseColors(type: PhaseType): Pair<androidx.compose.ui.graphics.Color, androidx.compose.ui.graphics.Color> {
    // Background + accent; intentionally loud.
    return when (type) {
        PhaseType.PREPARE -> androidx.compose.ui.graphics.Color(0xFF102A43) to androidx.compose.ui.graphics.Color(
            0xFF2EC4B6
        )

        PhaseType.REST -> androidx.compose.ui.graphics.Color(0xFF2B0B3F) to androidx.compose.ui.graphics.Color(
            0xFFFF4D6D
        )

        PhaseType.WORK -> androidx.compose.ui.graphics.Color(0xFF083344) to androidx.compose.ui.graphics.Color(
            0xFF3DDC97
        )

        PhaseType.BETWEEN_SETS -> androidx.compose.ui.graphics.Color(0xFF1F2937) to androidx.compose.ui.graphics.Color(
            0xFFFFC857
        )

        PhaseType.DONE -> androidx.compose.ui.graphics.Color(0xFF0B1320) to androidx.compose.ui.graphics.Color(
            0xFF8E9AAF
        )

    }
}
private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
fun KeepScreenOn(keepOn: Boolean) {
    val context = LocalContext.current
    DisposableEffect(keepOn) {
        val activity = context.findActivity()
        val window = activity?.window

        if (keepOn) {
            window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }

        onDispose {
            // Always clear when this composable leaves composition
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
}
@Composable
fun DoubleBackToExit(
    enabled: Boolean = true,
    timeoutMs: Long = 2000L,
    message: String = "Press back again to exit…"
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }

    var armed by remember { mutableStateOf(false) }

    LaunchedEffect(armed) {
        if (armed) {
            delay(timeoutMs)
            armed = false
        }
    }

    BackHandler(enabled = enabled) {
        if (armed) {
            activity?.finish()
        } else {
            armed = true
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }
}