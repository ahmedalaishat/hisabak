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
        // Each flavor loads its own Firebase iOS app (the plists are per-bundle-id, so staging
        // crashes attribute to com.hisabak.staging, not prod). Guarded so builds without a
        // plist keep working; collection is off in debug and on in release, mirroring
        // HisabakApp on Android.
        let flavor = (Bundle.main.object(forInfoDictionaryKey: "HisabakFlavor") as? String) ?? "prod"
        let firebasePlist = flavor == "staging" ? "GoogleService-Info-Staging" : "GoogleService-Info-Prod"
        if let path = Bundle.main.path(forResource: firebasePlist, ofType: "plist"),
           let options = FirebaseOptions(contentsOfFile: path) {
            FirebaseApp.configure(options: options)
            #if DEBUG
            Crashlytics.crashlytics().setCrashlyticsCollectionEnabled(false)
            #else
            Crashlytics.crashlytics().setCrashlyticsCollectionEnabled(true)
            #endif
        }
        IosAppStartKt.startIosApp(
            gcmCipher: CryptoKitGcmCipher(),
            aiSmsBridge: FoundationModelsSmsParser(),
            aiCategoryBridge: FoundationModelsCategorySuggester()
        )
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