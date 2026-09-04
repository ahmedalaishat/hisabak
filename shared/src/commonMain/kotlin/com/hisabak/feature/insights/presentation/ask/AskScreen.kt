package com.hisabak.feature.insights.presentation.ask

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hisabak.feature.insights.domain.ai.ASK_DAILY_ALLOWANCE
import com.hisabak.feature.insights.domain.ai.AskRole
import com.hisabak.feature.insights.domain.ai.AskTurn
import com.hisabak.feature.insights.domain.ai.MAX_QUESTION_LENGTH
import com.hisabak.feature.insights.domain.ai.SuggestedQuestion
import com.hisabak.shared.resources.Res
import com.hisabak.shared.resources.insights_ask_empty_body
import com.hisabak.shared.resources.insights_ask_empty_title
import com.hisabak.shared.resources.insights_ask_footer
import com.hisabak.shared.resources.insights_ask_none_left
import com.hisabak.shared.resources.insights_ask_placeholder
import com.hisabak.shared.resources.insights_ask_q_focus
import com.hisabak.shared.resources.insights_ask_q_savings
import com.hisabak.shared.resources.insights_ask_q_stay_under
import com.hisabak.shared.resources.insights_ask_q_uncategorized
import com.hisabak.shared.resources.insights_ask_q_where_to_cut
import com.hisabak.shared.resources.insights_ask_q_why_up
import com.hisabak.shared.resources.insights_ask_remaining
import com.hisabak.shared.resources.insights_ask_send
import com.hisabak.shared.resources.insights_ask_suggestions
import com.hisabak.shared.resources.insights_ask_thinking
import com.hisabak.shared.resources.insights_ask_unavailable
import com.hisabak.ui.components.localizeDigits
import com.hisabak.ui.components.rememberIsArabic
import com.hisabak.ui.icons.HugeIcons
import com.hisabak.ui.theme.HisabakTheme
import com.hisabak.ui.theme.Spacing
import org.jetbrains.compose.resources.stringResource

/**
 * A conversation, not a form: the thread scrolls under a pinned composer, and the composer is the
 * only chrome. Deliberately not a chat app — the user's question is a plain right-aligned line in
 * a neutral bubble, the answer is unboxed body text at full width, because the answer is the
 * content and a second bubble around it would halve the width money figures need.
 *
 * Green is never used here: it means money-positive or the one primary action, and nothing on this
 * screen is either.
 */
@Composable
fun AskScreen(
    state: AskUiState,
    onIntent: (AskIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    // Follow the conversation as it grows; a new answer should never arrive off screen.
    LaunchedEffect(state.turns.size, state.busy) {
        val last = state.turns.size + if (state.busy) 1 else 0
        if (last > 0) listState.animateScrollToItem(last)
    }

    Column(modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentPadding = PaddingValues(
                start = Spacing.pageMargin,
                end = Spacing.pageMargin,
                top = Spacing.pageMargin,
                bottom = Spacing.s5,
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.s5),
        ) {
            if (state.isEmpty) {
                item(key = "intro") { AskIntro() }
            }
            items(state.turns, key = { "${it.role}:${it.text.hashCode()}" }) { turn ->
                when (turn.role) {
                    AskRole.User -> QuestionBubble(turn)
                    AskRole.Assistant -> Answer(turn)
                }
            }
            if (state.busy) {
                item(key = "busy") { Thinking() }
            }
            state.notice?.let { notice ->
                item(key = "notice") {
                    Text(
                        text = stringResource(
                            when (notice) {
                                AskNotice.NoQuestionsLeft -> Res.string.insights_ask_none_left
                                AskNotice.Unavailable -> Res.string.insights_ask_unavailable
                            },
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = HisabakTheme.colors.warning,
                    )
                }
            }
            // Suggestions sit at the end of the thread, where the next question is asked from.
            // Any already asked are dropped: repeating a question is not a suggestion.
            val asked = state.turns.filter { it.role == AskRole.User }.map { it.text }.toSet()
            item(key = "suggestions") {
                Suggestions(
                    questions = state.suggestions,
                    asked = asked,
                    enabled = !state.busy,
                    heading = !state.isEmpty,
                    onQuestion = { onIntent(AskIntent.QuestionTapped(it)) },
                )
            }
        }
        Composer(state = state, onIntent = onIntent)
    }
}

@Composable
private fun AskIntro() {
    Column {
        Text(
            text = stringResource(Res.string.insights_ask_empty_title),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(Spacing.s2))
        Text(
            text = stringResource(Res.string.insights_ask_empty_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** The question, as asked. Right-aligned and neutral: it is context for the answer, not the point. */
@Composable
private fun QuestionBubble(turn: AskTurn) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 4.dp),
            modifier = Modifier.widthIn(max = 300.dp),
        ) {
            Text(
                text = turn.text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = Spacing.s4, vertical = Spacing.s3),
            )
        }
    }
}

/** The answer: full width, unboxed, with a small mark so its source is never in doubt. */
@Composable
private fun Answer(turn: AskTurn) {
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s2)) {
            Icon(
                imageVector = HugeIcons.Idea,
                contentDescription = null,
                tint = HisabakTheme.colors.info,
                modifier = Modifier.size(14.dp),
            )
            Text(
                text = stringResource(Res.string.insights_ask_footer),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(Spacing.s2))
        Text(
            text = turn.text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun Thinking() {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s2)) {
        CircularProgressIndicator(
            modifier = Modifier.size(14.dp),
            strokeWidth = 2.dp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(Res.string.insights_ask_thinking),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Suggested questions as full-width rows rather than chips: they are sentences, and a sentence in a
 * pill either wraps badly or gets truncated.
 */
@Composable
private fun Suggestions(
    questions: List<SuggestedQuestion>,
    asked: Set<String>,
    enabled: Boolean,
    heading: Boolean,
    onQuestion: (String) -> Unit,
) {
    val texts = questions.map { questionText(it) }.filterNot { it in asked }
    if (texts.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s2)) {
        if (heading) {
            Text(
                text = stringResource(Res.string.insights_ask_suggestions),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        texts.forEach { text ->
            SuggestionRow(text = text, enabled = enabled, onClick = { onQuestion(text) })
        }
    }
}

@Composable
private fun SuggestionRow(text: String, enabled: Boolean, onClick: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        shape = RoundedCornerShape(12.dp),
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.s4, vertical = Spacing.s4),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.s3),
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = HugeIcons.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

/** Pinned to the bottom, above the keyboard. The allowance sits here, where spending it happens. */
@Composable
private fun Composer(state: AskUiState, onIntent: (AskIntent) -> Unit) {
    val arabic = rememberIsArabic()
    Column(Modifier.fillMaxWidth().imePadding().navigationBarsPadding()) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.pageMargin, vertical = Spacing.s3),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(Spacing.s2),
        ) {
            OutlinedTextField(
                value = state.draft,
                onValueChange = { onIntent(AskIntent.DraftChanged(it)) },
                placeholder = {
                    Text(
                        stringResource(Res.string.insights_ask_placeholder),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                enabled = !state.busy,
                minLines = 1,
                maxLines = 4,
                shape = RoundedCornerShape(20.dp),
                textStyle = MaterialTheme.typography.bodyMedium,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                ),
                modifier = Modifier.weight(1f),
            )
            Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                IconButton(onClick = { onIntent(AskIntent.Submitted) }, enabled = state.canSend) {
                    Icon(
                        imageVector = HugeIcons.ArrowUpward,
                        contentDescription = stringResource(Res.string.insights_ask_send),
                        tint = if (state.canSend) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
        }
        // Only ever one line under the field: the count while typing near the cap, the allowance
        // otherwise. Two counters at once would be noise.
        val hint = if (state.draft.length > MAX_QUESTION_LENGTH - 100) {
            localizeDigits("${state.draft.length}/$MAX_QUESTION_LENGTH", arabic)
        } else {
            state.remaining?.let {
                stringResource(
                    Res.string.insights_ask_remaining,
                    localizeDigits(it.toString(), arabic),
                    localizeDigits(ASK_DAILY_ALLOWANCE.toString(), arabic),
                )
            }
        }
        hint?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Spacing.pageMargin).padding(bottom = Spacing.s3),
            )
        }
        Spacer(Modifier.width(Spacing.s1))
    }
}

@Composable
internal fun questionText(q: SuggestedQuestion): String {
    val name = q.category?.name ?: ""
    return when (q.kind) {
        SuggestedQuestion.Kind.WhyUp -> stringResource(Res.string.insights_ask_q_why_up, name)
        SuggestedQuestion.Kind.StayUnderLimit -> stringResource(Res.string.insights_ask_q_stay_under, name)
        SuggestedQuestion.Kind.WhereToCut -> stringResource(Res.string.insights_ask_q_where_to_cut, name)
        SuggestedQuestion.Kind.ImproveSavings -> stringResource(Res.string.insights_ask_q_savings)
        SuggestedQuestion.Kind.Uncategorized -> stringResource(Res.string.insights_ask_q_uncategorized)
        SuggestedQuestion.Kind.FocusOn -> stringResource(Res.string.insights_ask_q_focus)
    }
}
