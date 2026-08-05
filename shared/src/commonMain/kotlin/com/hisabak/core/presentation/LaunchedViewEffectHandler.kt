package com.hisabak.core.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.Flow

@Composable
fun <EFFECT> LaunchedViewEffectHandler(
    effectFlow: Flow<EFFECT?>,
    onConsumeEffect: () -> Unit,
    onEffect: suspend (EFFECT) -> Unit,
) {
    val effect by effectFlow.collectAsStateWithLifecycle(null)
    val currentOnEffect by rememberUpdatedState(onEffect)
    val currentOnConsumeEffect by rememberUpdatedState(onConsumeEffect)

    LaunchedEffect(effect) {
        effect?.let {
            currentOnEffect(it)
            currentOnConsumeEffect()
        }
    }
}
