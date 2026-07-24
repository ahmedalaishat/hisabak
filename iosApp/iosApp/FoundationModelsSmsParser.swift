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
                completion(
                    AiSmsBridgeResult(
                        brandName: parsed.brand,
                        amountMinor: Int64(((parsed.amount ?? 0) * 100).rounded()),
                        hasAmount: parsed.amount != nil,
                        currencyCode: parsed.currency,
                        dateIso: parsed.date
                    )
                )
            } catch {
                completion(nil)
            }
        }
        #else
        completion(nil)
        #endif
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

    @Guide(description: "The transaction date and time in ISO 8601 format, e.g. 2026-07-24T10:30:00; empty if not stated.")
    var date: String?
}
#endif
