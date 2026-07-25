package com.example.smartcoop.utils

import android.content.Context
import android.widget.Toast
import retrofit2.HttpException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * Единый обработчик ошибок для всего приложения
 * Показывает понятные сообщения пользователю при любых ошибках
 */
object ErrorHandler {

    /**
     * Обработать ошибку и показать пользователю понятное сообщение
     * @param context Контекст для Toast
     * @param throwable Исключение, которое произошло
     * @param customMessage Своё сообщение (если нужно переопределить)
     */
    fun handle(
        context: Context,
        throwable: Throwable?,
        customMessage: String? = null
    ) {
        // Определяем текст сообщения в зависимости от типа ошибки
        val message = when {
            customMessage != null -> customMessage
            throwable == null -> "Неизвестная ошибка"
            throwable is HttpException -> handleHttpError(throwable)
            throwable is UnknownHostException -> "❌ Нет подключения к интернету"
            throwable is SocketTimeoutException -> "⏱️ Сервер не отвечает. Проверьте подключение."
            throwable is ConnectException -> "❌ Не удалось подключиться к серверу"
            else -> "❌ Ошибка: ${throwable.message ?: "Неизвестная ошибка"}"
        }

        // Показываем сообщение пользователю
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        // Логируем ошибку для разработчика
        throwable?.printStackTrace()
    }

    /**
     * Обработка HTTP-ошибок (коды ответа сервера)
     */
    private fun handleHttpError(error: HttpException): String {
        return when (error.code()) {
            400 -> "❌ Неверный запрос"
            401 -> "🔒 Требуется авторизация. Войдите заново."
            403 -> "⛔ Доступ запрещён"
            404 -> "📭 Данные не найдены"
            500 -> "⚠️ Ошибка на сервере. Попробуйте позже."
            502 -> "⚠️ Сервер временно недоступен"
            else -> "❌ Ошибка сервера: ${error.code()}"
        }
    }
}