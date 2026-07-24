import AppIntents
import Shared

/// Shortcuts action for capture — the iOS counterpart of androidApp's `CaptureActivity`.
/// Wire it to a "When I get a message" personal automation ("Run immediately", filtered to the
/// bank sender) for hands-free capture; the app itself never reads messages. Runs in the app
/// process, so Koin is already started by `iOSApp.init` when `perform()` executes.
struct CaptureTransactionIntent: AppIntent {
    static let title: LocalizedStringResource = "Capture transaction"
    static let description = IntentDescription(
        "Reads a bank message and records the transaction in Hisabak."
    )

    @Parameter(title: "Message text")
    var message: String

    static var parameterSummary: some ParameterSummary {
        Summary("Capture transaction from \(\.$message)")
    }

    func perform() async throws -> some IntentResult & ProvidesDialog {
        let dialog = await withCheckedContinuation { continuation in
            ShortcutCaptureKt.captureFromShortcut(text: message) { _, outcome in
                continuation.resume(returning: outcome)
            }
        }
        return .result(dialog: IntentDialog(stringLiteral: dialog))
    }
}

struct HisabakAppShortcuts: AppShortcutsProvider {
    static var appShortcuts: [AppShortcut] {
        AppShortcut(
            intent: CaptureTransactionIntent(),
            phrases: ["Capture a transaction in \(.applicationName)"],
            shortTitle: "Capture transaction",
            systemImageName: "text.viewfinder"
        )
    }
}
