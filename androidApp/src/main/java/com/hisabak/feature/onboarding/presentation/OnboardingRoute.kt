package com.hisabak.feature.onboarding.presentation

import androidx.compose.runtime.Composable
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun OnboardingRoute(
    viewModel: OnboardingViewModel = koinViewModel(),
) {
    OnboardingScreen(onFinish = viewModel::complete)
}
