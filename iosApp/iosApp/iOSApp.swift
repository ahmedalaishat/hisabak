import SwiftUI
import Shared
import FirebaseCore
import FirebaseCrashlytics

@main
struct iOSApp: App {
    @Environment(\.scenePhase) private var scenePhase

    // Koin + BGTaskScheduler registration must complete before launch finishes.
    init() {
        // Crash reporting only (no event analytics on iOS yet — see docs/kmp-migration.md B6).
        // Guarded so builds without a GoogleService-Info.plist keep working; collection is
        // off in debug and on in release, mirroring HisabakApp on Android.
        if Bundle.main.url(forResource: "GoogleService-Info", withExtension: "plist") != nil {
            FirebaseApp.configure()
            #if DEBUG
            Crashlytics.crashlytics().setCrashlyticsCollectionEnabled(false)
            #else
            Crashlytics.crashlytics().setCrashlyticsCollectionEnabled(true)
            #endif
        }
        IosAppStartKt.startIosApp(gcmCipher: CryptoKitGcmCipher(), aiSmsBridge: FoundationModelsSmsParser())
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
        .onChange(of: scenePhase) { _, phase in
            // BGAppRefreshTask is opportunistic, so an overdue auto-backup runs on foreground.
            if phase == .active {
                IosAppStartKt.onIosAppForeground()
            }
        }
    }
}