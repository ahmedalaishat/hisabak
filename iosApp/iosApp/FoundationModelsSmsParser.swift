import Foundation
import Shared
#if canImport(FoundationModels)
import FoundationModels
#endif

/// On-device AI SMS parsing over Apple's Foundation Models (Apple Intelligence, iOS 26+).
/// Implements the Kotlin `AiSmsBridge` seam — same philosophy as `CryptoKitGcmCipher`: the
/// Swift-only framework stays in the Swift layer, no exceptions cross the bridge (failure is a
/// nil completion), and the shared Kotlin sanitize step owns acceptance rules. On devices
/// without Apple Intelligence (or below iOS 26) `isAvailable` is false and the app hides every
/// AI affordance.
final class FoundationModelsSmsParser: AiSmsBridge {

    func isAvailable() -> Bool {
        #if canImport(FoundationModels)
        if #available(iOS 26.0, *) {
            if case .available = SystemLanguageModel.default.availability { return true }
        }
        #endif
        return false
    }

    func parse(body: String, knownBrands: [String], completion: @escaping (AiSmsBridgeResult?) -> Void) {
        #if canImport(FoundationModels)
        guard #available(iOS 26.0, *) else { completion(nil); return }
        Task {
            do {
                let session = LanguageModelSession(instructions: Self.instructions(knownBrands: knownBrands))
                let response = try await session.respond(to: body, generating: ParsedBankSms.self)
                let parsed = response.content
                completion(Self.bridgeResult(
                    brand: parsed.brand, amount: parsed.amount,
                    currency: parsed.currency, date: parsed.date
                ))
            } catch {
                completion(nil)
            }
        }
        #else
        completion(nil)
        #endif
    }

    func parseFreeText(
        text: String,
        knownBrands: [String],
        todayIso: String,
        completion: @escaping (AiSmsBridgeResult?) -> Void
    ) {
        #if canImport(FoundationModels)
        guard #available(iOS 26.0, *) else { completion(nil); return }
        Task {
            do {
                let session = LanguageModelSession(
                    instructions: Self.freeTextInstructions(knownBrands: knownBrands, todayIso: todayIso)
                )
                let response = try await session.respond(to: text, generating: ParsedQuickEntry.self)
                let parsed = response.content
                completion(Self.bridgeResult(
                    brand: parsed.brand, amount: parsed.amount,
                    currency: parsed.currency, date: parsed.date
                ))
            } catch {
                completion(nil)
            }
        }
        #else
        completion(nil)
        #endif
    }

    /// `Int64(Double)` traps on NaN/overflow — a runtime fatal error the `do/catch` cannot
    /// catch — and the model controls the value, so bound it before converting. Out-of-range
    /// amounts degrade to "no amount", which the shared sanitize step rejects as incomplete.
    private static func bridgeResult(
        brand: String?, amount: Double?, currency: String?, date: String?
    ) -> AiSmsBridgeResult {
        let minor = amount.flatMap { a -> Int64? in
            guard a.isFinite, a > 0, a < 9e16 else { return nil }
            return Int64((a * 100).rounded())
        }
        return AiSmsBridgeResult(
            brandName: brand,
            amountMinor: minor ?? 0,
            hasAmount: minor != nil,
            currencyCode: currency,
            dateIso: date
        )
    }

    private static func freeTextInstructions(knownBrands: [String], todayIso: String) -> String {
        var text = """
            You extract a spending or income record from a short note a person typed, in English, \
            Arabic, or both (e.g. "100 at noon", "lunch 45 yesterday", "salary 15k"). \
            Today is \(todayIso). Resolve relative date wording like "yesterday" or "last friday" \
            against today's date; never produce a future date. Expand amount shorthand like "15k" \
            to 15000. If the note doesn't describe a purchase, payment, or income, leave every \
            field empty.
            """
        if !knownBrands.isEmpty {
            text += """
                \nKnown brands: \(knownBrands.joined(separator: ", ")). \
                If the named merchant matches a known brand - even with typos, different casing, \
                or an abbreviation - use that brand name exactly as listed.
                """
        }
        return text
    }

    private static func instructions(knownBrands: [String]) -> String {
        var text = """
            You extract bank transaction data from SMS messages. Messages may be in English, Arabic, \
            or both. If the text is not a bank transaction message, leave every field empty.
            """
        if !knownBrands.isEmpty {
            text += """
                \nKnown brands: \(knownBrands.joined(separator: ", ")). \
                If the merchant matches a known brand - even with typos, different casing, or an \
                abbreviation - use that brand name exactly as listed.
                """
        }
        return text
    }
}

#if canImport(FoundationModels)
@available(iOS 26.0, *)
@Generable
private struct ParsedBankSms {
    @Guide(description: "The merchant or sender name only, cleaned of locations and reference codes (e.g. CARREFOUR, not CARREFOUR, DUBAI, ARE). Empty if not a bank transaction.")
    var brand: String?

    @Guide(description: "The transaction amount as a positive decimal number without separators.")
    var amount: Double?

    @Guide(description: "The ISO 4217 currency code such as AED or USD; empty if not stated.")
    var currency: String?

    @Guide(description: "The transaction date and time in ISO 8601 format, e.g. 2026-07-24T10:30:00, ONLY if a date is written in the message itself. If the message has no date, leave this empty - never guess or invent one.")
    var date: String?
}

@available(iOS 26.0, *)
@Generable
private struct ParsedQuickEntry {
    @Guide(description: "The merchant, store, or payee named in the note. Empty if none is named.")
    var brand: String?

    @Guide(description: "The amount as a positive decimal number without separators; expand shorthand like 15k to 15000.")
    var amount: Double?

    @Guide(description: "The ISO 4217 currency code such as AED or USD, ONLY if the note states a currency; empty otherwise.")
    var currency: String?

    @Guide(description: "The date resolved against today's date from wording like yesterday, last friday, or an explicit date, in ISO 8601 format, e.g. 2026-07-31. Empty if the note has no date wording. Never a future date.")
    var date: String?
}
#endif
