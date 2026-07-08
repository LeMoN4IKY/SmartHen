package com.example.smartcoop.data

data class Subscription(
    val tariff: String,
    val status: String,
    val expires_at: Long
)