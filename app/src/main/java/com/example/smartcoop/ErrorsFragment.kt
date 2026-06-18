package com.example.smartcoop

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.smartcoop.data.SensorManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.example.smartcoop.data.Sensor

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

        checkAllBtn.setOnClickListener {
            if (!isChecking) {
                startSystemCheck()
            }
        }

        lifecycleScope.launch {
            sensorManager.loadSensors("coop_1")
            sensorManager.sensors.collect { sensors ->
                updateErrorsUI(sensors)
            }
        }
    }

    private fun startSystemCheck() {
        isChecking = true
        checkAllBtn.isEnabled = false
        checkAllBtn.text = "⏳ Проверка..."

        lifecycleScope.launch {
            Toast.makeText(requireContext(), "🔍 Проверка систем...", Toast.LENGTH_SHORT).show()

            // ============================================================
            // 🔧 СЮДА ТЫ БУДЕШЬ ВСТАВЛЯТЬ РЕАЛЬНЫЙ ЗАПРОС К БЭКЕНДУ
            // ============================================================
            // Пример:
            // val results = api.checkAllSensors("coop_1")
            // results.forEach { status ->
            //     sensorManager.updateSensorStatus(status.sensorId, status.isOnline)
            // }
            // ============================================================

            // Пока — заглушка с задержкой (имитация опроса)
            delay(1500)

            // Обновляем UI
            updateErrorsUI(sensorManager.sensors.value)

            isChecking = false
            checkAllBtn.isEnabled = true
            checkAllBtn.text = "🔄 Проверить все системы"

            val offlineCount = sensorManager.sensors.value.count { !it.isOnline }
            if (offlineCount > 0) {
                Toast.makeText(
                    requireContext(),
                    "⚠️ Обнаружено $offlineCount неисправных датчиков!",
                    Toast.LENGTH_LONG
                ).show()
            } else {
                Toast.makeText(
                    requireContext(),
                    "✅ Все системы исправны!",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun updateErrorsUI(sensors: List<Sensor>) {
        containerLayout.removeAllViews()

        sensors.forEach { sensor ->
            val itemView = layoutInflater.inflate(R.layout.item_system_status, containerLayout, false)
            val icon = itemView.findViewById<TextView>(R.id.systemIcon)
            val name = itemView.findViewById<TextView>(R.id.systemName)
            val status = itemView.findViewById<TextView>(R.id.systemStatus)

            name.text = sensor.name

            if (sensor.isOnline) {
                icon.text = "✅"
                status.text = "Исправен"
                status.setTextColor(resources.getColor(android.R.color.holo_green_dark))
            } else {
                icon.text = "❌"
                status.text = "Неисправен, проверьте!"
                status.setTextColor(resources.getColor(android.R.color.holo_red_dark))
            }

            containerLayout.addView(itemView)
        }
    }
}