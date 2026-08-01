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

    func perform() async throws -> some IntentResult & ReturnsValue<Bool> & ProvidesDialog {
        let (dialog, needsReview) = await withCheckedContinuation { continuation in
            ShortcutCaptureKt.captureFromShortcut(text: message) { outcome, needsReview in
                continuation.resume(returning: (outcome, needsReview.boolValue))
            }
        }
        // The returned flag lets a shortcut branch: If [Needs review] -> Open SMS inbox.
        return .result(value: needsReview, dialog: IntentDialog(stringLiteral: dialog))
    }
}

/// Opens the app straight on the SMS inbox — the review counterpart of the capture action.
/// Useful as the If-branch after a capture that returned needs-review, from Spotlight, or as
/// a home-screen shortcut.
struct OpenSmsInboxIntent: AppIntent {
    static let title: LocalizedStringResource = "Open SMS inbox"
    static let description = IntentDescription("Opens Hisabak on the SMS inbox for review.")
    static let openAppWhenRun = true

    @MainActor
    func perform() async throws -> some IntentResult {
        ShortcutCaptureKt.openInboxFromShortcut()
        return .result()
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
        AppShortcut(
            intent: OpenSmsInboxIntent(),
            phrases: ["Review messages in \(.applicationName)"],
            shortTitle: "Open SMS inbox",
            systemImageName: "tray.full"
        )
    }
}
