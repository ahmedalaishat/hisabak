package com.hisabak.feature.sms.platform

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import com.hisabak.core.common.DomainResult
import com.hisabak.feature.sms.domain.capture.CaptureSource
import com.hisabak.feature.sms.domain.capture.CaptureTransactionUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.time.Instant

/**
 * Listens for incoming SMS broadcasts and hands each body to [CaptureTransactionUseCase] as the
 * [CaptureSource.SMS_BROADCAST] source. Auto-capture; present in the sideload build only (the Play
 * build's manifest strips this receiver and its restricted permission).
 *
 * Multi-part messages are re-assembled by grouping PDUs that share the same originating address,
 * matching how the Android SMS app reconstructs long messages.
 */
class IncomingSmsReceiver : BroadcastReceiver(), KoinComponent {

    private val capture: CaptureTransactionUseCase by inject()

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "onReceive action=${intent.action}")
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent).orEmpty()
        Log.d(TAG, "onReceive parts=${messages.size}")
        if (messages.isEmpty()) return

        val bodiesByAddress = messages
            .groupBy { it.originatingAddress ?: UNKNOWN_SENDER }
            .mapValues { (_, parts) -> parts.joinToString(separator = "") { it.messageBody.orEmpty() } }

        val receivedAt = messages.firstOrNull()
            ?.timestampMillis
            ?.let(Instant::ofEpochMilli)
            ?: Instant.now()

        val pending = goAsync()
        scope.launch {
            try {
                bodiesByAddress.values
                    .filter { it.isNotBlank() }
                    .forEach { body ->
                        val result = capture(body, CaptureSource.SMS_BROADCAST, receivedAt)
                        if (result is DomainResult.Failure) {
                            Log.d(TAG, "SMS ingestion failed: ${result.error.message}")
                        }
                    }
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        const val TAG = "IncomingSmsReceiver"
        const val UNKNOWN_SENDER = "unknown"
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
