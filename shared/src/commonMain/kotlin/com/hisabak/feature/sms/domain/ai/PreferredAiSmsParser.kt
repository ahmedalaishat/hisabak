package com.hisabak.feature.sms.domain.ai

/**
 * Routes a parse to whichever engine the user has asked for, falling back to the other.
 *
 * **The remote service leads when it is enabled.** Turning on "Parse with the online model" is a
 * statement of preference, not merely of consent: the on-device models are materially weaker, and
 * a setting that says it sends messages to a better model should actually do so. Preferring the
 * local model there made the switch close to a no-op on the devices that have one.
 *
 * Nothing is transmitted until that switch is on — [RemoteAiSmsParser] reports `Unavailable` until
 * then, so a device with no opt-in behaves exactly as it did before this class existed.
 *
 * The fallback runs in both directions, which is the point of keeping both: a phone with no local
 * model relies on the service, and a phone with one still parses when the service is unreachable —
 * offline, an outage, or a daily budget that has been spent.
 */
class PreferredAiSmsParser(
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
     * A null from the leading engine falls through rather than ending the attempt: an engine
     * returning nothing is a real and common failure mode, and the second engine is often the one
     * that can read the message.
     */
    private suspend fun firstUsable(attempt: suspend (AiSmsParser) -> AiParsedSms?): AiParsedSms? {
        val preferred = if (remote.availability() == AiParserAvailability.Ready) {
            listOf(remote, onDevice)
        } else {
            listOf(onDevice)
        }
        for (parser in preferred) {
            if (parser.availability() == AiParserAvailability.Ready) {
                attempt(parser)?.let { return it }
            }
        }
        return null
    }
}
