package com.example.smartcoop

import retrofit2.http.*
import com.example.smartcoop.data.Sensor
import com.example.smartcoop.data.Coop
import com.example.smartcoop.data.Subscription

interface SmartHenApi {

    // ========== ДАТЧИКИ ==========
    @GET("sensors/{coopId}")
    suspend fun getSensors(@Path("coopId") coopId: String): List<Sensor>

    @POST("sensors/{coopId}/{sensorId}")
    suspend fun updateSensor(
        @Path("coopId") coopId: String,
        @Path("sensorId") sensorId: String,
        @Query("value") value: Float
    ): Map<String, String>

    @POST("sensors/check/{coopId}")
    suspend fun checkSensors(@Path("coopId") coopId: String): List<SensorStatus>

    // ========== КУРЯТНИКИ ==========
    @GET("coops/list/{userId}")
    suspend fun getCoops(@Path("userId") userId: String): List<Coop>

    @POST("coops/add")
    suspend fun addCoop(
        @Query("user_id") userId: String,
        @Query("serial") serial: String
    ): Map<String, String>

    @POST("coops/rename")
    suspend fun renameCoop(
        @Query("coop_id") coopId: String,
        @Query("new_name") newName: String
    ): Map<String, String>

    // ========== КАМЕРА ==========
    @POST("camera/start")
    suspend fun startCamera(): Map<String, String>

    @POST("camera/stop")
    suspend fun stopCamera(): Map<String, String>

    @POST("camera/start_record")
    suspend fun startRecord(): Map<String, String>

    @POST("camera/stop_record")
    suspend fun stopRecord(): Map<String, String>

    @GET("camera/status")
    suspend fun getCameraStatus(): CameraStatus

    // ========== ПОДПИСКИ ==========
    @GET("subscription/status/{userId}")
    suspend fun getSubscription(@Path("userId") userId: String): Subscription

    @POST("subscription/update")
    suspend fun updateSubscription(
        @Query("user_id") userId: String,
        @Query("tariff") tariff: String
    ): Map<String, String>

    // ========== ЯЙЦА ==========
    @POST("eggs/add")
    suspend fun addEggs(
        @Query("coop_id") coopId: String,
        @Query("count") count: Int
    ): Map<String, String>

    @GET("eggs/stats/{coop_id}")
    suspend fun getEggStats(@Path("coop_id") coopId: String): List<EggStats>
}

data class SensorStatus(
    val sensorId: String,
    val isOnline: Boolean
)

data class CameraStatus(
    val is_on: Boolean,
    val is_recording: Boolean
)

data class EggStats(
    val date: String,
    val count: Int
)