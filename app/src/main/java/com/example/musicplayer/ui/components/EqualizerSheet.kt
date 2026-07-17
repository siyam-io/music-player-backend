package com.example.musicplayer.ui.components

import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.musicplayer.viewmodel.MusicViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EqualizerSheet(
    viewModel: MusicViewModel,
    onDismissRequest: () -> Unit
) {
    val eqEnabled by viewModel.eqEnabled.collectAsState()
    val eqBands by viewModel.eqBands.collectAsState()
    val view = LocalView.current

    val presets = listOf(
        Preset("Flat", listOf(0, 0, 0, 0, 0)),
        Preset("Bass Boost", listOf(10, 6, 0, 0, -2)),
        Preset("Vocal Boost", listOf(-2, 0, 5, 6, 2)),
        Preset("Rock", listOf(6, 3, -2, 2, 5)),
        Preset("Classical", listOf(5, 3, -1, 3, 5))
    )

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        containerColor = Color(0xFF1C1C1E),
        contentColor = Color.White
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 8.dp)
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Equalizer",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                Switch(
                    checked = eqEnabled,
                    onCheckedChange = {
                        triggerHaptic(view)
                        viewModel.toggleEqualizer(it)
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = MaterialTheme.colorScheme.primary
                    )
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Presets Horizontal Row
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(presets) { preset ->
                    val isSelected = eqBands == preset.levels && eqEnabled
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.primary 
                                else Color(0xFF2C2C2E)
                            )
                            .clickable(enabled = eqEnabled) {
                                triggerHaptic(view)
                                for (i in 0..4) {
                                    viewModel.setEqualizerBand(i, preset.levels[i])
                                }
                            }
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(
                            preset.name,
                            color = if (isSelected) Color.White else Color.LightGray,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Frequency Sliders (60Hz, 230Hz, 910Hz, 4kHz, 14kHz)
            val bandLabels = listOf("60 Hz", "230 Hz", "910 Hz", "4 kHz", "14 kHz")
            
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                for (i in 0..4) {
                    val bandLevel = eqBands.getOrElse(i) { 0 }
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = bandLabels[i],
                            color = if (eqEnabled) Color.White else Color.Gray,
                            fontSize = 14.sp,
                            modifier = Modifier.width(60.dp)
                        )

                        Slider(
                            value = bandLevel.toFloat(),
                            onValueChange = { value ->
                                viewModel.setEqualizerBand(i, value.toInt())
                            },
                            valueRange = -15f..15f,
                            enabled = eqEnabled,
                            colors = SliderDefaults.colors(
                                activeTrackColor = MaterialTheme.colorScheme.primary,
                                inactiveTrackColor = Color.DarkGray,
                                thumbColor = Color.White
                            ),
                            modifier = Modifier.weight(1f)
                        )

                        Text(
                            text = String.format("%+d dB", bandLevel),
                            color = if (eqEnabled) Color.LightGray else Color.Gray,
                            fontSize = 12.sp,
                            modifier = Modifier.width(50.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.End
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

data class Preset(val name: String, val levels: List<Int>)

private fun triggerHaptic(view: View) {
    try {
        view.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
    } catch (e: Exception) {
        // Fallback
    }
}
