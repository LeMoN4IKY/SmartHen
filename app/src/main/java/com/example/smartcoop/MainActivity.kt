package com.example.smartcoop

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.work.*
import com.example.smartcoop.utils.LocaleHelper
import com.google.android.material.bottomnavigation.BottomNavigationView
import java.util.concurrent.TimeUnit
import androidx.fragment.app.Fragment

class MainActivity : AppCompatActivity() {

    private lateinit var navView: BottomNavigationView
    private lateinit var prefs: SharedPreferences

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.applyLanguage(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {

        LocaleHelper.applyLanguage(baseContext)

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)

        navView = findViewById(R.id.nav_view)

        // Восстанавливаем последний фрагмент
        val lastFragmentId = prefs.getInt("last_fragment", R.id.navigation_dashboard)
        navView.selectedItemId = lastFragmentId
        supportFragmentManager.beginTransaction()
            .replace(R.id.nav_host_fragment, getFragmentByItemId(lastFragmentId))
            .commit()

        navView.setOnItemSelectedListener { item ->
            saveLastFragment(item.itemId)
            when (item.itemId) {
                R.id.navigation_dashboard -> {
                    supportFragmentManager.beginTransaction()
                        .replace(R.id.nav_host_fragment, DashboardFragment())
                        .commit()
                    true
                }
                R.id.navigation_camera -> {
                    supportFragmentManager.beginTransaction()
                        .replace(R.id.nav_host_fragment, CameraFragment())
                        .commit()
                    true
                }
                R.id.navigation_errors -> {
                    supportFragmentManager.beginTransaction()
                        .replace(R.id.nav_host_fragment, ErrorsFragment())
                        .commit()
                    true
                }
                R.id.navigation_analytics -> {
                    supportFragmentManager.beginTransaction()
                        .replace(R.id.nav_host_fragment, AnalyticsFragment())
                        .commit()
                    true
                }
                R.id.navigation_profile -> {
                    supportFragmentManager.beginTransaction()
                        .replace(R.id.nav_host_fragment, ProfileFragment())
                        .commit()
                    true
                }
                else -> false
            }
        }

        // Очистка базы данных
//        val cleanupManager = DatabaseCleanupManager(this)
//        cleanupManager.startPeriodicCleanup()

        // Уведомления (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    100
                )
            }
        }

        // Точные будильники (Android 12+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            if (!alarmManager.canScheduleExactAlarms()) {
                val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                startActivity(intent)
            }
        }

        // Ежедневные уведомления
        val workRequest = PeriodicWorkRequestBuilder<NotificationWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(getDelayUntilNext9AM(), TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "notifications",
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )
    }

    private fun saveLastFragment(fragmentId: Int) {
        prefs.edit().putInt("last_fragment", fragmentId).apply()
    }

    private fun getFragmentByItemId(itemId: Int): Fragment {
        return when (itemId) {
            R.id.navigation_dashboard -> DashboardFragment()
            R.id.navigation_camera -> CameraFragment()
            R.id.navigation_errors -> ErrorsFragment()
            R.id.navigation_analytics -> AnalyticsFragment()
            R.id.navigation_profile -> ProfileFragment()
            else -> DashboardFragment()
        }
    }

    private fun getDelayUntilNext9AM(): Long {
        val calendar = java.util.Calendar.getInstance()
        calendar.set(java.util.Calendar.HOUR_OF_DAY, 9)
        calendar.set(java.util.Calendar.MINUTE, 0)
        calendar.set(java.util.Calendar.SECOND, 0)

        val now = System.currentTimeMillis()
        var next9AM = calendar.timeInMillis
        if (next9AM <= now) {
            next9AM += 24 * 60 * 60 * 1000
        }
        return next9AM - now
    }
}