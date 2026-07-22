package com.hisabak.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hisabak.ui.theme.HisabakTheme
import com.hisabak.ui.theme.HisabakType
import com.hisabak.ui.theme.Sizing

@Composable
fun Avatar(
    name: String,
    modifier: Modifier = Modifier,
    size: Dp = Sizing.avatar,
    src: Any? = null,
) {
    val initials = name
        .trim()
        .split("\\s+".toRegex())
        .filter { it.isNotEmpty() }
        .take(2)
        .joinToString("") { it.first().uppercase() }
        .ifEmpty { "?" }

    val bg = HisabakTheme.colors.incomeSoft
    val fg = HisabakTheme.colors.income
    val border = MaterialTheme.colorScheme.outlineVariant

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(bg)
            .border(1.dp, border, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initials,
            style = HisabakType.amount.copy(fontSize = (size.value * 0.38f).sp),
            color = fg,
        )
    }
}
