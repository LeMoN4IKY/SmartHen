package com.example.smartcoop

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.smartcoop.data.Sensor
import com.example.smartcoop.data.SensorManager
import com.example.smartcoop.utils.ErrorHandler
import kotlinx.coroutines.launch

/**
 * Фрагмент диагностики систем
 * Отображает статус всех датчиков (онлайн/офлайн)
 * Автообновляется при смене курятника
 */
class ErrorsFragment : Fragment() {

    private lateinit var sensorManager: SensorManager
    private lateinit var containerLayout: LinearLayout
    private lateinit var checkAllBtn: Button
    private var isChecking = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_errors, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        sensorManager = SensorManager(requireContext())
        containerLayout = view.findViewById(R.id.errorsContainer)
        checkAllBtn = view.findViewById(R.id.checkAllBtn)

        // Кнопка проверки
        checkAllBtn.setOnClickListener {
            if (!isChecking) {
                startSystemCheck()
            }
        }

        // Загрузка датчиков
        lifecycleScope.launch {
            try {
                val coopId = DataManager.getCurrentCoopIdOrDefault()
                sensorManager.loadSensors(coopId)
                sensorManager.sensors.collect { sensors ->
                    updateErrorsUI(sensors)
                }
            } catch (e: Exception) {
                ErrorHandler.handle(requireContext(), e, "⚠️ Не удалось загрузить датчики")
            }
        }

        // ===== АВТООБНОВЛЕНИЕ ПРИ СМЕНЕ КУРЯТНИКА =====
        lifecycleScope.launch {
            CoopManager(requireContext()).selectedCoopId.collect { coopId ->
                if (coopId != null) {
                    try {
                        sensorManager.loadSensors(coopId)
                    } catch (e: Exception) {
                        ErrorHandler.handle(requireContext(), e, "⚠️ Ошибка загрузки")
                    }
                }
            }
        }
    }

    // ===== ПЕРЕВОД НАЗВАНИЙ ДАТЧИКОВ =====

    private fun translateSensorName(sensor: Sensor): String {
        return when {
            sensor.name.contains("Вода", ignoreCase = true) ||
                    sensor.name.contains("Water", ignoreCase = true) -> getString(R.string.water)

            sensor.name.contains("Корм", ignoreCase = true) ||
                    sensor.name.contains("Feed", ignoreCase = true) -> getString(R.string.feed)

            sensor.name.contains("Температура", ignoreCase = true) ||
                    sensor.name.contains("Temperature", ignoreCase = true) -> getString(R.string.temp_sensor)

            sensor.name.contains("Отопление", ignoreCase = true) ||
                    sensor.name.contains("Heating", ignoreCase = true) -> getString(R.string.heating)

            sensor.name.contains("Загрязнение", ignoreCase = true) ||
                    sensor.name.contains("Air", ignoreCase = true) -> getString(R.string.air_quality)

            sensor.name.contains("Накоплено яиц", ignoreCase = true) ||
                    sensor.name.contains("Eggs", ignoreCase = true) -> getString(R.string.eggs_collected)

            else -> sensor.name
        }
    }

    // ===== ЗАПУСК ПРОВЕРКИ =====

    private fun startSystemCheck() {
        isChecking = true
        checkAllBtn.isEnabled = false
        checkAllBtn.text = getString(R.string.checking_systems)

        lifecycleScope.launch {
            try {
                Toast.makeText(requireContext(), R.string.checking_systems_toast, Toast.LENGTH_SHORT).show()

                val results = sensorManager.checkAllSensors()
                updateErrorsUI(sensorManager.sensors.value)

                val offlineCount = results.count { !it.isOnline }
                if (offlineCount > 0) {
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.sensors_offline_warning, offlineCount),
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    Toast.makeText(requireContext(), R.string.all_systems_ok, Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                ErrorHandler.handle(requireContext(), e, "❌ Ошибка проверки систем")
            } finally {
                isChecking = false
                checkAllBtn.isEnabled = true
                checkAllBtn.text = getString(R.string.check_all_systems)
            }
        }
    }

    // ===== ОБНОВЛЕНИЕ UI =====

    private fun updateErrorsUI(sensors: List<Sensor>) {
        containerLayout.removeAllViews()

        if (sensors.isEmpty()) {
            val emptyView = TextView(requireContext()).apply {
                text = getString(R.string.no_sensors)
                textSize = 16f
                setTextColor(ContextCompat.getColor(requireContext(), android.R.color.darker_gray))
                gravity = android.view.Gravity.CENTER
                setPadding(0, 32, 0, 32)
            }
            containerLayout.addView(emptyView)
            return
        }

        sensors.forEach { sensor ->
            val itemView = layoutInflater.inflate(R.layout.item_system_status, containerLayout, false)
            val icon = itemView.findViewById<TextView>(R.id.systemIcon)
            val name = itemView.findViewById<TextView>(R.id.systemName)
            val status = itemView.findViewById<TextView>(R.id.systemStatus)

            name.text = translateSensorName(sensor)

            if (sensor.isOnline) {
                icon.text = "✅"
                status.text = getString(R.string.sensor_online)
                status.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.holo_green_dark))
            } else {
                icon.text = "❌"
                status.text = getString(R.string.sensor_offline)
                status.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.holo_red_dark))
            }

            containerLayout.addView(itemView)
        }
    }
}