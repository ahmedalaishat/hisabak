package com.hisabak.feature.onboarding.presentation

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.hisabak.BuildConfig
import com.hisabak.R
import com.hisabak.ui.components.hisabakClickable
import com.hisabak.ui.theme.Motion
import com.hisabak.ui.theme.PillShape
import com.hisabak.ui.theme.Spacing
import kotlinx.coroutines.launch

private const val PAGE_COUNT = 6

@Composable
fun OnboardingScreen(onFinish: () -> Unit) {
    val pagerState = rememberPagerState(pageCount = { PAGE_COUNT })
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // The final CTA primes SMS auto-capture (the signature feature), then finishes regardless.
    // In the SMS-free (Play) build there's no RECEIVE_SMS to request, so it just finishes.
    val smsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { onFinish() }

    fun finishWithSmsPrimer() {
        if (!BuildConfig.SMS_AUTO_CAPTURE) {
            onFinish()
            return
        }
        val granted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.RECEIVE_SMS,
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) onFinish() else smsLauncher.launch(Manifest.permission.RECEIVE_SMS)
    }

    val isLast = pagerState.currentPage == PAGE_COUNT - 1

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .systemBarsPadding(),
    ) {
        // Skip
        Box(Modifier.fillMaxWidth().height(48.dp).padding(horizontal = Spacing.s3)) {
            if (!isLast) {
                Text(
                    text = stringResource(R.string.onboarding_skip),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .clip(MaterialTheme.shapes.medium)
                        .hisabakClickable { scope.launch { pagerState.animateScrollToPage(PAGE_COUNT - 1) } }
                        .padding(horizontal = Spacing.s4, vertical = Spacing.s3),
                )
            }
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
        ) { page ->
            val offset = (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
            val active = pagerState.currentPage == page
            when (page) {
                0 -> WelcomePage(active, offset)
                1 -> SmsCapturePage(active, offset)
                2 -> PrivacyPage(active, offset)
                3 -> BudgetsPage(active, offset)
                4 -> InsightsPage(active, offset)
                else -> GetStartedPage(active, offset)
            }
        }

        // Footer: progress dots + primary CTA
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.s5, vertical = Spacing.s5),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            PageDots(current = pagerState.currentPage, count = PAGE_COUNT)
            Spacer(Modifier.height(Spacing.s5))
            Button(
                onClick = {
                    if (isLast) finishWithSmsPrimer()
                    else scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = PillShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            ) {
                Text(
                    text = stringResource(if (isLast) R.string.onboarding_get_started else R.string.onboarding_next),
                    style = MaterialTheme.typography.titleSmall,
                )
            }
        }
    }
}

@Composable
private fun PageDots(current: Int, count: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s2)) {
        repeat(count) { i ->
            val selected = i == current
            val width by animateDpAsState(
                targetValue = if (selected) 22.dp else 7.dp,
                animationSpec = androidx.compose.animation.core.tween(Motion.Duration.Slow, easing = Motion.Easing.Standard),
                label = "dotWidth",
            )
            val color by animateColorAsState(
                targetValue = if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.outlineVariant,
                label = "dotColor",
            )
            Box(
                Modifier
                    .height(7.dp)
                    .width(width)
                    .clip(CircleShape)
                    .background(color),
            )
        }
    }
}
