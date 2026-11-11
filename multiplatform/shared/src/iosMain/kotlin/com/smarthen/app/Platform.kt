package com.smarthen.app
import androidx.compose.ui.window.ComposeUIViewController
import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import platform.Foundation.NSUserDefaults
import platform.Foundation.NSDate
import platform.Foundation.timeIntervalSince1970
class AppleStore : LocalStore {
    private val prefs = NSUserDefaults.standardUserDefaults
    override fun get(key: String) = prefs.stringForKey(key)
    override fun put(key: String, value: String) { prefs.setObject(value, forKey = key) }
}
actual fun platformClient() = HttpClient(Darwin)
actual fun nowSeconds(): Long = NSDate().timeIntervalSince1970.toLong()
fun MainViewController() = ComposeUIViewController { App(AppleStore(), AppleSessionStore()) }
