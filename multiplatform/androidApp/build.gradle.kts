plugins { id("com.android.application"); kotlin("plugin.compose") }
android {
    namespace = "com.smarthen.quail"
    compileSdk = 37
    defaultConfig {
        applicationId = "com.smarthen.quail"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }
    buildFeatures { compose = true; buildConfig = true }
}
dependencies {
    implementation(project(":shared"))
    implementation("androidx.activity:activity-compose:1.12.0")
}
