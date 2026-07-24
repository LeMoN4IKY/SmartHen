package com.example.smartcoop

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.journeyapps.barcodescanner.BarcodeCallback
import com.journeyapps.barcodescanner.BarcodeResult
import com.journeyapps.barcodescanner.DecoratedBarcodeView
import android.widget.FrameLayout

/**
 * Фрагмент для сканирования QR-кода.
 * Использует библиотеку ZXing Android Embedded.
 */
class QrScannerFragment : Fragment() {

    // View для сканирования
    private lateinit var barcodeView: DecoratedBarcodeView
    private var isScanning = true

    // Колбэк, который срабатывает при успешном сканировании
    private val callback = BarcodeCallback { result: BarcodeResult? ->
        if (result != null && isScanning) {
            isScanning = false
            // Останавливаем сканирование
            barcodeView.pause()

            // Передаём результат обратно в Activity
            parentFragmentManager.setFragmentResult(
                "qr_scan_result",
                Bundle().apply {
                    putString("qr_code", result.text)
                }
            )

            // Закрываем фрагмент
            parentFragmentManager.popBackStack()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        barcodeView = DecoratedBarcodeView(requireContext())
        barcodeView.decodeContinuous(callback)
        return barcodeView
    }

    override fun onResume() {
        super.onResume()
        // Проверяем разрешение на камеру
        if (ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.CAMERA
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // Запрашиваем разрешение
            ActivityCompat.requestPermissions(
                requireActivity(),
                arrayOf(Manifest.permission.CAMERA),
                100
            )
        } else {
            // Запускаем камеру
            barcodeView.resume()
        }
    }

    override fun onPause() {
        super.onPause()
        // Останавливаем камеру при уходе с экрана
        barcodeView.pause()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Скрываем контейнер при закрытии сканера
        val container = requireActivity().findViewById<FrameLayout>(R.id.fragment_container)
        container?.visibility = View.GONE
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 100) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Разрешение получено — запускаем сканер
                barcodeView.resume()
            } else {
                Toast.makeText(requireContext(), "Нет доступа к камере", Toast.LENGTH_SHORT).show()
                parentFragmentManager.popBackStack()
            }
        }
    }
}