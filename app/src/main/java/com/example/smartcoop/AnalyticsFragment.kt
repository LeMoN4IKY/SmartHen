package com.example.smartcoop

import android.app.DatePickerDialog
import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.*
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.github.mikephil.charting.formatter.ValueFormatter
import com.github.mikephil.charting.highlight.Highlight
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

/**
 * Фрагмент аналитики — графики яйценоскости и температуры
 */
class AnalyticsFragment : Fragment() {

    // UI элементы
    private lateinit var btnWeek: Button
    private lateinit var btnMonth: Button
    private lateinit var btnYear: Button
    private lateinit var btnCalendar: Button
    private lateinit var barChart: BarChart
    private lateinit var tempChart: LineChart
    private lateinit var totalText: TextView
    private lateinit var averageText: TextView
    private lateinit var bestDayText: TextView
    private lateinit var tempTitle: TextView

    // Состояние
    private var currentPeriod = "week"
    private var selectedDate: Calendar = Calendar.getInstance()
    private val api = RetrofitHelper.api

    private val TEMP_SENSOR_ID = "sensor_temp"
    private var currentDates: List<String> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_analytics, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Привязка UI
        btnWeek = view.findViewById(R.id.btnWeek)
        btnMonth = view.findViewById(R.id.btnMonth)
        btnYear = view.findViewById(R.id.btnYear)
        btnCalendar = view.findViewById(R.id.btnCalendar)
        barChart = view.findViewById(R.id.barChart)
        tempChart = view.findViewById(R.id.tempChart)
        totalText = view.findViewById(R.id.totalEggs)
        averageText = view.findViewById(R.id.averageEggs)
        bestDayText = view.findViewById(R.id.bestDay)
        tempTitle = view.findViewById(R.id.tempTitle)

        // Обработчики кнопок
        btnWeek.setOnClickListener {
            currentPeriod = "week"
            selectedDate = Calendar.getInstance()
            loadPeriod("week")
        }
        btnMonth.setOnClickListener {
            currentPeriod = "month"
            selectedDate = Calendar.getInstance()
            loadPeriod("month")
        }
        btnYear.setOnClickListener {
            currentPeriod = "year"
            selectedDate = Calendar.getInstance()
            loadPeriod("year")
        }
        btnCalendar.setOnClickListener { showDatePicker() }

        // Загрузка по умолчанию — текущая неделя
        loadPeriod("week")
    }

    /**
     * Диалог выбора даты
     */
    private fun showDatePicker() {
        DatePickerDialog(
            requireContext(),
            { _, year, month, dayOfMonth ->
                selectedDate.set(year, month, dayOfMonth)
                loadCustomWeek(selectedDate)
            },
            selectedDate.get(Calendar.YEAR),
            selectedDate.get(Calendar.MONTH),
            selectedDate.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    /**
     * Загрузка произвольной недели
     */
    private fun loadCustomWeek(selectedDate: Calendar) {
        lifecycleScope.launch(Dispatchers.IO) {
            val coopId = DataManager.getCurrentCoopIdOrDefault()
            val dates = getWeekDates(selectedDate)
            currentDates = dates
            val eggs = getEggsForDates(coopId, dates)
            val temps = getTemperaturesForDates(coopId, TEMP_SENSOR_ID, dates)
            withContext(Dispatchers.Main) {
                updateCharts(eggs, temps, dates, "week")
                highlightSelectedDay(selectedDate, dates)
            }
        }
    }

    /**
     * Загрузка периода: неделя/месяц/год
     */
    private fun loadPeriod(period: String) {
        currentPeriod = period
        val coopId = DataManager.getCurrentCoopIdOrDefault()

        when (period) {
            "week" -> {
                lifecycleScope.launch(Dispatchers.IO) {
                    val dates = getLastDays(7)
                    currentDates = dates
                    val eggs = getEggsForDates(coopId, dates)
                    val temps = getTemperaturesForDates(coopId, TEMP_SENSOR_ID, dates)
                    withContext(Dispatchers.Main) {
                        updateCharts(eggs, temps, dates, "week")
                        clearHighlight()
                        tempTitle.visibility = View.VISIBLE
                        tempChart.visibility = View.VISIBLE
                    }
                }
            }
            "month" -> {
                lifecycleScope.launch(Dispatchers.IO) {
                    val dates = getLastDays(30)
                    currentDates = dates
                    val eggs = getEggsForDates(coopId, dates)
                    val temps = getTemperaturesForDates(coopId, TEMP_SENSOR_ID, dates)
                    withContext(Dispatchers.Main) {
                        updateCharts(eggs, temps, dates, "month")
                        clearHighlight()
                        tempTitle.visibility = View.VISIBLE
                        tempChart.visibility = View.VISIBLE
                    }
                }
            }
            "year" -> {
                lifecycleScope.launch(Dispatchers.IO) {
                    val monthlyEggs = getMonthlyEggStats(coopId)
                    val monthlyTemps = getMonthlyTemperatures(coopId, TEMP_SENSOR_ID)
                    withContext(Dispatchers.Main) {
                        updateYearCharts(monthlyEggs, monthlyTemps)
                        clearHighlight()
                        tempTitle.visibility = View.VISIBLE
                        tempChart.visibility = View.VISIBLE
                    }
                }
            }
        }
    }

    // ===== ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ =====

    private fun getLastDays(days: Int): List<String> {
        val dates = mutableListOf<String>()
        val format = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val calendar = Calendar.getInstance()
        for (i in (days - 1) downTo 0) {
            calendar.time = Date()
            calendar.add(Calendar.DAY_OF_YEAR, -i)
            dates.add(format.format(calendar.time))
        }
        return dates
    }

    private fun getWeekDates(selected: Calendar): List<String> {
        val dates = mutableListOf<String>()
        val format = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val calendar = selected.clone() as Calendar
        calendar.add(Calendar.DAY_OF_YEAR, -6)
        for (i in 0 until 7) {
            dates.add(format.format(calendar.time))
            calendar.add(Calendar.DAY_OF_YEAR, 1)
        }
        return dates
    }

    private suspend fun getEggsForDates(coopId: String, dates: List<String>): Map<String, Int> {
        return try {
            val stats = api.getEggStats(coopId)
            val result = mutableMapOf<String, Int>()
            for (date in dates) {
                result[date] = stats.find { it.date == date }?.count ?: 0
            }
            result
        } catch (e: Exception) {
            e.printStackTrace()
            dates.associateWith { 0 }
        }
    }

    private suspend fun getTemperaturesForDates(
        coopId: String,
        sensorId: String,
        dates: List<String>
    ): Map<String, Float> {
        return try {
            val history = api.getSensorHistory(coopId, sensorId, dates.size + 1)
            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val result = mutableMapOf<String, Float>()
            for (date in dates) {
                val dayValues = history.filter {
                    val d = Date(it.timestamp * 1000)
                    dateFormat.format(d) == date
                }
                val avg = if (dayValues.isNotEmpty()) {
                    dayValues.map { it.value }.average().toFloat()
                } else {
                    0f
                }
                result[date] = avg
            }
            result
        } catch (e: Exception) {
            e.printStackTrace()
            dates.associateWith { 0f }
        }
    }

    private suspend fun getMonthlyEggStats(coopId: String): Map<String, Int> {
        return try {
            val stats = api.getEggStats(coopId)
            val monthFormat = SimpleDateFormat("yyyy-MM", Locale.getDefault())
            val result = mutableMapOf<String, Int>()
            val calendar = Calendar.getInstance()
            for (i in 11 downTo 0) {
                calendar.time = Date()
                calendar.add(Calendar.MONTH, -i)
                val monthKey = monthFormat.format(calendar.time)
                val monthTotal = stats.filter {
                    it.date.startsWith(monthKey)
                }.sumOf { it.count }
                result[getMonthName(monthKey)] = monthTotal
            }
            result
        } catch (e: Exception) {
            e.printStackTrace()
            emptyMap()
        }
    }

    private suspend fun getMonthlyTemperatures(coopId: String, sensorId: String): Map<String, Float> {
        return try {
            val history = api.getSensorHistory(coopId, sensorId, 365)
            val monthFormat = SimpleDateFormat("yyyy-MM", Locale.getDefault())
            val result = mutableMapOf<String, Float>()
            val calendar = Calendar.getInstance()
            for (i in 11 downTo 0) {
                calendar.time = Date()
                calendar.add(Calendar.MONTH, -i)
                val monthKey = monthFormat.format(calendar.time)
                val monthValues = history.filter {
                    val d = Date(it.timestamp * 1000)
                    monthFormat.format(d) == monthKey
                }.map { it.value }
                val avg = if (monthValues.isNotEmpty()) {
                    monthValues.average().toFloat()
                } else {
                    0f
                }
                result[getMonthName(monthKey)] = avg
            }
            result
        } catch (e: Exception) {
            e.printStackTrace()
            emptyMap()
        }
    }

    /**
     * Обновить графики (неделя/месяц)
     */
    private fun updateCharts(
        eggs: Map<String, Int>,
        temps: Map<String, Float>,
        dates: List<String>,
        period: String
    ) {
        val isDarkTheme = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        val textColor = if (isDarkTheme) Color.WHITE else Color.BLACK
        val axisColor = if (isDarkTheme) Color.LTGRAY else Color.DKGRAY
        val gridColor = if (isDarkTheme) Color.DKGRAY else Color.LTGRAY

        // Подписи для оси X
        val labels = if (period == "week") {
            dates.map { getShortDayName(it) }
        } else {
            dates.map { it.substring(5) }
        }

        // ===== ГРАФИК ЯИЦ =====
        val eggEntries = dates.mapIndexed { index, date ->
            BarEntry(index.toFloat(), eggs[date]?.toFloat() ?: 0f)
        }
        val barDataSet = BarDataSet(eggEntries, getString(R.string.eggs_chart_label))
        barDataSet.color = Color.parseColor("#FF9800")
        barDataSet.valueTextColor = textColor
        barDataSet.valueTextSize = 12f
        barDataSet.valueFormatter = object : ValueFormatter() {
            override fun getFormattedValue(value: Float): String = value.toInt().toString()
        }

        barChart.data = BarData(barDataSet)
        barChart.xAxis.valueFormatter = IndexAxisValueFormatter(labels)
        barChart.xAxis.position = XAxis.XAxisPosition.BOTTOM
        barChart.xAxis.textColor = axisColor
        barChart.xAxis.granularity = 1f
        barChart.xAxis.labelRotationAngle = -30f
        barChart.axisLeft.textColor = axisColor
        barChart.axisLeft.gridColor = gridColor
        barChart.axisRight.isEnabled = false
        barChart.description.isEnabled = false
        barChart.legend.textColor = textColor
        barChart.animateY(1000)
        barChart.invalidate()

        // ===== ГРАФИК ТЕМПЕРАТУРЫ =====
        val tempEntries = dates.mapIndexed { index, date ->
            Entry(index.toFloat(), temps[date] ?: 0f)
        }
        val tempDataSet = LineDataSet(tempEntries, getString(R.string.temperature_chart_label))
        tempDataSet.color = Color.parseColor("#2196F3")
        tempDataSet.setCircleColor(Color.parseColor("#2196F3"))
        tempDataSet.circleRadius = 4f
        tempDataSet.setDrawValues(true)
        tempDataSet.valueTextColor = textColor
        tempDataSet.valueTextSize = 10f
        tempDataSet.valueFormatter = object : ValueFormatter() {
            override fun getFormattedValue(value: Float): String = String.format("%.1f", value)
        }
        tempDataSet.lineWidth = 2f
        tempDataSet.setDrawFilled(true)
        tempDataSet.fillColor = Color.parseColor("#802196F3")
        tempDataSet.fillAlpha = 80

        tempChart.data = LineData(tempDataSet)
        tempChart.xAxis.valueFormatter = IndexAxisValueFormatter(labels)
        tempChart.xAxis.position = XAxis.XAxisPosition.BOTTOM
        tempChart.xAxis.textColor = axisColor
        tempChart.xAxis.granularity = 1f
        tempChart.xAxis.labelRotationAngle = -30f
        tempChart.axisLeft.textColor = axisColor
        tempChart.axisLeft.gridColor = gridColor
        tempChart.axisRight.isEnabled = false
        tempChart.description.isEnabled = false
        tempChart.legend.textColor = textColor
        tempChart.animateXY(1000, 1000)
        tempChart.invalidate()

        // ===== СТАТИСТИКА =====
        val total = eggs.values.sum()
        val average = if (eggs.isNotEmpty()) total / eggs.size else 0
        val best = eggs.values.maxOrNull() ?: 0

        val periodText = when (period) {
            "week" -> "неделю"
            "month" -> "месяц"
            else -> "период"
        }
        totalText.text = "📊 За $periodText: $total яиц"
        averageText.text = "📈 В среднем: $average яиц/день"
        bestDayText.text = "🏆 Лучший день: $best яиц"
    }

    /**
     * Обновить графики для года
     */
    private fun updateYearCharts(eggs: Map<String, Int>, temps: Map<String, Float>) {
        val isDarkTheme = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        val textColor = if (isDarkTheme) Color.WHITE else Color.BLACK
        val axisColor = if (isDarkTheme) Color.LTGRAY else Color.DKGRAY
        val gridColor = if (isDarkTheme) Color.DKGRAY else Color.LTGRAY

        val monthNames = listOf("Янв", "Фев", "Мар", "Апр", "Май", "Июн", "Июл", "Авг", "Сен", "Окт", "Ноя", "Дек")

        // ===== ГРАФИК ЯИЦ =====
        val eggEntries = monthNames.mapIndexed { index, month ->
            BarEntry(index.toFloat(), eggs[month]?.toFloat() ?: 0f)
        }
        val barDataSet = BarDataSet(eggEntries, getString(R.string.eggs_chart_label))
        barDataSet.color = Color.parseColor("#FF9800")
        barDataSet.valueTextColor = textColor
        barDataSet.valueTextSize = 12f
        barDataSet.valueFormatter = object : ValueFormatter() {
            override fun getFormattedValue(value: Float): String = value.toInt().toString()
        }

        barChart.data = BarData(barDataSet)
        barChart.xAxis.valueFormatter = IndexAxisValueFormatter(monthNames)
        barChart.xAxis.position = XAxis.XAxisPosition.BOTTOM
        barChart.xAxis.textColor = axisColor
        barChart.xAxis.granularity = 1f
        barChart.axisLeft.textColor = axisColor
        barChart.axisLeft.gridColor = gridColor
        barChart.axisRight.isEnabled = false
        barChart.description.isEnabled = false
        barChart.legend.textColor = textColor
        barChart.animateY(1000)
        barChart.invalidate()

        // ===== ГРАФИК ТЕМПЕРАТУРЫ =====
        val tempEntries = monthNames.mapIndexed { index, month ->
            Entry(index.toFloat(), temps[month] ?: 0f)
        }
        val tempDataSet = LineDataSet(tempEntries, getString(R.string.temperature_chart_label))
        tempDataSet.color = Color.parseColor("#2196F3")
        tempDataSet.setCircleColor(Color.parseColor("#2196F3"))
        tempDataSet.circleRadius = 4f
        tempDataSet.setDrawValues(true)
        tempDataSet.valueTextColor = textColor
        tempDataSet.valueTextSize = 10f
        tempDataSet.valueFormatter = object : ValueFormatter() {
            override fun getFormattedValue(value: Float): String = String.format("%.1f", value)
        }
        tempDataSet.lineWidth = 2f
        tempDataSet.setDrawFilled(true)
        tempDataSet.fillColor = Color.parseColor("#802196F3")
        tempDataSet.fillAlpha = 80

        tempChart.data = LineData(tempDataSet)
        tempChart.xAxis.valueFormatter = IndexAxisValueFormatter(monthNames)
        tempChart.xAxis.position = XAxis.XAxisPosition.BOTTOM
        tempChart.xAxis.textColor = axisColor
        tempChart.xAxis.granularity = 1f
        tempChart.axisLeft.textColor = axisColor
        tempChart.axisLeft.gridColor = gridColor
        tempChart.axisRight.isEnabled = false
        tempChart.description.isEnabled = false
        tempChart.legend.textColor = textColor
        tempChart.animateXY(1000, 1000)
        tempChart.invalidate()

        // ===== СТАТИСТИКА =====
        val total = eggs.values.sum()
        val average = if (eggs.isNotEmpty()) total / eggs.size else 0
        val best = eggs.values.maxOrNull() ?: 0

        totalText.text = "📊 За год: $total яиц"
        averageText.text = "📈 В среднем: $average яиц/месяц"
        bestDayText.text = "🏆 Лучший месяц: $best яиц"
    }

    /**
     * Подсветка выбранного дня — тёмно-зелёный цвет
     */
    private fun highlightSelectedDay(selected: Calendar, dates: List<String>) {
        val format = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val selectedDateStr = format.format(selected.time)
        val index = dates.indexOf(selectedDateStr)

        if (index >= 0) {
            // Столбец яиц — тёмно-зелёный
            val barDataSet = barChart.data?.getDataSetByIndex(0) as? BarDataSet
            barDataSet?.let { dataset ->
                val colors = mutableListOf<Int>()
                for (i in 0 until dates.size) {
                    colors.add(if (i == index) Color.parseColor("#2E7D32") else Color.parseColor("#FF9800"))
                }
                dataset.colors = colors
                barChart.invalidate()
            }

            // Точка температуры — тёмно-зелёный
            val tempDataSet = tempChart.data?.getDataSetByIndex(0) as? LineDataSet
            tempDataSet?.let { dataset ->
                val circleColors = mutableListOf<Int>()
                for (i in 0 until dates.size) {
                    circleColors.add(if (i == index) Color.parseColor("#2E7D32") else Color.parseColor("#2196F3"))
                }
                dataset.setCircleColors(circleColors)
                tempChart.invalidate()
            }

            // Всплывающая подсказка
            val highlight = Highlight(index.toFloat(), 0, 0)
            barChart.highlightValue(highlight, true)
            tempChart.highlightValue(highlight, true)
        }
    }

    /**
     * Сбросить выделение
     */
    private fun clearHighlight() {
        // Возвращаем оранжевый цвет
        val barDataSet = barChart.data?.getDataSetByIndex(0) as? BarDataSet
        barDataSet?.let { dataset ->
            val colors = mutableListOf<Int>()
            for (i in 0 until currentDates.size) {
                colors.add(Color.parseColor("#FF9800"))
            }
            dataset.colors = colors
            barChart.invalidate()
        }

        // Возвращаем синий цвет
        val tempDataSet = tempChart.data?.getDataSetByIndex(0) as? LineDataSet
        tempDataSet?.let { dataset ->
            val circleColors = mutableListOf<Int>()
            for (i in 0 until currentDates.size) {
                circleColors.add(Color.parseColor("#2196F3"))
            }
            dataset.setCircleColors(circleColors)
            tempChart.invalidate()
        }

        // Убираем подсказки
        barChart.highlightValue(null)
        tempChart.highlightValue(null)
    }

    /**
     * Название дня недели на текущем языке
     */
    private fun getShortDayName(dateStr: String): String {
        val format = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        return try {
            val date = format.parse(dateStr) ?: Date()
            val calendar = Calendar.getInstance().apply { time = date }
            when (calendar.get(Calendar.DAY_OF_WEEK)) {
                Calendar.MONDAY -> getString(R.string.monday_short)
                Calendar.TUESDAY -> getString(R.string.tuesday_short)
                Calendar.WEDNESDAY -> getString(R.string.wednesday_short)
                Calendar.THURSDAY -> getString(R.string.thursday_short)
                Calendar.FRIDAY -> getString(R.string.friday_short)
                Calendar.SATURDAY -> getString(R.string.saturday_short)
                Calendar.SUNDAY -> getString(R.string.sunday_short)
                else -> dateStr.substring(5)
            }
        } catch (e: Exception) {
            dateStr.substring(5)
        }
    }

    private fun getMonthName(monthKey: String): String {
        return try {
            val parts = monthKey.split("-")
            val monthNum = parts[1].toInt()
            arrayOf("Янв", "Фев", "Мар", "Апр", "Май", "Июн",
                "Июл", "Авг", "Сен", "Окт", "Ноя", "Дек")[monthNum - 1]
        } catch (e: Exception) {
            monthKey
        }
    }
}