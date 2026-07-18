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
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.smartcoop.data.SmartCoopRepository
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.*
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.github.mikephil.charting.formatter.ValueFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class AnalyticsFragment : Fragment() {

    private lateinit var repository: SmartCoopRepository
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

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_analytics, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        repository = SmartCoopRepository(requireContext())

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

        btnWeek.setOnClickListener { loadPeriod("week") }
        btnMonth.setOnClickListener { loadPeriod("month") }
        btnYear.setOnClickListener { loadPeriod("year") }
        btnCalendar.setOnClickListener { showDatePicker() }

        loadPeriod("week")
    }

    private fun showDatePicker() {
        val calendar = Calendar.getInstance()
        DatePickerDialog(requireContext(), { _, year, month, dayOfMonth ->
            val selectedDate = Calendar.getInstance()
            selectedDate.set(year, month, dayOfMonth)
            loadCustomWeek(selectedDate)
        }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun loadCustomWeek(selectedDate: Calendar) {
        lifecycleScope.launch(Dispatchers.IO) {
            val calendar = Calendar.getInstance()
            calendar.time = selectedDate.time

            val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
            val daysToMonday = if (dayOfWeek == Calendar.SUNDAY) 6 else dayOfWeek - Calendar.MONDAY
            calendar.add(Calendar.DAY_OF_MONTH, -daysToMonday)
            calendar.set(Calendar.HOUR_OF_DAY, 0)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)

            val weekStart = calendar.timeInMillis
            val weekEnd = weekStart + (7 * 24 * 60 * 60 * 1000)

            val eggs = repository.getEggsForDateRange(weekStart, weekEnd)
            val temps = repository.getTemperaturesForDateRange(weekStart, weekEnd)

            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val selectedDateStr = dateFormat.format(selectedDate.time)

            withContext(Dispatchers.Main) {
                updateWeekCharts(eggs, temps, selectedDateStr)
                tempTitle.visibility = View.VISIBLE
                tempChart.visibility = View.VISIBLE
            }
        }
    }

    private fun loadPeriod(period: String) {
        when (period) {
            "week" -> {
                lifecycleScope.launch(Dispatchers.IO) {
                    val eggs = repository.getEggsForLastDays(7)
                    val temps = repository.getTemperaturesForLastDays(7)
                    withContext(Dispatchers.Main) {
                        updateWeekCharts(eggs, temps, "")  // ← добавили ""
                        tempTitle.visibility = View.VISIBLE
                        tempChart.visibility = View.VISIBLE
                    }
                }
            }
            "month" -> {
                lifecycleScope.launch(Dispatchers.IO) {
                    val eggsDaily = repository.getEggsForLastDays(30)
                    val tempsDaily = repository.getTemperaturesForLastDays(30)
                    val weeksEggs = groupByWeekWithDates(eggsDaily)
                    val weeksTemps = groupByWeekTempWithDates(tempsDaily)
                    withContext(Dispatchers.Main) {
                        updateMonthCharts(weeksEggs, weeksTemps)
                        tempTitle.visibility = View.VISIBLE
                        tempChart.visibility = View.VISIBLE
                    }
                }
            }
            "year" -> {
                lifecycleScope.launch(Dispatchers.IO) {
                    val eggs = repository.getEggsForLastYearByMonth()
                    withContext(Dispatchers.Main) {
                        updateYearChart(eggs)
                        tempTitle.visibility = View.GONE
                        tempChart.visibility = View.GONE
                    }
                }
            }
        }
    }

    private fun groupByWeekWithDates(data: List<Pair<String, Int>>): List<Pair<String, Int>> {
        val weeks = mutableListOf<Pair<String, Int>>()
        var weekSum = 0
        var currentWeek = -1
        var weekStartDate = ""

        data.forEachIndexed { index, (date, count) ->
            val week = index / 7
            if (week != currentWeek) {
                if (currentWeek != -1) {
                    weeks.add(Pair(weekStartDate, weekSum))
                }
                currentWeek = week
                weekSum = 0
                weekStartDate = date
            }
            weekSum += count
        }
        if (weekSum > 0) {
            weeks.add(Pair(weekStartDate, weekSum))
        }
        return weeks
    }

    private fun groupByWeekTempWithDates(data: List<Pair<String, Float>>): List<Pair<String, Float>> {
        val weeks = mutableListOf<Pair<String, Float>>()
        var weekSum = 0f
        var weekCount = 0
        var currentWeek = -1
        var weekStartDate = ""

        data.forEachIndexed { index, (date, temp) ->
            val week = index / 7
            if (week != currentWeek) {
                if (currentWeek != -1) {
                    weeks.add(Pair(weekStartDate, weekSum / weekCount))
                }
                currentWeek = week
                weekSum = 0f
                weekCount = 0
                weekStartDate = date
            }
            weekSum += temp
            weekCount++
        }
        if (weekCount > 0) {
            weeks.add(Pair(weekStartDate, weekSum / weekCount))
        }
        return weeks
    }

    private fun updateWeekCharts(eggs: List<Pair<String, Int>>, temps: List<Pair<String, Float>>, selectedDate: String) {
        val isDarkTheme = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        val textColor = if (isDarkTheme) Color.WHITE else Color.BLACK
        val axisColor = if (isDarkTheme) Color.LTGRAY else Color.DKGRAY
        val gridColor = if (isDarkTheme) Color.DKGRAY else Color.LTGRAY

        val fullDates = eggs.map { it.first }
        val shortLabels = fullDates.map { getShortDayName(it) }

        // ========== ГРАФИК ЯИЦ ==========
        val barEntries = eggs.mapIndexed { index, data -> BarEntry(index.toFloat(), data.second.toFloat()) }
        val barDataSet = BarDataSet(barEntries, "Яйца")
        barDataSet.color = Color.parseColor("#FF9800")
        barDataSet.valueTextColor = textColor
        barDataSet.valueTextSize = 12f
        barDataSet.valueFormatter = object : ValueFormatter() {
            override fun getFormattedValue(value: Float): String = value.toInt().toString()
        }

        barChart.data = BarData(barDataSet)
        barChart.xAxis.valueFormatter = IndexAxisValueFormatter(shortLabels)
        barChart.xAxis.position = XAxis.XAxisPosition.BOTTOM
        barChart.xAxis.textColor = axisColor
        barChart.axisLeft.textColor = axisColor
        barChart.axisLeft.gridColor = gridColor
        barChart.axisRight.isEnabled = false
        barChart.description.isEnabled = false
        barChart.legend.textColor = textColor
        barChart.animateY(1000)
        barChart.invalidate()

        // ========== ГРАФИК ТЕМПЕРАТУРЫ ==========
        val tempEntries = temps.mapIndexed { index, data -> Entry(index.toFloat(), data.second) }
        val tempDataSet = LineDataSet(tempEntries, "Температура, °C")
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
        tempChart.xAxis.valueFormatter = IndexAxisValueFormatter(shortLabels)
        tempChart.xAxis.position = XAxis.XAxisPosition.BOTTOM
        tempChart.xAxis.textColor = axisColor
        tempChart.axisLeft.textColor = axisColor
        tempChart.axisLeft.gridColor = gridColor
        tempChart.axisRight.isEnabled = false
        tempChart.description.isEnabled = false
        tempChart.legend.textColor = textColor
        tempChart.animateXY(1000, 1000)
        tempChart.invalidate()

        // ========== ПОДЧЕРКИВАНИЕ ВЫБРАННОГО ДНЯ ==========
        if (selectedDate.isNotEmpty()) {
            val index = fullDates.indexOf(selectedDate)
            if (index >= 0) {
                val newLabels = shortLabels.toMutableList()
                newLabels[index] = "${shortLabels[index]}\n▼"
                barChart.xAxis.valueFormatter = IndexAxisValueFormatter(newLabels)
                tempChart.xAxis.valueFormatter = IndexAxisValueFormatter(newLabels)
                barChart.invalidate()
                tempChart.invalidate()
            }
        }

        // ========== СТАТИСТИКА ==========
        val total = eggs.sumOf { it.second }
        val average = if (eggs.isNotEmpty()) total / eggs.size else 0
        val best = eggs.maxByOrNull { it.second }?.second ?: 0

        totalText.text = getString(R.string.stat_for_period, total)
        averageText.text = getString(R.string.stat_average, average)
        bestDayText.text = getString(R.string.stat_best_day, best)
    }

    private fun updateMonthCharts(eggs: List<Pair<String, Int>>, temps: List<Pair<String, Float>>) {
        val isDarkTheme = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        val textColor = if (isDarkTheme) Color.WHITE else Color.BLACK
        val axisColor = if (isDarkTheme) Color.LTGRAY else Color.DKGRAY
        val gridColor = if (isDarkTheme) Color.DKGRAY else Color.LTGRAY

        val labels = eggs.map { formatDateRange(it.first) }

        val entries = eggs.mapIndexed { index, data -> BarEntry(index.toFloat(), data.second.toFloat()) }
        val barDataSet = BarDataSet(entries, "Яйца (за неделю)")
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
        barChart.axisLeft.textColor = axisColor
        barChart.axisLeft.gridColor = gridColor
        barChart.axisRight.isEnabled = false
        barChart.description.isEnabled = false
        barChart.legend.textColor = textColor
        barChart.animateY(1000)
        barChart.invalidate()

        val tempEntries = temps.mapIndexed { index, data -> Entry(index.toFloat(), data.second) }
        val tempDataSet = LineDataSet(tempEntries, "Температура, °C")
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
        tempChart.axisLeft.textColor = axisColor
        tempChart.axisLeft.gridColor = gridColor
        tempChart.axisRight.isEnabled = false
        tempChart.description.isEnabled = false
        tempChart.legend.textColor = textColor
        tempChart.animateXY(1000, 1000)
        tempChart.invalidate()

        val total = eggs.sumOf { it.second }
        val average = if (eggs.isNotEmpty()) total / eggs.size else 0
        val best = eggs.maxByOrNull { it.second }?.second ?: 0

        totalText.text = "📊 За месяц: $total яиц"
        averageText.text = "📈 В среднем: $average яиц/неделю"
        bestDayText.text = "🏆 Лучшая неделя: $best яиц"
    }

    private fun updateYearChart(eggs: List<Pair<String, Int>>) {
        tempTitle.visibility = View.GONE
        tempChart.visibility = View.GONE

        val isDarkTheme = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        val textColor = if (isDarkTheme) Color.WHITE else Color.BLACK
        val axisColor = if (isDarkTheme) Color.LTGRAY else Color.DKGRAY

        val labels = eggs.map { it.first }
        val entries = eggs.mapIndexed { index, data -> BarEntry(index.toFloat(), data.second.toFloat()) }

        val barDataSet = BarDataSet(entries, "Яйца")
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
        barChart.axisLeft.textColor = axisColor
        barChart.axisRight.isEnabled = false
        barChart.description.isEnabled = false
        barChart.legend.textColor = textColor
        barChart.animateY(1000)
        barChart.invalidate()

        val total = eggs.sumOf { it.second }
        val average = if (eggs.isNotEmpty()) total / eggs.size else 0
        val best = eggs.maxByOrNull { it.second }?.second ?: 0

        totalText.text = "📊 За год: $total яиц"
        averageText.text = "📈 В среднем: $average яиц/месяц"
        bestDayText.text = "🏆 Лучший месяц: $best яиц"
    }

    private fun getShortDayName(dateStr: String): String {
        val calendar = Calendar.getInstance()
        val format = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        return try {
            calendar.time = format.parse(dateStr) ?: Date()
            when (calendar.get(Calendar.DAY_OF_WEEK)) {
                Calendar.MONDAY -> "Пн"
                Calendar.TUESDAY -> "Вт"
                Calendar.WEDNESDAY -> "Ср"
                Calendar.THURSDAY -> "Чт"
                Calendar.FRIDAY -> "Пт"
                Calendar.SATURDAY -> "Сб"
                Calendar.SUNDAY -> "Вс"
                else -> dateStr.substring(5)
            }
        } catch (e: Exception) {
            dateStr.substring(5)
        }
    }

    private fun formatDateRange(dateStr: String): String {
        val format = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        return try {
            val date = format.parse(dateStr) ?: Date()
            val calendar = Calendar.getInstance()
            calendar.time = date
            val startDay = calendar.get(Calendar.DAY_OF_MONTH)
            calendar.add(Calendar.DAY_OF_MONTH, 6)
            val endDay = calendar.get(Calendar.DAY_OF_MONTH)
            "$startDay-$endDay"
        } catch (e: Exception) {
            dateStr
        }
    }
}