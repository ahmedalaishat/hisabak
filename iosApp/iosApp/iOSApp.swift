import SwiftUI
import Shared

@main
struct iOSApp: App {
    // Koin + BGTaskScheduler registration must complete before launch finishes.
    init() {
        IosAppStartKt.startIosApp(gcmCipher: CryptoKitGcmCipher())
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}