package com.hisabak.feature.sms.domain.ai

/**
 * Tries the on-device model first and falls back to the remote service.
 *
 * On-device first is deliberate: it is free, works offline, and the message never leaves the
 * phone. The remote parser is the fallback for the devices that have no model — which is most of
 * them — and only when the user has opted in.
 *
 * A device with a working on-device model therefore never sends anything to the network, even
 * with the remote parser enabled.
 */
class PreferOnDeviceAiSmsParser(
    private val onDevice: AiSmsParser,
    private val remote: AiSmsParser,
) : AiSmsParser {

    override suspend fun availability(): AiParserAvailability =
        if (onDevice.availability() == AiParserAvailability.Ready ||
            remote.availability() == AiParserAvailability.Ready
        ) {
            AiParserAvailability.Ready
        } else {
            AiParserAvailability.Unavailable
        }

    override suspend fun parse(body: String, knownBrands: List<String>): AiParsedSms? =
        firstUsable { it.parse(body, knownBrands) }

    override suspend fun parseFreeText(
        text: String,
        knownBrands: List<String>,
        todayIso: String,
    ): AiParsedSms? = firstUsable { it.parseFreeText(text, knownBrands, todayIso) }

    /**
     * The on-device model returning nothing is a real failure mode (Nano is weak on Arabic and on
     * unusual formats), so a null from it falls through to the remote service rather than ending
     * the attempt — that fallback is the "more advanced model" the remote parser exists to provide.
     */
    private suspend fun firstUsable(attempt: suspend (AiSmsParser) -> AiParsedSms?): AiParsedSms? {
        if (onDevice.availability() == AiParserAvailability.Ready) {
            attempt(onDevice)?.let { return it }
        }
        if (remote.availability() == AiParserAvailability.Ready) {
            return attempt(remote)
        }
        return null
    }
}
