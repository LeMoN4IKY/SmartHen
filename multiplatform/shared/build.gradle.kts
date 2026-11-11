plugins {
    kotlin("multiplatform")
    kotlin("plugin.compose")
    kotlin("plugin.serialization")
    id("org.jetbrains.compose")
    id("com.android.kotlin.multiplatform.library")
}
kotlin {
    android {
        namespace = "com.smarthen.shared"
        compileSdk = 37
        minSdk = 26
        withHostTestBuilder {}.configure {}
    }
    iosArm64 { binaries.framework { baseName = "SmartHenShared"; isStatic = true } }
    iosSimulatorArm64 { binaries.framework { baseName = "SmartHenShared"; isStatic = true } }
    sourceSets {
        commonMain.dependencies {
            implementation("org.jetbrains.androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
            implementation("org.jetbrains.androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
            implementation("io.ktor:ktor-client-core:3.3.0")
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation("io.ktor:ktor-client-mock:3.3.0")
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
        }
        androidMain.dependencies { implementation("io.ktor:ktor-client-okhttp:3.3.0") }
        iosMain.dependencies { implementation("io.ktor:ktor-client-darwin:3.3.0") }
    }
}
