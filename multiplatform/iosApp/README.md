# iOS host

Open `SmartHen.xcodeproj` in Xcode, select the shared **SmartHen** scheme and an Apple Silicon iPhone/iPad simulator, then Run. The build phase runs `../gradlew :shared:embedAndSignAppleFrameworkForXcode` and links the resulting static Kotlin framework. A working JDK and the Android SDK configuration used by the multiplatform Gradle project are required even when Xcode initiates the build. Set `JAVA_HOME` in your development environment if Xcode cannot find the JDK.

The project supports iOS 16+ and arm64 devices/simulators. Intel simulators are not configured in the Kotlin project. Simulator builds do not require an Apple developer account. For physical devices, choose your own signing team in Xcode; no team or signing credentials are committed. The bundle identifier is `com.smarthen.quail`.

From this directory, compile the complete simulator host with:

```sh
xcodebuild -project SmartHen.xcodeproj -scheme SmartHen -configuration Debug -sdk iphonesimulator -destination 'generic/platform=iOS Simulator' -derivedDataPath /tmp/smarthen-ios-build CODE_SIGNING_ALLOWED=NO build
```

Local network access is declared for your configured house server; deployed servers should use HTTPS. Production icons, App Store metadata and distribution signing are separate release work. The shared scheme's test action currently has no native XCTest target; Kotlin common tests run through Gradle.
