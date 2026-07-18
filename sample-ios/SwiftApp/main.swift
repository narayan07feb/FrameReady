import SwiftUI
import SampleIos

/// Wraps the Kotlin/Compose Multiplatform `MainViewController()` — the exact same
/// `MainScreen` composable Android's `MainActivity` hosts — so it fills the SwiftUI window.
struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

struct ContentView: View {
    var body: some View {
        ComposeView()
            .ignoresSafeArea(.container, edges: .all)
    }
}

@main
struct SmokeTestApp: App {
    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}
