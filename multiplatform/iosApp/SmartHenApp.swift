import SwiftUI
import SmartHenShared

@main
struct SmartHenApp: App {
    var body: some Scene {
        WindowGroup {
            SharedView().ignoresSafeArea()
        }
    }
}

struct SharedView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        PlatformKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}
