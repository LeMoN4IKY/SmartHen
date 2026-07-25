package com.example.smartcoop

import android.graphics.drawable.AnimationDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.smartcoop.data.Sensor
import com.example.smartcoop.data.SensorManager
import com.example.smartcoop.data.SensorType
import com.example.smartcoop.utils.ErrorHandler
import kotlinx.coroutines.launch

/**
 * Главный экран с датчиками, анимацией курицы и кнопкой сбора яиц
 *
 * Особенности:
 * - Автообновление каждые 5 секунд
 * - Обновление при смене курятника
 * - Кэширование данных для офлайн-режима
 * - Индикатор "Данные из кэша"
 */
class DashboardFragment : Fragment() {

    // UI элементы
    private lateinit var sensorsContainer: LinearLayout
    private lateinit var warningMessage: TextView
    private lateinit var chickenRun: ImageView

    // Менеджеры
    private lateinit var sensorManager: SensorManager
    private lateinit var coopManager: CoopManager

    // Звук
    private lateinit var mediaPlayer: android.media.MediaPlayer

    // Автообновление
    private val updateHandler = Handler(Looper.getMainLooper())
    private var isUpdating = false

    // ===== ЗАДАЧА ДЛЯ ПЕРИОДИЧЕСКОГО ОБНОВЛЕНИЯ (каждые 5 секунд) =====
    private val updateRunnable = object : Runnable {
        override fun run() {
            if (isUpdating) {
                lifecycleScope.launch {
                    try {
                        val coopId = DataManager.getCurrentCoopIdOrDefault()
                        sensorManager.loadSensors(coopId)
                        updateCacheIndicator()
                    } catch (e: Exception) {
                        ErrorHandler.handle(requireContext(), e, "⚠️ Ошибка обновления")
                    }
                }
                updateHandler.postDelayed(this, 5000)
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_dashboard, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Инициализация
        sensorManager = SensorManager(requireContext())
        coopManager = CoopManager(requireContext())

        sensorsContainer = view.findViewById(R.id.sensorsContainer)
        warningMessage = view.findViewById(R.id.warningMessage)
        chickenRun = view.findViewById(R.id.chickenRun)

        mediaPlayer = android.media.MediaPlayer.create(requireContext(), R.raw.chicken_hurt1)

        // ===== КНОПКА "СОБРАТЬ ЯЙЦА" =====
        val collectBtn = view.findViewById<Button>(R.id.collectEggsBtn)
        collectBtn.setOnClickListener {
            if (::mediaPlayer.isInitialized) {
                mediaPlayer.start()
            }
            startEggLayingAnimation()

            lifecycleScope.launch {
                try {
                    val collected = sensorManager.collectEggs()
                    if (collected > 0) {
                        Toast.makeText(requireContext(), "🥚 Собрано $collected яиц!", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(requireContext(), "📭 Нет яиц для сбора", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    ErrorHandler.handle(requireContext(), e, "❌ Ошибка сбора яиц")
                }
            }
        }

        // ===== ПЕРВИЧНАЯ ЗАГРУЗКА =====
        lifecycleScope.launch {
            try {
                val coopId = DataManager.getCurrentCoopIdOrDefault()
                sensorManager.loadSensors(coopId)
                sensorManager.sensors.collect { sensors ->
                    updateSensorsUI(sensors)
                    updateCacheIndicator()
                }
            } catch (e: Exception) {
                ErrorHandler.handle(requireContext(), e, "⚠️ Не удалось загрузить датчики")
            }
        }

        // ===== АВТООБНОВЛЕНИЕ ПРИ СМЕНЕ КУРЯТНИКА =====
        lifecycleScope.launch {
            coopManager.selectedCoopId.collect { coopId ->
                if (coopId != null) {
                    try {
                        sensorManager.loadSensors(coopId)
                        updateCacheIndicator()
                    } catch (e: Exception) {
                        ErrorHandler.handle(requireContext(), e, "⚠️ Ошибка загрузки")
                    }
                }
            }
        }

        // ===== ЗАПУСК АВТООБНОВЛЕНИЯ (каждые 5 секунд) =====
        isUpdating = true
        updateRunnable.run()
    }

    // ===== ИНДИКАТОР КЭША =====

    /**
     * Показывает пользователю, откуда взяты данные (сервер или кэш)
     */
    private fun updateCacheIndicator() {
        val isFromCache = sensorManager.isDataFromCache()
        val lastUpdate = DataManager.getLastUpdateTimeString()

        warningMessage.visibility = View.VISIBLE

        warningMessage.text = if (isFromCache) {
            "📦 Данные из кэша (обновлено: $lastUpdate)"
        } else {
            if (DataManager.isDataFresh()) {
                "🟢 Данные с сервера (обновлено: $lastUpdate)"
            } else {
                "🔄 Данные загружаются..."
            }
        }

        warningMessage.setTextColor(
            if (isFromCache) {
                ContextCompat.getColor(requireContext(), android.R.color.holo_orange_dark)
            } else {
                ContextCompat.getColor(requireContext(), android.R.color.holo_green_dark)
            }
        )
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

    // ===== ОБНОВЛЕНИЕ UI =====

    private fun updateSensorsUI(sensors: List<Sensor>) {
        sensorsContainer.removeAllViews()

        val water = sensors.find { it.type == SensorType.WATER }
        val feed = sensors.find { it.type == SensorType.FEED }
        val temp = sensors.find { it.type == SensorType.TEMPERATURE }
        val heating = sensors.find { it.type == SensorType.HEATING }
        val air = sensors.find { it.type == SensorType.AIR_QUALITY }
        val eggs = sensors.find { it.type == SensorType.EGG_COUNT }

        if (water != null && feed != null) {
            sensorsContainer.addView(createDoubleRow(water, feed))
        }

        if (temp != null && heating != null) {
            sensorsContainer.addView(createTempHeatingRow(temp, heating))
        }

        if (air != null) {
            sensorsContainer.addView(createSingleRow(air))
        }

        if (eggs != null) {
            sensorsContainer.addView(createEggsRow(eggs))
        }
    }

    // ===== СОЗДАНИЕ КАРТОЧЕК =====

    private fun createDoubleRow(sensor1: Sensor, sensor2: Sensor): LinearLayout {
        val row = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 16 }
        }
        row.addView(createSensorCard(sensor1, true))
        row.addView(createSensorCard(sensor2, false))
        return row
    }

    private fun createTempHeatingRow(tempSensor: Sensor, heatingSensor: Sensor): LinearLayout {
        val row = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 16 }
        }
        row.addView(createSensorCard(tempSensor, true))
        row.addView(createHeatingCard(heatingSensor, false))
        return row
    }

    private fun createSingleRow(sensor: Sensor): LinearLayout {
        val row = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 16 }
        }
        row.addView(createSensorCard(sensor, true))
        return row
    }

    private fun createHeatingCard(sensor: Sensor, isLeft: Boolean = false): CardView {
        val card = layoutInflater.inflate(R.layout.item_sensor, sensorsContainer, false) as CardView
        val icon = card.findViewById<TextView>(R.id.sensorIcon)
        val name = card.findViewById<TextView>(R.id.sensorName)
        val value = card.findViewById<TextView>(R.id.sensorValue)
        val progress = card.findViewById<ProgressBar>(R.id.sensorProgress)

        icon.text = "🔥"
        name.text = translateSensorName(sensor)
        progress.visibility = View.GONE

        // Статус отопления зависит от температуры (обновляется автоматически)
        lifecycleScope.launch {
            sensorManager.sensors.collect { sensors ->
                val tempSensor = sensors.find { it.type == SensorType.TEMPERATURE }
                val isHeatingOn = tempSensor?.currentValue?.let { it < 19 } ?: false
                value.text = if (isHeatingOn) getString(R.string.on) else getString(R.string.off)
                value.setTextColor(
                    if (isHeatingOn) ContextCompat.getColor(requireContext(), android.R.color.holo_red_dark)
                    else ContextCompat.getColor(requireContext(), android.R.color.darker_gray)
                )
            }
        }

        val layoutParams = card.layoutParams as LinearLayout.LayoutParams
        layoutParams.weight = 1f
        layoutParams.width = 0
        if (!isLeft) layoutParams.marginStart = 8
        card.layoutParams = layoutParams

        return card
    }

    private fun createEggsRow(sensor: Sensor): LinearLayout {
        val row = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 16 }
        }

        val card = layoutInflater.inflate(R.layout.item_eggs, sensorsContainer, false) as CardView
        val eggsValue = card.findViewById<TextView>(R.id.eggsValue)
        val eggProgress = card.findViewById<TextView>(R.id.eggProgressText)

        eggsValue.text = String.format("%.0f шт", sensor.currentValue)
        val percent = ((sensor.currentValue / sensor.maxValue) * 100).coerceIn(0f, 100f).toInt()
        eggProgress.text = "$percent%"

        row.addView(card)
        return row
    }

    private fun createSensorCard(sensor: Sensor, isLeft: Boolean = true): CardView {
        val card = layoutInflater.inflate(R.layout.item_sensor, sensorsContainer, false) as CardView
        val icon = card.findViewById<TextView>(R.id.sensorIcon)
        val name = card.findViewById<TextView>(R.id.sensorName)
        val value = card.findViewById<TextView>(R.id.sensorValue)
        val progress = card.findViewById<ProgressBar>(R.id.sensorProgress)

        // Иконка по типу датчика
        icon.text = when (sensor.type) {
            SensorType.WATER -> "💧"
            SensorType.FEED -> "🌽"
            SensorType.TEMPERATURE -> "🌡️"
            SensorType.AIR_QUALITY -> "😷"
            else -> "📊"
        }

        name.text = translateSensorName(sensor)

        val intValue = sensor.currentValue.toInt()
        value.text = "$intValue${sensor.unit}"

        // Прогресс-бар (для температуры не показываем)
        if (sensor.type == SensorType.TEMPERATURE) {
            progress.visibility = View.GONE
        } else {
            progress.visibility = View.VISIBLE
            val progressPercent = ((sensor.currentValue - sensor.minValue) / (sensor.maxValue - sensor.minValue) * 100)
                .coerceIn(0f, 100f)
            progress.progress = progressPercent.toInt()
        }

        val layoutParams = card.layoutParams as LinearLayout.LayoutParams
        layoutParams.weight = 1f
        layoutParams.width = 0
        layoutParams.marginEnd = if (isLeft) 8 else 0
        layoutParams.marginStart = if (isLeft) 0 else 8
        card.layoutParams = layoutParams

        return card
    }

    // ===== АНИМАЦИЯ КУРИЦЫ =====

    private fun startEggLayingAnimation() {
        chickenRun.visibility = View.VISIBLE
        chickenRun.setImageResource(R.drawable.chicken_run)
        val anim = chickenRun.drawable as AnimationDrawable
        anim.start()

        val displayMetrics = resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels

        chickenRun.animate()
            .translationX((screenWidth - chickenRun.width).toFloat())
            .setDuration(1500)
            .withEndAction {
                anim.stop()
                chickenRun.visibility = View.GONE
                chickenRun.translationX = 0f
            }
            .start()
    }

    // ===== ЖИЗНЕННЫЙ ЦИКЛ =====

    override fun onDestroy() {
        super.onDestroy()
        isUpdating = false
        updateHandler.removeCallbacks(updateRunnable)
        if (::mediaPlayer.isInitialized) {
            mediaPlayer.release()
        }
    }
}