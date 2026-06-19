package com.example.smartcoop.data

data class User(
    val id: String,
    val name: String,
    val email: String,
    val phone: String,
    val activeCoopId: String? = null
)