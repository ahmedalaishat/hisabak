package com.hisabak.feature.sms.presentation.inbox

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hisabak.BuildConfig
import com.hisabak.core.presentation.LaunchedViewEffectHandler
import com.hisabak.shared.resources.Res
import com.hisabak.shared.resources.sms_ai_parse_failed
import com.hisabak.shared.resources.sms_template_learned
import com.hisabak.shared.resources.sms_template_learned_undo
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun SmsInboxRoute(
    onCreateTemplate: (String) -> Unit,
    onReviewTransaction: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SmsInboxViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    // Auto-import (RECEIVE_SMS) exists only in the staging build; the prod/Play build is SMS-free.
    LaunchedEffect(Unit) {
        if (!BuildConfig.SMS_AUTO_CAPTURE) return@LaunchedEffect
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECEIVE_SMS,
        ) == PackageManager.PERMISSION_GRANTED
        viewModel.onIntent(SmsInboxIntent.PermissionChanged(granted))
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        viewModel.onIntent(SmsInboxIntent.PermissionChanged(granted))
    }

    val aiParseFailedText = stringResource(Res.string.sms_ai_parse_failed)
    val templateLearnedText = stringResource(Res.string.sms_template_learned)
    val undoText = stringResource(Res.string.sms_template_learned_undo)
    LaunchedViewEffectHandler(
        effectFlow = viewModel.effect,
        onConsumeEffect = { viewModel.onIntent(SmsInboxIntent.ConsumeEffect) },
        onEffect = { effect ->
            when (effect) {
                is SmsInboxEffect.ParseFailed ->
                    snackbarHostState.showSnackbar("Could not parse: ${effect.reason}")
                is SmsInboxEffect.TransactionCreated -> {
                    val learned = effect.learnedTemplateId
                    val created = "Transaction created: ${formatMoney(effect.amount)}"
                    // One snackbar, not two: the learned rule is a footnote to the confirm the
                    // user just made, and queuing a second would make them wait through it.
                    val result = snackbarHostState.showSnackbar(
                        message = if (learned == null) created else "$created. $templateLearnedText",
                        actionLabel = undoText.takeIf { learned != null },
                        duration = if (learned == null) SnackbarDuration.Short else SnackbarDuration.Long,
                    )
                    if (learned != null && result == SnackbarResult.ActionPerformed) {
                        viewModel.onIntent(SmsInboxIntent.UndoLearnedTemplate(learned))
                    }
                }
                SmsInboxEffect.AiParseFailed ->
                    snackbarHostState.showSnackbar(aiParseFailedText)
            }
        },
    )

    SmsInboxScreen(
        state = state,
        onCreateTemplate = { onCreateTemplate(it.value) },
        onReviewTransaction = onReviewTransaction,
        onImportParsed = { viewModel.onIntent(SmsInboxIntent.ImportParsed(it)) },
        snackbarHostState = snackbarHostState,
        autoImportAvailable = BuildConfig.SMS_AUTO_CAPTURE,
        onSearchChange = { viewModel.onIntent(SmsInboxIntent.SearchChanged(it)) },
        onDraftChange = { viewModel.onIntent(SmsInboxIntent.DraftChanged(it)) },
        onIngest = { viewModel.onIntent(SmsInboxIntent.IngestDraft) },
        onDelete = { viewModel.onIntent(SmsInboxIntent.Delete(it)) },
        onEnableAutoImport = { permissionLauncher.launch(Manifest.permission.RECEIVE_SMS) },
        modifier = modifier,
        onSuggestParse = { viewModel.onIntent(SmsInboxIntent.SuggestParse(it)) },
        onConfirmSuggestion = { viewModel.onIntent(SmsInboxIntent.ConfirmSuggestion(it)) },
        onPromptAccept = { viewModel.onIntent(SmsInboxIntent.AcceptPrompt(it)) },
        onPromptLater = { viewModel.onIntent(SmsInboxIntent.DismissPrompt(it)) },
        onPromptNever = { viewModel.onIntent(SmsInboxIntent.SuppressPrompt(it)) },
        onDismissSuggestion = { viewModel.onIntent(SmsInboxIntent.DismissSuggestion(it)) },
    )
}
