// SPDX-License-Identifier: MIT
package com.fiveseven.tabataxtreme.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    // Loud, punchy dark scheme; tweak later if you want dynamic color.
    val scheme = darkColorScheme()
    MaterialTheme(colorScheme = scheme, content = content)
}
