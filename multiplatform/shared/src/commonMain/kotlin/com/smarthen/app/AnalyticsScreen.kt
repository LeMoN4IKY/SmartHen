package com.smarthen.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

@Composable
fun AnalyticsScreen(viewModel: AnalyticsViewModel, ru: Boolean, modifier: Modifier = Modifier) {
    val ui by viewModel.state.collectAsState()
    var expanded by remember { mutableStateOf(false) }
    fun t(r: String, e: String) = if (ru) r else e
    fun metricName(type: String) = when (type) {
        "TEMPERATURE" -> t("Температура", "Temperature")
        "HUMIDITY" -> t("Влажность", "Humidity")
        "FEED" -> t("Корм", "Feed")
        "WATER" -> t("Вода", "Water")
        "AIR_QUALITY" -> t("Качество воздуха", "Air quality")
        "EGG_COUNT" -> t("Яйца в лотке", "Eggs in tray")
        "HEATING" -> t("Отопление", "Heating")
        else -> type
    }
    val metric = ui.sensors.firstOrNull { it.key() == ui.selected }
    val statistics = remember(ui.readings) { sampleStatistics(ui.readings) }
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Text(t("История показаний", "Measurement history"), style = MaterialTheme.typography.headlineLarge)
            Text(t("Изменения по времени измерения датчика", "Changes by the sensor’s measurement time"))
        }
        if (ui.house.isBlank()) item { Text(t("Выберите птичник на вкладке «Птичники».", "Choose a house in the Houses tab.")) }
        else {
            item {
                Box {
                    OutlinedButton(onClick = { expanded = true }, enabled = ui.sensors.isNotEmpty() && !ui.busy) { Text(metric?.let { "${metricName(it.type)} · ${it.sensor_id}" } ?: t("Выбрать датчик", "Choose sensor")) }
                    DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
                        ui.sensors.forEach { sensor -> DropdownMenuItem(text = { Text("${metricName(sensor.type)} · ${sensor.sensor_id} · ${sensor.device_id.take(8)}") }, onClick = { expanded = false; viewModel.select(sensor.key()) }) }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    HistoryPeriod.entries.forEach { period -> FilterChip(selected = ui.period == period, onClick = { viewModel.period(period) }, enabled = !ui.busy, label = { Text(when(period) { HistoryPeriod.DAY -> t("24 ч", "24 h"); HistoryPeriod.WEEK -> t("7 дней", "7 days"); HistoryPeriod.MONTH -> t("30 дней", "30 days") }) }) }
                }
                OutlinedButton(onClick = viewModel::refresh, enabled = !ui.busy) { Text(t("Обновить", "Refresh")) }
                if (ui.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            if (!ui.busy && ui.error == null && ui.readings.isEmpty()) item { Text(t("За выбранный период измерений нет.", "No measurements in the selected period.")) }
            if (statistics != null) item {
                val unit = ui.readings.first().unit
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("${metric?.sensor_id.orEmpty()} · $unit", style = MaterialTheme.typography.titleLarge)
                        MetricChart(ui.readings, ui.since, ui.until, t("График показаний: ${statistics.count} измерений. Минимум ${statistics.minimum}, максимум ${statistics.maximum} $unit.", "Measurement chart: ${statistics.count} samples. Minimum ${statistics.minimum}, maximum ${statistics.maximum} $unit."))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(t("${ui.period.seconds / 3600} ч назад", "${ui.period.seconds / 3600} h ago"), style = MaterialTheme.typography.bodySmall)
                            Text(t("Конец периода", "Period end"), style = MaterialTheme.typography.bodySmall)
                        }
                        Text(t("Минимум: ${statistics.minimum} $unit", "Minimum: ${statistics.minimum} $unit"))
                        Text(t("Максимум: ${statistics.maximum} $unit", "Maximum: ${statistics.maximum} $unit"))
                        Text(t("Среднее по измерениям: ${statistics.sampleMean} $unit", "Sample mean: ${statistics.sampleMean} $unit"))
                        Text(t("Загружено измерений: ${statistics.count}. Среднее не взвешено по времени.", "Loaded samples: ${statistics.count}. The mean is not time-weighted."), style = MaterialTheme.typography.bodySmall)
                        if (metric?.type == "EGG_COUNT") Text(t("Это число яиц в лотке, а не объём производства за период.", "This is the egg count in the tray, not production over this period."), style = MaterialTheme.typography.bodySmall)
                        if (ui.nextCursor != null) {
                            Text(t("Показана часть истории. Статистика рассчитана только по загруженным измерениям.", "Only part of the history is shown. Statistics cover loaded samples only."))
                            OutlinedButton(onClick = viewModel::loadMore, enabled = !ui.busy) { Text(t("Загрузить ещё", "Load more")) }
                        }
                    }
                }
            }
            ui.error?.let { error -> item { Text(if (error == AnalyticsError.SESSION_EXPIRED) t("Сессия истекла. Войдите снова.", "Session expired. Sign in again.") else t("Не удалось загрузить историю. Проверьте связь и повторите запрос.", "Could not load history. Check your connection and retry."), color = MaterialTheme.colorScheme.error) } }
        }
    }
}

@Composable
private fun MetricChart(rows: List<MetricReading>, since: Long, until: Long, description: String) {
    val line = MaterialTheme.colorScheme.primary
    val grid = MaterialTheme.colorScheme.outlineVariant
    Canvas(Modifier.fillMaxWidth().height(180.dp).semantics { contentDescription = description }) {
        if (rows.isEmpty()) return@Canvas
        val inset = 6.dp.toPx()
        val width = (size.width - inset * 2).coerceAtLeast(0f)
        val height = (size.height - inset * 2).coerceAtLeast(0f)
        // Halving first keeps normalization finite even for opposite extreme Double values.
        val minimum = rows.minOf { it.value / 2 }
        val maximum = rows.maxOf { it.value / 2 }
        fun point(row: MetricReading): Offset {
            val x = if (until <= since) .5 else ((row.observed_at - since).toDouble() / (until - since)).coerceIn(0.0, 1.0)
            val y = if (maximum == minimum) .5 else ((row.value / 2 - minimum) / (maximum - minimum)).coerceIn(0.0, 1.0)
            return Offset(inset + width * x.toFloat(), inset + height * (1 - y.toFloat()))
        }
        repeat(3) { index -> val y = inset + height * index / 2; drawLine(grid, Offset(inset, y), Offset(inset + width, y), 1.dp.toPx()) }
        if (rows.size == 1) drawCircle(line, 4.dp.toPx(), point(rows.first()))
        else {
            val path = Path()
            rows.forEachIndexed { index, row -> val point = point(row); if (index == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y) }
            drawPath(path, line, style = Stroke(2.dp.toPx()))
        }
    }
}
