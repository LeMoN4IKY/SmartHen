package com.example.smartcoop

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

/**
 * Фрагмент камеры.
 * Управление камерой и записью через сервер.
 * Поддерживает перевод на русский и английский.
 */
class CameraFragment : Fragment() {

    // UI элементы
    private lateinit var cameraStatus: TextView
    private lateinit var toggleCameraBtn: Button
    private lateinit var recordBtn: Button

    // Состояние
    private var isCameraOn = false
    private var isRecording = false
    private val api = RetrofitHelper.api

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_camera, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        cameraStatus = view.findViewById(R.id.cameraStatus)
        toggleCameraBtn = view.findViewById(R.id.toggleCameraBtn)
        recordBtn = view.findViewById(R.id.recordBtn)

        // Загружаем текущий статус камеры с сервера
        lifecycleScope.launch {
            try {
                val status = api.getCameraStatus()
                isCameraOn = status.is_on
                isRecording = status.is_recording
                updateUI()
            } catch (e: Exception) {
                e.printStackTrace()
                // Если сервер не отвечает, показываем статус "неизвестно"
                cameraStatus.text = getString(R.string.camera_unknown)
                cameraStatus.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.darker_gray))
            }
        }

        // ========== КНОПКА ЗАПУСК / ОСТАНОВКА КАМЕРЫ ==========
        toggleCameraBtn.setOnClickListener {
            lifecycleScope.launch {
                try {
                    if (isCameraOn) {
                        api.stopCamera()
                        isCameraOn = false
                        isRecording = false
                        Toast.makeText(requireContext(), R.string.camera_stopped, Toast.LENGTH_SHORT).show()
                    } else {
                        api.startCamera()
                        isCameraOn = true
                        Toast.makeText(requireContext(), R.string.camera_started, Toast.LENGTH_SHORT).show()
                    }
                    updateUI()
                } catch (e: Exception) {
                    Toast.makeText(requireContext(), getString(R.string.camera_error, e.message ?: ""), Toast.LENGTH_SHORT).show()
                }
            }
        }

        // ========== ЗАПИСЬ ==========
        recordBtn.setOnClickListener {
            if (!isCameraOn) {
                Toast.makeText(requireContext(), R.string.camera_start_first, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            lifecycleScope.launch {
                try {
                    if (isRecording) {
                        api.stopRecord()
                        isRecording = false
                        Toast.makeText(requireContext(), R.string.recording_stopped, Toast.LENGTH_SHORT).show()
                    } else {
                        api.startRecord()
                        isRecording = true
                        Toast.makeText(requireContext(), R.string.recording_started, Toast.LENGTH_SHORT).show()
                    }
                    updateUI()
                } catch (e: Exception) {
                    Toast.makeText(requireContext(), getString(R.string.camera_error, e.message ?: ""), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    /**
     * Обновить UI в зависимости от состояния камеры
     */
    private fun updateUI() {
        if (isCameraOn) {
            cameraStatus.text = getString(R.string.camera_on_status)
            cameraStatus.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.holo_green_dark))
            toggleCameraBtn.text = getString(R.string.camera_stop_btn)
        } else {
            cameraStatus.text = getString(R.string.camera_not)
            cameraStatus.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.darker_gray))
            toggleCameraBtn.text = getString(R.string.camera_start)
            // Сбрасываем текст кнопки записи, если камера выключена
            recordBtn.text = getString(R.string.lets_view)
        }

        if (isRecording) {
            recordBtn.text = getString(R.string.record_stop_btn)
        } else {
            recordBtn.text = getString(R.string.lets_view)
        }
    }
}