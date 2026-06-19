package com.example.smartcoop

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import com.example.smartcoop.data.Coop

class CoopManager(private val context: Context) {

    private val _coops = MutableStateFlow<List<Coop>>(emptyList())
    val coops: StateFlow<List<Coop>> = _coops

    private val _selectedCoopId = MutableStateFlow<String?>(null)
    val selectedCoopId: StateFlow<String?> = _selectedCoopId

    suspend fun loadCoops() {
        // 🔧 ЗДЕСЬ БУДЕТ ЗАПРОС К СЕРВЕРУ: GET /coops
        val mockCoops = listOf(
            Coop(id = "1", name = "Курятник №1", serial = "COOP-001"),
            Coop(id = "2", name = "Курятник №2", serial = "COOP-002")
        )
        _coops.value = mockCoops
        _selectedCoopId.value = mockCoops.firstOrNull()?.id
    }

    fun selectCoop(coopId: String) {
        _selectedCoopId.value = coopId
        // 🔧 ЗАПРОС К СЕРВЕРУ: POST /user/select_coop
    }

    suspend fun renameCoop(coopId: String, newName: String) {
        // 🔧 ЗАПРОС К СЕРВЕРУ: POST /coops/rename
        val list = _coops.value.toMutableList()
        val index = list.indexOfFirst { it.id == coopId }
        if (index >= 0) {
            list[index] = list[index].copy(name = newName)
            _coops.value = list
        }
    }

    suspend fun addCoopBySerial(serial: String) {
        // 🔧 ЗАПРОС К СЕРВЕРУ: POST /coops/add
        val newCoop = Coop(
            id = (System.currentTimeMillis()).toString(),
            name = "Курятник ${_coops.value.size + 1}",
            serial = serial
        )
        _coops.value = _coops.value + newCoop
    }
}