package com.hisabak.feature.onboarding.presentation

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.hisabak.BuildConfig
import org.koin.compose.viewmodel.koinViewModel

/** Android glue for the shared [OnboardingScreen]: the final CTA primes SMS auto-capture (the
 *  signature feature) via the platform permission launcher, then finishes regardless. In the
 *  SMS-free (Play) build there's no RECEIVE_SMS to request, so it just finishes. */
@Composable
fun OnboardingRoute(
    viewModel: OnboardingViewModel = koinViewModel(),
) {
    val context = LocalContext.current
    val smsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { viewModel.complete() }

    OnboardingScreen(
        onFinish = {
            val granted = !BuildConfig.SMS_AUTO_CAPTURE ||
                ContextCompat.checkSelfPermission(
                    context, Manifest.permission.RECEIVE_SMS,
                ) == PackageManager.PERMISSION_GRANTED
            if (granted) viewModel.complete() else smsLauncher.launch(Manifest.permission.RECEIVE_SMS)
        },
    )
}
