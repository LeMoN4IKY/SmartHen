package com.smarthen.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

@Composable
fun HousesScreen(viewModel: HousesViewModel, ru: Boolean, selectedHouseId: String, onSelect: (HouseDto) -> Unit, modifier: Modifier = Modifier) {
    val ui by viewModel.state.collectAsState()
    var name by remember(ui.server) { mutableStateOf("") }
    var claimToken by remember(ui.server, selectedHouseId) { mutableStateOf("") }
    LaunchedEffect(ui.claimedDevice) { if (ui.claimedDevice != null) claimToken = "" }
    fun t(r: String, e: String) = if (ru) r else e
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Text(t("Мои птичники", "My houses"), style = MaterialTheme.typography.headlineLarge)
            Text(t("Выберите птичник для мониторинга или добавьте новый.", "Choose a house to monitor or add a new one."))
            if (ui.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            OutlinedButton(onClick = viewModel::refresh, enabled = !ui.busy) { Text(t("Обновить список", "Refresh list")) }
        }
        if (ui.houses.isEmpty() && !ui.busy && ui.error == null) item { Text(t("У вас пока нет птичников. Создайте первый ниже.", "No houses yet. Create your first one below.")) }
        items(ui.houses, key = { it.id }) { house ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(house.name, style = MaterialTheme.typography.titleLarge)
                    OutlinedButton(onClick = { onSelect(house) }, enabled = !ui.busy) { Text(if (house.id == selectedHouseId) t("Выбран", "Selected") else t("Выбрать", "Select")) }
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(t("Новый птичник", "New house"), style = MaterialTheme.typography.titleLarge)
                    OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), singleLine = true, enabled = !ui.busy, label = { Text(t("Название", "Name")) })
                    Button(onClick = { viewModel.create(name) }, enabled = !ui.busy && name.isNotBlank()) { Text(t("Добавить", "Add")) }
                }
            }
        }
        val selected = ui.houses.firstOrNull { it.id == selectedHouseId }
        if (selected != null) item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(t("Подключить контроллер", "Connect controller"), style = MaterialTheme.typography.titleLarge)
                    Text(t("Птичник: ${selected.name}. Введите одноразовый код привязки устройства.", "House: ${selected.name}. Enter the device’s one-time claim code."))
                    OutlinedTextField(claimToken, { claimToken = it }, Modifier.fillMaxWidth(), enabled = !ui.busy, label = { Text(t("Код привязки", "Claim code")) }, singleLine = true, visualTransformation = PasswordVisualTransformation())
                    Button(onClick = { viewModel.claim(selected.id, claimToken) }, enabled = !ui.busy && claimToken.isNotBlank()) { Text(t("Привязать устройство", "Claim device")) }
                    Text(t("Используйте код привязки, а не секретный ключ контроллера. Сканирование QR будет добавлено отдельно.", "Use the claim code, not the controller’s secret key. QR scanning will be added separately."), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        ui.claimedDevice?.let { device -> item { Text(t("Устройство «${device.name}» привязано. Ожидаем показания контроллера.", "Device “${device.name}” claimed. Waiting for controller readings.")) } }
        ui.error?.let { error -> item {
            Text(when (error) {
                HousesError.INVALID_INPUT -> t("Проверьте название птичника и код привязки.", "Check the house name and claim code.")
                HousesError.SESSION_EXPIRED -> t("Сессия истекла. Войдите снова в профиле.", "Session expired. Sign in again from your profile.")
                HousesError.INVALID_CLAIM -> t("Код недействителен, истёк или уже использован.", "The code is invalid, expired or already used.")
                HousesError.UNAVAILABLE -> t("Не удалось выполнить запрос. Проверьте связь. После сбоя добавления обновите список перед повтором.", "Request failed. Check your connection. After a failed creation, refresh the list before retrying.")
            }, color = MaterialTheme.colorScheme.error)
        } }
    }
}
