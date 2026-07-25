package com.example.smartcoop

import com.example.smartcoop.data.Sensor

/**
 * Центральное хранилище данных приложения
 * Все глобальные переменные и кэш хранятся здесь
 */
object DataManager {
    // ID пользователя (позже из авторизации)
    var userId: String? = "user_1"

    // ID текущего выбранного курятника
    var currentCoopId: String? = null

    // ===== КЭШ ДЛЯ ОФЛАЙН-РЕЖИМА =====
    // Сохраняем последние загруженные датчики
    var cachedSensors: List<Sensor> = emptyList()

    // Время последнего обновления кэша (Unix timestamp)
    var lastUpdateTime: Long = 0

    /**
     * Получить ID текущего курятника или "1" по умолчанию
     */
    fun getCurrentCoopIdOrDefault(): String {
        return currentCoopId ?: "1"
    }

    /**
     * Проверить, актуальны ли данные (не старше 30 секунд)
     */
    fun isDataFresh(): Boolean {
        return System.currentTimeMillis() - lastUpdateTime < 30000
    }

    /**
     * Получить время последнего обновления в читаемом формате
     */
    fun getLastUpdateTimeString(): String {
        return if (lastUpdateTime > 0) {
            val date = java.util.Date(lastUpdateTime)
            val format = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
            format.format(date)
        } else {
            "никогда"
        }
    }
}