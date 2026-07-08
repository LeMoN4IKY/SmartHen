package com.example.smartcoop

import android.content.Context
import com.example.smartcoop.data.Coop
import com.example.smartcoop.RetrofitHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class CoopManager(private val context: Context) {

    private val _coops = MutableStateFlow<List<Coop>>(emptyList())
    val coops: StateFlow<List<Coop>> = _coops

    private val _selectedCoopId = MutableStateFlow<String?>(null)
    val selectedCoopId: StateFlow<String?> = _selectedCoopId

    private val userId = "user_1"  // 🔧 ПОЗЖЕ ЗАМЕНИМ НА РЕАЛЬНОГО ПОЛЬЗОВАТЕЛЯ
    private val api = RetrofitHelper.api

    suspend fun loadCoops() {
        try {
            val response = api.getCoops(userId)
            _coops.value = response
            if (response.isNotEmpty()) {
                _selectedCoopId.value = response.first().id
            }
        } catch (e: Exception) {
            e.printStackTrace()
            _coops.value = emptyList()
        }
    }

    fun selectCoop(coopId: String) {
        _selectedCoopId.value = coopId
    }

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

    suspend fun addCoopBySerial(serial: String) {
        try {
            val response = api.addCoop(userId, serial)
            loadCoops()  // перезагружаем список
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}