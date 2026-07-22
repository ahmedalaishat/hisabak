package com.hisabak

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.ComposeUIViewController
import com.hisabak.ui.theme.HisabakTheme
import platform.UIKit.UIViewController

/** Xcode entry point for the Phase B `iosApp`. Placeholder root — a real multiplatform nav host
 *  (JB Navigation 3) replaces it in Phase B. TODO(Phase-B) */
fun MainViewController(): UIViewController = ComposeUIViewController {
    HisabakTheme {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = "Hisabak iOS: TODO(Phase-B) nav",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}
