import Foundation
import Shared
#if canImport(FoundationModels)
import FoundationModels
#endif

/// On-device category suggestion for the brand editor over Apple's Foundation Models
/// (Apple Intelligence, iOS 26+). Implements the Kotlin `AiCategoryBridge` seam — same rules as
/// `FoundationModelsSmsParser`: no exceptions cross the bridge (failure is a nil completion) and
/// the shared Kotlin sanitize step owns acceptance rules (existing-name snapping, vocabulary
/// validation). On devices without Apple Intelligence `isAvailable` is false and the suggestion
/// affordance never appears.
final class FoundationModelsCategorySuggester: AiCategoryBridge {

    func isAvailable() -> Bool {
        #if canImport(FoundationModels)
        if #available(iOS 26.0, *) {
            if case .available = SystemLanguageModel.default.availability { return true }
        }
        #endif
        return false
    }

    func suggest(
        brandName: String,
        categories: [String],
        completion: @escaping (AiCategoryBridgeResult?) -> Void
    ) {
        #if canImport(FoundationModels)
        guard #available(iOS 26.0, *) else { completion(nil); return }
        Task {
            do {
                let session = LanguageModelSession(instructions: Self.instructions(categories: categories))
                let response = try await session.respond(to: brandName, generating: SuggestedCategory.self)
                let suggested = response.content
                completion(AiCategoryBridgeResult(
                    existingName: suggested.existing,
                    newName: suggested.name,
                    newType: suggested.type,
                    newColor: suggested.color,
                    newIcon: suggested.icon
                ))
            } catch {
                completion(nil)
            }
        }
        #else
        completion(nil)
        #endif
    }

    private static func instructions(categories: [String]) -> String {
        var text = """
            You pick a budget category for a merchant or brand name, in English or Arabic. \
            If one of the user's categories fits the brand, set existing to that category's name \
            exactly as listed and leave every other field empty. Otherwise propose a new category: \
            a short name (1-2 words, same language as the brand) with the best fitting type, color, \
            and icon. If the brand name is meaningless or you cannot tell what it sells, leave \
            every field empty.
            """
        if !categories.isEmpty {
            text += "\nThe user's categories (name (type)): \(categories.joined(separator: ", "))."
        }
        return text
    }
}

#if canImport(FoundationModels)
@available(iOS 26.0, *)
@Generable
private struct SuggestedCategory {
    @Guide(description: "The name of the user's existing category that fits the brand, exactly as listed. Empty when proposing a new category instead.")
    var existing: String?

    @Guide(description: "A short name for a proposed new category (1-2 words, same language as the brand). Empty when an existing category was chosen.")
    var name: String?

    @Guide(description: "The proposed category type: one of income, expenses, savings, investment.")
    var type: String?

    @Guide(description: "The proposed color: one of green, blue, orange, red, teal, purple, pink, gray.")
    var color: String?

    @Guide(description: "The proposed icon: one of wallet, cart, briefcase, car, utensils, piggy-bank, home, film, book, heart, gift, plane.")
    var icon: String?
}
#endif
