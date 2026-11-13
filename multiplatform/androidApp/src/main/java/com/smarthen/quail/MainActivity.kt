package com.smarthen.quail
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.smarthen.app.AndroidSessionStore
import com.smarthen.app.App
import com.smarthen.app.AndroidStore
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val store = AndroidStore(applicationContext)
        setContent { App(store, AndroidSessionStore(applicationContext), allowLocalHttp = BuildConfig.DEBUG) }
    }
}
