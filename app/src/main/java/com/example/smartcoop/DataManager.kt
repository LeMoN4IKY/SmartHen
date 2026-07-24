package com.example.smartcoop

object DataManager {
    // ID пользователя (позже будет из авторизации)
    var userId: String? = "user_1"

    // ID текущего выбранного курятника
    var currentCoopId: String? = null

    /**
     * Получить ID текущего курятника или значение по умолчанию "1"
     */
    fun getCurrentCoopIdOrDefault(): String {
        return currentCoopId ?: "1"
    }
}