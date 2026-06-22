package com.example.smartcoop

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment

class CameraFragment : Fragment() {

    private lateinit var cameraStatus: TextView
    private lateinit var toggleCameraBtn: Button
    private lateinit var recordBtn: Button

    private var isCameraOn = false
    private var isRecording = false

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

        // ========== КНОПКА ЗАПУСК / ОСТАНОВКА КАМЕРЫ ==========
        toggleCameraBtn.setOnClickListener {
            if (isCameraOn) {
                // 🔧 ЗДЕСЬ БУДЕТ ЗАПРОС К СЕРВЕРУ: POST /camera/stop
                isCameraOn = false
                isRecording = false
                cameraStatus.text = "⏸ Камера остановлена"
                cameraStatus.setTextColor(resources.getColor(android.R.color.darker_gray))
                toggleCameraBtn.text = "▶ Запустить камеру"
                recordBtn.text = "⏺ Начать запись"
                Toast.makeText(requireContext(), "🔍 Запрос на остановку камеры отправлен", Toast.LENGTH_SHORT).show()
            } else {
                // 🔧 ЗДЕСЬ БУДЕТ ЗАПРОС К СЕРВЕРУ: POST /camera/start
                isCameraOn = true
                cameraStatus.text = "📹 Камера запущена"
                cameraStatus.setTextColor(resources.getColor(android.R.color.holo_green_dark))
                toggleCameraBtn.text = "⏹ Остановить камеру"
                Toast.makeText(requireContext(), "🔍 Запрос на запуск камеры отправлен", Toast.LENGTH_SHORT).show()
            }
        }

        // ========== ЗАПИСЬ ==========
        recordBtn.setOnClickListener {
            if (!isCameraOn) {
                Toast.makeText(requireContext(), "⚠️ Сначала запустите камеру", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (isRecording) {
                // 🔧 ЗДЕСЬ БУДЕТ ЗАПРОС К СЕРВЕРУ: POST /camera/stop_record
                isRecording = false
                recordBtn.text = "⏺ Начать запись"
                Toast.makeText(requireContext(), "⏹ Запись остановлена", Toast.LENGTH_SHORT).show()
            } else {
                // 🔧 ЗДЕСЬ БУДЕТ ЗАПРОС К СЕРВЕРУ: POST /camera/start_record
                isRecording = true
                recordBtn.text = "⏹ Остановить запись"
                Toast.makeText(requireContext(), "⏺ Запись начата", Toast.LENGTH_SHORT).show()
            }
        }
    }
}