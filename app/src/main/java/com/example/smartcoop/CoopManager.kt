package com.example.smartcoop

import android.content.Context
import com.example.smartcoop.data.Coop
import com.example.smartcoop.RetrofitHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Менеджер курятников — загрузка, выбор, переименование
 */
class CoopManager(private val context: Context) {

    private val _coops = MutableStateFlow<List<Coop>>(emptyList())
    val coops: StateFlow<List<Coop>> = _coops

    private val _selectedCoopId = MutableStateFlow<String?>(null)
    val selectedCoopId: StateFlow<String?> = _selectedCoopId

    // ID пользователя — из DataManager
    private val userId: String
        get() = DataManager.userId ?: "user_1"

    private val api = RetrofitHelper.api

    /**
     * Загрузить список курятников с сервера
     */
    suspend fun loadCoops() {
        try {
            val response = api.getCoops(userId)
            _coops.value = response
            // Если есть курятники и нет выбранного — выбираем первый
            if (response.isNotEmpty() && _selectedCoopId.value == null) {
                selectCoop(response.first().id)
            }
            // Если выбранного нет в списке — выбираем первый
            _selectedCoopId.value?.let { selectedId ->
                if (response.none { it.id == selectedId }) {
                    if (response.isNotEmpty()) {
                        selectCoop(response.first().id)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            _coops.value = emptyList()
        }
    }

    /**
     * Выбрать курятник по ID
     */
    fun selectCoop(coopId: String) {
        _selectedCoopId.value = coopId
        // Синхронизация с DataManager
        DataManager.currentCoopId = coopId
    }

    /**
     * Переименовать курятник
     */
    suspend fun renameCoop(coopId: String, newName: String) {
        try {
            api.renameCoop(coopId, newName)
            val list = _coops.value.toMutableList()
            val index = list.indexOfFirst { it.id == coopId }
            if (index >= 0) {
                list[index] = list[index].copy(name = newName)
                _coops.value = list
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Привязать курятник по QR-коду (серийному номеру)
     */
    suspend fun addCoopBySerial(serial: String): Boolean {
        return try {
            api.addCoop(userId, serial)
            loadCoops()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Получить текущий выбранный курятник
     */
    fun getSelectedCoop(): Coop? {
        val id = _selectedCoopId.value ?: return null
        return _coops.value.find { it.id == id }
    }

    /**
     * Получить имя текущего курятника
     */
    fun getSelectedCoopName(): String {
        return getSelectedCoop()?.name ?: "Не выбран"
    }
}