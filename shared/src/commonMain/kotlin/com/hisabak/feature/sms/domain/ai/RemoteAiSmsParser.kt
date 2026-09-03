package com.hisabak.feature.sms.domain.ai

import com.hisabak.core.domain.AppPreferences
import kotlinx.coroutines.flow.first

/**
 * [AiSmsParser] backed by the parse service instead of an on-device model.
 *
 * This is what gives the feature reach: Gemini Nano needs AICore (flagship silicon only) and Apple
 * Foundation Models needs an iPhone 15 Pro or newer, so most devices report `Unavailable` and see
 * no AI at all. A server-side model works everywhere.
 *
 * It is **strictly opt-in**: unlike on-device inference, this sends message text off the phone, so
 * [availability] requires the user to have turned it on. Nothing reaches the network until they do.
 *
 * Cost stays bounded because a successful parse is turned into a regex template
 * (`SynthesizeTemplateUseCase`), so the service is called roughly once per bank *format* rather
 * than once per message.
 */
class RemoteAiSmsParser(
    private val client: RemoteParseClient,
    private val preferences: AppPreferences,
) : AiSmsParser {

    override suspend fun availability(): AiParserAvailability =
        if (client.isConfigured && preferences.remoteParseEnabled.first()) {
            AiParserAvailability.Ready
        } else {
            AiParserAvailability.Unavailable
        }

    override suspend fun parse(body: String, knownBrands: List<String>): AiParsedSms? =
        request(body, knownBrands, todayIso = null, freeText = false)

    override suspend fun parseFreeText(
        text: String,
        knownBrands: List<String>,
        todayIso: String,
    ): AiParsedSms? = request(text, knownBrands, todayIso, freeText = true)

    private suspend fun request(
        text: String,
        knownBrands: List<String>,
        todayIso: String?,
        freeText: Boolean,
    ): AiParsedSms? {
        // Re-checked per call, not just in availability(): the user can revoke consent between the
        // screen reading availability and the parse firing, and revocation must take effect at once.
        if (availability() != AiParserAvailability.Ready) return null
        return client.parse(RemoteParseRequest(text, knownBrands, todayIso, freeText))
    }
}
