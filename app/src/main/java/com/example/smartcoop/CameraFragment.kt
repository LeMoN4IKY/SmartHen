package com.example.smartcoop

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class CameraFragment : Fragment() {

    private lateinit var cameraStatus: TextView
    private lateinit var toggleCameraBtn: Button
    private lateinit var recordBtn: Button

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
        // GET http://<IP>:8000/camera/status
        lifecycleScope.launch {
            try {
                val status = api.getCameraStatus()
                isCameraOn = status.is_on
                isRecording = status.is_recording
                updateUI()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // ========== КНОПКА ЗАПУСК / ОСТАНОВКА КАМЕРЫ ==========
        toggleCameraBtn.setOnClickListener {
            lifecycleScope.launch {
                try {
                    if (isCameraOn) {
                        // POST http://<IP>:8000/camera/stop
                        api.stopCamera()
                        isCameraOn = false
                        isRecording = false
                        Toast.makeText(requireContext(), "Камера остановлена", Toast.LENGTH_SHORT).show()
                    } else {
                        // POST http://<IP>:8000/camera/start
                        api.startCamera()
                        isCameraOn = true
                        Toast.makeText(requireContext(), "Камера запущена", Toast.LENGTH_SHORT).show()
                    }
                    updateUI()
                } catch (e: Exception) {
                    Toast.makeText(requireContext(), "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }

        // ========== ЗАПИСЬ ==========
        recordBtn.setOnClickListener {
            if (!isCameraOn) {
                Toast.makeText(requireContext(), "Сначала запустите камеру", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            lifecycleScope.launch {
                try {
                    if (isRecording) {
                        // POST http://<IP>:8000/camera/stop_record
                        api.stopRecord()
                        isRecording = false
                        Toast.makeText(requireContext(), "Запись остановлена", Toast.LENGTH_SHORT).show()
                    } else {
                        // POST http://<IP>:8000/camera/start_record
                        api.startRecord()
                        isRecording = true
                        Toast.makeText(requireContext(), "Запись начата", Toast.LENGTH_SHORT).show()
                    }
                    updateUI()
                } catch (e: Exception) {
                    Toast.makeText(requireContext(), "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun updateUI() {
        if (isCameraOn) {
            cameraStatus.text = "📹 Камера запущена"
            cameraStatus.setTextColor(resources.getColor(android.R.color.holo_green_dark))
            toggleCameraBtn.text = "⏹ Остановить камеру"
        } else {
            cameraStatus.text = "⏸ Камера остановлена"
            cameraStatus.setTextColor(resources.getColor(android.R.color.darker_gray))
            toggleCameraBtn.text = "▶ Запустить камеру"
            recordBtn.text = "⏺ Начать запись"
        }

        if (isRecording) {
            recordBtn.text = "⏹ Остановить запись"
        } else {
            recordBtn.text = "⏺ Начать запись"
        }
    }
}