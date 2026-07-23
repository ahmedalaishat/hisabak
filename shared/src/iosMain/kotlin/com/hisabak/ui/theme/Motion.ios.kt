package com.hisabak.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import platform.UIKit.UIAccessibilityIsReduceMotionEnabled

/** Honors the iOS Reduce Motion accessibility setting (Android reads the animator duration
 *  scale; iOS only exposes an on/off switch, so it maps to 0 or full scale). */
@Composable
internal actual fun rememberAnimatorDurationScale(): Float =
    remember { if (UIAccessibilityIsReduceMotionEnabled()) 0f else 1f }
