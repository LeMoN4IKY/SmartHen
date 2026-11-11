package com.smarthen.app
import android.content.Context
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
class AndroidStore(context: Context) : LocalStore {
    private val prefs = context.getSharedPreferences("smarthen", Context.MODE_PRIVATE)
    override fun get(key: String) = prefs.getString(key, null)
    override fun put(key: String, value: String) { prefs.edit().putString(key, value).apply() }
}
actual fun platformClient() = HttpClient(OkHttp)
actual fun nowSeconds(): Long = System.currentTimeMillis() / 1000
