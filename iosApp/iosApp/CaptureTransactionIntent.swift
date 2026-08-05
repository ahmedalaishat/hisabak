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

    func perform() async throws -> some IntentResult & ReturnsValue<Bool> {
        // No dialog: notifications are the feedback channel ("transaction recorded" on a
        // parse, "saved for review" on the fallback). The returned flag still lets a
        // shortcut branch: If [Needs review] -> Open SMS inbox.
        let needsReview = await withCheckedContinuation { continuation in
            ShortcutCaptureKt.captureFromShortcut(text: message) { needsReview in
                continuation.resume(returning: needsReview.boolValue)
            }
        }
        return .result(value: needsReview)
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
            // Short: the home-screen grid truncates long tile labels. The banknote glyph reads
            // as money capture — text.viewfinder looked like a QR scanner.
            shortTitle: "Capture",
            systemImageName: "banknote"
        )
        AppShortcut(
            intent: OpenSmsInboxIntent(),
            phrases: ["Review messages in \(.applicationName)"],
            shortTitle: "Review inbox",
            systemImageName: "tray.and.arrow.down"
        )
    }
}
