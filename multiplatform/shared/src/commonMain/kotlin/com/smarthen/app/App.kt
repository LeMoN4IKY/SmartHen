package com.smarthen.app

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.smarthen.domain.*
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

private enum class Page { HOME, SETTINGS, ACCOUNT, HOUSES, ANALYTICS }

@Composable
fun App(store: LocalStore, sessions: SessionStore, active: Boolean = true, allowLocalHttp: Boolean = false) {
    var language by remember { mutableStateOf(store.get("language") ?: "ru") }
    var theme by remember { mutableStateOf(store.get("theme") ?: "system") }
    var server by remember { mutableStateOf(store.get("server") ?: "") }
    var house by remember { mutableStateOf(store.get("house") ?: "") }
    var page by remember { mutableStateOf(Page.HOME) }
    val ru = language == "ru"
    fun t(russian: String, english: String) = if (ru) russian else english
    val dark = theme == "dark" || (theme == "system" && isSystemInDarkTheme())
    val colors = if (dark) darkColorScheme(
        primary = Color(0xFF9CDBB5), onPrimary = Color(0xFF123822),
        primaryContainer = Color(0xFF254B36), onPrimaryContainer = Color(0xFFD9F4DF),
        secondaryContainer = Color(0xFF304639), onSecondaryContainer = Color(0xFFDAEBDD),
        background = Color(0xFF101A16), onBackground = Color(0xFFE1EAE1),
        surface = Color(0xFF18241E), onSurface = Color(0xFFE1EAE1),
        surfaceContainer = Color(0xFF19271F), onSurfaceVariant = Color(0xFFBDCABD),
    )
        else lightColorScheme(
            primary = Color(0xFF256347), onPrimary = Color.White,
            primaryContainer = Color(0xFFDAEBDC), onPrimaryContainer = Color(0xFF163D29),
            secondaryContainer = Color(0xFFE3E9DF), onSecondaryContainer = Color(0xFF263D2D),
            background = Color(0xFFF4F6F0), onBackground = Color(0xFF1B291F),
            surface = Color.White, onSurface = Color(0xFF1B291F),
            surfaceContainer = Color(0xFFF0F3EB), onSurfaceVariant = Color(0xFF4D5D50),
        )
    val resources = viewModel<ApiResourcesViewModel> { ApiResourcesViewModel() }
    val accountClient = resources.client
    val account = viewModel<AccountViewModel> { AccountViewModel(ApiAuthRepository(accountClient, allowLocalHttp), sessions) }
    LaunchedEffect(server) { account.configure(server) }
    val accountState by account.state.collectAsState()
    val currentAccount = accountState.takeIf { it.server == server }?.user
    val monitorOwner = remember(server, accountState.sessionRevision) {
        object : androidx.lifecycle.ViewModelStoreOwner {
            override val viewModelStore = androidx.lifecycle.ViewModelStore()
        }
    }
    DisposableEffect(monitorOwner) { onDispose { monitorOwner.viewModelStore.clear() } }
    val monitor = viewModel<MonitorViewModel>(viewModelStoreOwner = monitorOwner) {
        MonitorViewModel(store, accountId = currentAccount?.id, allowLocalHttp = allowLocalHttp, tokenProvider = account::bearerToken)
    }
    val houses = viewModel<HousesViewModel> { HousesViewModel(ApiHousesRepository(accountClient, allowLocalHttp)) }
    LaunchedEffect(server, accountState.sessionRevision) { houses.configure(server, account.bearerToken()) }
    val analytics = viewModel<AnalyticsViewModel> { AnalyticsViewModel(ApiAnalyticsRepository(accountClient, allowLocalHttp)) }
    LaunchedEffect(server, house, accountState.sessionRevision) { analytics.configure(server, account.bearerToken(), house) }
    val analyticsState by analytics.state.collectAsState()
    LaunchedEffect(analyticsState.error) {
        analytics.expiredSessionToken()?.let { account.invalidateSession(analyticsState.server, it) }
    }
    val monitorState by monitor.state.collectAsState()
    val housesState by houses.state.collectAsState()
    LaunchedEffect(monitor, monitorState.sessionExpired, housesState.error, housesState.server) {
        val monitorToken = monitor.expiredSessionToken()
        val housesToken = houses.expiredSessionToken()
        val invalidated = (monitorToken != null && account.invalidateSession(monitorState.server, monitorToken)) ||
            (housesToken != null && account.invalidateSession(housesState.server, housesToken))
        if (invalidated) page = Page.ACCOUNT
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var resumed by remember(lifecycle) { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    DisposableEffect(lifecycle, monitor) {
        val observer = LifecycleEventObserver { _, _ ->
            resumed = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
            // Stop immediately on backgrounding, rather than waiting for recomposition.
            if (!resumed) monitor.pause()
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); monitor.pause() }
    }
    val effectiveActive = active && resumed && currentAccount != null
    DisposableEffect(monitor, effectiveActive, server, house) {
        monitor.configure(server, house, effectiveActive)
        onDispose { monitor.pause() }
    }
    // A configuration change can render before its effect runs. Never show the old house's data.
    val ui = monitorState.takeIf { it.server == server && it.house == house } ?: MonitorUiState()
    val readings = if (currentAccount != null) ui.readings else emptyList()
    val cached = ui.cached
    val loading = ui.loading
    val failed = ui.failed
    val now = ui.nowSeconds
    MaterialTheme(colorScheme = colors) {
        Scaffold(containerColor = colors.background, bottomBar = {
            NavigationBar {
                NavigationBarItem(selected = page == Page.HOME, onClick = { page = Page.HOME }, icon = { Text("◉") }, label = { Text(t("Обзор", "Overview")) })
                NavigationBarItem(selected = page == Page.ANALYTICS, onClick = { page = Page.ANALYTICS }, icon = { Text("▥") }, label = { Text(t("История", "History")) })
                NavigationBarItem(selected = page == Page.HOUSES, onClick = { page = Page.HOUSES }, icon = { Text("⌂") }, label = { Text(t("Птичники", "Houses")) })
                NavigationBarItem(selected = page == Page.ACCOUNT, onClick = { page = Page.ACCOUNT }, icon = { Text("○") }, label = { Text(t("Профиль", "Profile")) })
                NavigationBarItem(selected = page == Page.SETTINGS, onClick = { page = Page.SETTINGS }, icon = { Text("⚙") }, label = { Text(t("Настройки", "Settings")) })
            }
        }) { padding ->
            if (page == Page.ANALYTICS) {
                AnalyticsScreen(analytics, ru, Modifier.padding(padding))
                return@Scaffold
            }
            if (page == Page.HOUSES) {
                Box(Modifier.padding(padding)) {
                    key(server, accountState.sessionRevision) {
                    HousesScreen(houses, ru, house, onSelect = { selected ->
                        house = selected.id; store.put("house", selected.id); page = Page.HOME
                    })
                    }
                }
                return@Scaffold
            }
            if (page == Page.ACCOUNT) {
                AccountScreen(account, ru, Modifier.padding(padding))
                return@Scaffold
            }
            LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                item {
                    Text("SMARTHEN / QUAIL", style = MaterialTheme.typography.labelLarge, color = colors.primary)
                    Spacer(Modifier.height(12.dp))
                    Text(if (page == Page.HOME) t("Ваш птичник", "Your quail house") else t("Настройки", "Settings"), style = MaterialTheme.typography.headlineLarge)
                    Text(t("Забота о перепелах — в деталях", "Care for your quail, down to the details"), color = colors.onSurfaceVariant)
                }
                if (page == Page.HOME) {
                    item {
                        Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = colors.primaryContainer)) {
                            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text(if (house.isBlank()) t("Подключите птичник", "Connect your house") else t("Птичник · $house", "House · $house"), style = MaterialTheme.typography.titleLarge)
                                Text(when {
                                    currentAccount == null -> t("Войдите в профиль, чтобы получить показания ваших устройств.", "Sign in to see your device readings.")
                                    server.isBlank() || house.isBlank() -> t("Укажите адрес сервера и идентификатор птичника. Здесь появятся показания ваших датчиков.", "Add your server address and house ID to see your sensor readings.")
                                    failed -> t("Не удалось обновить данные. Проверьте интернет и доступность сервера. Повторяем автоматически.", "Could not refresh. Check your connection and server availability. Retrying automatically.")
                                    loading && readings.isEmpty() -> t("Получаем показания…", "Getting readings…")
                                    readings.isEmpty() -> t("Сервер не вернул датчики для этого птичника.", "The server returned no sensors for this house.")
                                    cached -> t("Сохранённые показания. Они могут отличаться от текущих.", "Saved readings. Current conditions may be different.")
                                    else -> t("Показания получены. Свежесть каждого датчика указана ниже.", "Readings received. Each sensor’s freshness is shown below.")
                                })
                                if (currentAccount == null) Button(onClick = { page = Page.ACCOUNT }) { Text(t("Войти", "Sign in")) }
                                else if (server.isBlank() || house.isBlank()) Button(onClick = { page = Page.SETTINGS }) { Text(t("Настроить подключение", "Set up connection")) }
                                else OutlinedButton(onClick = { monitor.refresh() }, enabled = !loading) { Text(t("Обновить", "Refresh")) }
                                if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                            }
                        }
                    }
                    if (readings.isNotEmpty()) item { Text(t("Показания датчиков", "Sensor readings"), style = MaterialTheme.typography.titleMedium) }
                    items(readings) { sensor ->
                        val kind = SensorKind.entries.find { it.name == sensor.type } ?: SensorKind.UNKNOWN
                        val status = SensorSnapshot(DeviceId(house, sensor.id), kind, sensor.current_value, sensor.unit, sensor.last_seen, sensor.is_online, if (cached) ReadingSource.CACHE else ReadingSource.LIVE).freshness(now, 30)
                        val name = when (kind) {
                            SensorKind.TEMPERATURE -> t("Температура", "Temperature")
                            SensorKind.HUMIDITY -> t("Влажность", "Humidity")
                            SensorKind.WATER -> t("Вода", "Water")
                            SensorKind.FEED -> t("Корм", "Feed")
                            SensorKind.AIR_QUALITY -> t("Качество воздуха", "Air quality")
                            SensorKind.EGG_COUNT -> t("Яйца в лотке", "Eggs in tray")
                            SensorKind.HEATING -> t("Отопление", "Heating")
                            SensorKind.UNKNOWN -> sensor.name
                        }
                        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
                            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(name, style = MaterialTheme.typography.titleMedium)
                                Text("${sensor.current_value} ${sensor.unit}", style = MaterialTheme.typography.headlineMedium)
                                Text(when (status) {
                                    Freshness.FRESH -> if (cached) t("Сохранённое значение", "Saved value") else t("Недавнее измерение", "Recent measurement")
                                    Freshness.STALE -> t("Устаревшее измерение", "Stale measurement")
                                    Freshness.OFFLINE -> t("Датчик не в сети", "Sensor offline")
                                    Freshness.INVALID -> t("Некорректное значение", "Invalid reading")
                                    Freshness.UNKNOWN -> t("Время измерения неизвестно", "Measurement time unknown")
                                }, color = if (status == Freshness.FRESH) colors.primary else colors.error)
                                if (sensor.last_seen > 0 && sensor.last_seen <= now) Text(t("Измерено ${now - sensor.last_seen} с назад", "Measured ${now - sensor.last_seen}s ago"), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                } else {
                    item {
                        Text(t("Оформление", "Appearance"), style = MaterialTheme.typography.titleLarge)
                        listOf("system" to t("Как на устройстве", "System"), "light" to t("Дневная тема", "Light"), "dark" to t("Ночная тема", "Dark")).forEach { (value, label) ->
                            TextButton(onClick = { theme = value; store.put("theme", value) }) { Text((if (theme == value) "●  " else "○  ") + label) }
                        }
                        Text(t("Язык", "Language"), style = MaterialTheme.typography.titleLarge)
                        Row { listOf("ru" to "Русский", "en" to "English").forEach { (value, label) ->
                            TextButton(onClick = { language = value; store.put("language", value) }) { Text((if (language == value) "●  " else "○  ") + label) }
                        } }
                    }
                    item { ConnectionForm(server, house, ru) { newServer, newHouse ->
                        store.put("server", newServer); store.put("house", newHouse)
                        server = newServer; house = newHouse; page = Page.HOME
                    } }
                }
            }
        }
    }
}

@Composable
private fun ConnectionForm(server: String, house: String, ru: Boolean, save: (String, String) -> Unit) {
    var address by remember(server) { mutableStateOf(server) }
    var id by remember(house) { mutableStateOf(house) }
    fun t(r: String, e: String) = if (ru) r else e
    Card {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(t("Подключение к серверу", "Server connection"), style = MaterialTheme.typography.titleLarge)
            Text(t("Укажите HTTPS-адрес сервера SmartHen. Создать и выбрать птичник можно в разделе «Птичники».", "Enter your SmartHen HTTPS server. Create and select a house in Houses."))
            OutlinedTextField(address, { address = it }, Modifier.fillMaxWidth(), label = { Text(t("Адрес сервера", "Server URL")) }, placeholder = { Text("https://example.com") }, singleLine = true)
            OutlinedTextField(id, { id = it }, Modifier.fillMaxWidth(), label = { Text(t("Идентификатор птичника", "House ID")) }, singleLine = true)
            Text(t("HTTP предназначен только для локальной разработки; в Android доступен в debug-сборке.", "HTTP is for local development only; Android permits it in debug builds."), style = MaterialTheme.typography.bodySmall)
            Button(onClick = { save(address.trim().trimEnd('/'), id.trim()) }, enabled = address.isNotBlank()) { Text(t("Сохранить и подключить", "Save and connect")) }
        }
    }
}
