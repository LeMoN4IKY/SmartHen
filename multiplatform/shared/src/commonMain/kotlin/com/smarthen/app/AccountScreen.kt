package com.smarthen.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

@Composable
fun AccountScreen(viewModel: AccountViewModel, ru: Boolean, modifier: Modifier = Modifier) {
    val ui by viewModel.state.collectAsState()
    var register by remember { mutableStateOf(false) }
    var email by remember(ui.server) { mutableStateOf("") }
    var name by remember(ui.server) { mutableStateOf("") }
    // Password is deliberately not saveable and is cleared once authentication succeeds.
    var password by remember(ui.server) { mutableStateOf("") }
    LaunchedEffect(ui.user) { if (ui.user != null) password = "" }
    fun t(r: String, e: String) = if (ru) r else e
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("SMARTHEN / QUAIL", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Text(t("Ваш профиль", "Your profile"), style = MaterialTheme.typography.headlineLarge)
        val user = ui.user
        if (user == null) {
            Text(if (register) t("Создайте аккаунт для ваших птичников", "Create an account for your houses") else t("Войдите, чтобы управлять своими птичниками", "Sign in to manage your houses"))
            if (register) OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), enabled = !ui.busy, label = { Text(t("Ваше имя", "Your name")) }, singleLine = true)
            OutlinedTextField(email, { email = it }, Modifier.fillMaxWidth(), enabled = !ui.busy, label = { Text("Email") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
            OutlinedTextField(password, { password = it }, Modifier.fillMaxWidth(), enabled = !ui.busy, label = { Text(t("Пароль", "Password")) }, singleLine = true, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), supportingText = { Text(t("Не менее 12 символов", "At least 12 characters")) })
            Button(onClick = { viewModel.submit(name, email, password, register) }, enabled = !ui.busy && ui.server.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text(if (register) t("Создать аккаунт", "Create account") else t("Войти", "Sign in")) }
            TextButton(onClick = { register = !register; password = "" }, enabled = !ui.busy) { Text(if (register) t("Уже есть аккаунт? Войти", "Have an account? Sign in") else t("Создать аккаунт", "Create account")) }
            if (ui.server.isBlank()) Text(t("Сначала укажите сервер в настройках подключения.", "Set your server in connection settings first."))
        } else {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(user.display_name, style = MaterialTheme.typography.titleLarge)
                    Text(user.email)
                }
            }
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(t("Тариф и подписка", "Plan and subscription"), style = MaterialTheme.typography.titleLarge)
                    val subscription = ui.subscription
                    Text(when {
                        subscription == null -> t("Сведения о подписке пока не получены", "Subscription details have not loaded")
                        subscription.status == "inactive" -> t("Нет активной подписки", "No active subscription")
                        subscription.status == "active" -> subscription.tariff ?: t("Активная подписка", "Active subscription")
                        else -> t("Статус требует проверки", "Status needs verification")
                    })
                    Text(t("Оплата будет доступна после подключения платёжного сервиса. Сейчас списаний нет.", "Payments will become available once the payment provider is connected. No charges are made now."), style = MaterialTheme.typography.bodySmall)
                    OutlinedButton(onClick = viewModel::refresh, enabled = !ui.busy) { Text(t("Обновить сведения", "Refresh details")) }
                }
            }
            OutlinedButton(onClick = viewModel::logout, enabled = !ui.busy) { Text(t("Выйти из аккаунта", "Sign out")) }
        }
        if (ui.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        ui.error?.let { error ->
            Text(when (error) {
                AccountError.INVALID_INPUT -> t("Проверьте email, имя и пароль (не менее 12 символов).", "Check email, name and password (at least 12 characters).")
                AccountError.INVALID_CREDENTIALS -> t("Не удалось войти. Проверьте email и пароль.", "Could not sign in. Check email and password.")
                AccountError.EMAIL_USED -> t("Этот email уже зарегистрирован. Попробуйте войти.", "This email is already registered. Try signing in.")
                AccountError.SESSION_EXPIRED -> t("Сессия истекла. Войдите снова.", "Session expired. Sign in again.")
                AccountError.STORAGE -> t("Не удалось безопасно сохранить сессию на устройстве.", "Could not securely save this session on your device.")
                AccountError.LOGOUT_PENDING -> t("Выход не подтверждён сервером. Повторите попытку.", "Server has not confirmed sign out. Please retry.")
                AccountError.UNAVAILABLE -> t("Запрос не выполнен. Проверьте связь и повторите попытку.", "Request failed. Check your connection and try again.")
            }, color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
fun ProfileSettings(theme: String, language: String, onTheme: (String) -> Unit, onLanguage: (String) -> Unit) {
    val ru = language == "ru"
    fun t(r: String, e: String) = if (ru) r else e
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(t("Оформление", "Appearance"), style = MaterialTheme.typography.titleLarge)
        listOf("system" to t("Как на устройстве", "System"), "light" to t("Дневная тема", "Light"), "dark" to t("Ночная тема", "Dark")).forEach { (value, label) ->
            Row { RadioButton(theme == value, onClick = { onTheme(value) }); TextButton(onClick = { onTheme(value) }) { Text(label) } }
        }
        Text(t("Язык", "Language"), style = MaterialTheme.typography.titleLarge)
        listOf("ru" to "Русский", "en" to "English").forEach { (value, label) ->
            Row { RadioButton(language == value, onClick = { onLanguage(value) }); TextButton(onClick = { onLanguage(value) }) { Text(label) } }
        }
    }
}
