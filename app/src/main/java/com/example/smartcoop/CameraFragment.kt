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
    private lateinit var startBtn: Button
    private var isCameraOn = false

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
        startBtn = view.findViewById(R.id.startCameraBtn)

        startBtn.setOnClickListener {
            if (isCameraOn) {
                cameraStatus.text = "⏸ Камера остановлена"
                startBtn.text = "▶ Запустить камеру"
                isCameraOn = false
            } else {
                cameraStatus.text = "📹 Камера запущена (демо-режим)"
                startBtn.text = "⏹ Остановить камеру"
                isCameraOn = true
                Toast.makeText(context, "Камера запущена", Toast.LENGTH_SHORT).show()
            }
        }
    }
}