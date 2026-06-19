package com.example.smartcoop

import android.Manifest
import android.app.AlertDialog
import android.content.ContentValues.TAG
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.SwitchCompat
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.smartcoop.data.Coop
import com.example.smartcoop.utils.LocaleHelper
import kotlinx.coroutines.launch

class ProfileFragment : Fragment() {

    private lateinit var userName: EditText
    private lateinit var userPhone: EditText
    private lateinit var userEmail: EditText
    private lateinit var saveBtn: Button
    private lateinit var themeSwitch: SwitchCompat
    private lateinit var prefs: SharedPreferences

    // Курятники
    private lateinit var coopSpinner: Spinner
    private lateinit var renameCoopBtn: Button
    private lateinit var scanQrBtn: Button
    private lateinit var coopManager: CoopManager
    private var coopList: List<Coop> = emptyList()
    private var selectedCoopId: String? = null

    // Подписка
    private lateinit var tariffStatus: TextView
    private lateinit var changeTariffBtn: Button
    private lateinit var addCardBtn: Button

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_profile, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Инициализация
        userName = view.findViewById(R.id.userName)
        userPhone = view.findViewById(R.id.userPhone)
        userEmail = view.findViewById(R.id.userEmail)
        saveBtn = view.findViewById(R.id.saveProfileBtn)
        themeSwitch = view.findViewById(R.id.themeSwitch)
        coopSpinner = view.findViewById(R.id.coopSpinner)
        renameCoopBtn = view.findViewById(R.id.renameCoopBtn)
        scanQrBtn = view.findViewById(R.id.scanQrBtn)
        tariffStatus = view.findViewById(R.id.tariffStatus)
        changeTariffBtn = view.findViewById(R.id.changeTariffBtn)
        addCardBtn = view.findViewById(R.id.addCardBtn)

        prefs = requireActivity().getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)
        coopManager = CoopManager(requireContext())

        loadSavedData()
        setupThemeSwitch()
        setupLanguageSpinner(view)
        setupCoops()
        setupSubscription()

        saveBtn.setOnClickListener { saveData() }
        renameCoopBtn.setOnClickListener { showRenameDialog() }
        scanQrBtn.setOnClickListener { scanQrCode() }
        changeTariffBtn.setOnClickListener { showTariffDialog() }
        addCardBtn.setOnClickListener { addCard() }
    }

    // ============ ЗАГРУЗКА ДАННЫХ ============

    private fun loadSavedData() {
        userName.setText(prefs.getString("userName", "Петр Иванов"))
        userPhone.setText(prefs.getString("userPhone", "+7 999 123-45-67"))
        userEmail.setText(prefs.getString("userEmail", "user@coop.ru"))
        val isDark = prefs.getBoolean("dark_theme", false)
        themeSwitch.isChecked = isDark
    }

    private fun saveData() {
        val editor = prefs.edit()
        editor.putString("userName", userName.text.toString())
        editor.putString("userPhone", userPhone.text.toString())
        editor.putString("userEmail", userEmail.text.toString())
        editor.apply()
        Toast.makeText(requireContext(), "Настройки сохранены", Toast.LENGTH_SHORT).show()
    }

    // ============ ТЕМА ============

    private fun setupThemeSwitch() {
        themeSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            } else {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            }
            prefs.edit().putBoolean("dark_theme", isChecked).apply()
            requireActivity().recreate()
        }
    }

    // ============ ЯЗЫК ============

    private fun setupLanguageSpinner(view: View) {
        val languageSpinner = view.findViewById<Spinner>(R.id.languageSpinner)
        val languages = arrayOf("Русский", "English")
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, languages)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        languageSpinner.adapter = adapter

        val currentLang = LocaleHelper.getLanguage(requireContext())
        languageSpinner.setSelection(if (currentLang == "ru") 0 else 1)

        languageSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                val newLang = if (position == 0) "ru" else "en"
                if (newLang != LocaleHelper.getLanguage(requireContext())) {
                    LocaleHelper.setLocale(requireContext(), newLang)
                    requireActivity().recreate()
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }
    }

    // ============ КУРЯТНИКИ ============

    private fun setupCoops() {
        lifecycleScope.launch {
            coopManager.loadCoops()
            coopManager.coops.collect { coops ->
                coopList = coops
                updateCoopSpinner()
            }
            coopManager.selectedCoopId.collect { coopId ->
                selectedCoopId = coopId
                if (coopId != null) {
                    val index = coopList.indexOfFirst { it.id == coopId }
                    if (index >= 0) coopSpinner.setSelection(index)
                }
            }
        }
    }

    private fun updateCoopSpinner() {
        val names = coopList.map { it.name }.toTypedArray()
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, names)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        coopSpinner.adapter = adapter

        // Восстанавливаем выбор
        val currentIndex = coopList.indexOfFirst { it.id == selectedCoopId }
        if (currentIndex >= 0) {
            coopSpinner.setSelection(currentIndex)
        }

        coopSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                if (position < coopList.size) {
                    val coop = coopList[position]
                    selectedCoopId = coop.id
                    coopManager.selectCoop(coop.id)
                    Toast.makeText(requireContext(), "Выбран: ${coop.name}", Toast.LENGTH_SHORT).show()
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }
    }

    private fun showRenameDialog() {
        // Если selectedCoopId null, берём первый курятник из списка
        val coopId = selectedCoopId ?: coopList.firstOrNull()?.id
        val currentCoop = coopList.find { it.id == coopId }

        if (currentCoop == null) {
            Toast.makeText(requireContext(), "Нет доступных курятников", Toast.LENGTH_SHORT).show()
            return
        }

        val input = EditText(requireContext())
        input.setText(currentCoop.name)
        input.setSelection(input.text.length)

        AlertDialog.Builder(requireContext())
            .setTitle("Переименовать курятник")
            .setView(input)
            .setPositiveButton("Сохранить") { _, _ ->
                val newName = input.text.toString().trim()
                if (newName.isNotEmpty()) {
                    lifecycleScope.launch {
                        coopManager.renameCoop(currentCoop.id, newName)
                        updateCoopSpinner()
                        Toast.makeText(requireContext(), "✅ Курятник переименован", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(requireContext(), "Имя не может быть пустым", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun scanQrCode() {
        // 🔧 ЗДЕСЬ БУДЕТ ЗАПУСК КАМЕРЫ ДЛЯ СКАНИРОВАНИЯ QR
        // Пока заглушка
        Toast.makeText(requireContext(), "📷 Сканирование QR (заглушка)", Toast.LENGTH_SHORT).show()
        // После сканирования:
        // val serial = "COOP-003"
        // lifecycleScope.launch { coopManager.addCoopBySerial(serial) }
    }

    // ============ ПОДПИСКА ============

    private fun setupSubscription() {
        // 🔧 ЗДЕСЬ БУДЕТ ЗАПРОС К СЕРВЕРУ: GET /subscription/status
        tariffStatus.text = "Тариф: Базовый (до 30.06.2026)"
    }

    private fun showTariffDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_tariffs, null)
        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .setNegativeButton("Закрыть", null)
            .create()

        dialogView.findViewById<View>(R.id.cardBasic).setOnClickListener {
            // 🔧 ЗДЕСЬ БУДЕТ ЗАПРОС К СЕРВЕРУ: POST /subscription/update
            Toast.makeText(requireContext(), "Выбран тариф: Базовый (300 ₽)", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }
        dialogView.findViewById<View>(R.id.cardOptimum).setOnClickListener {
            Toast.makeText(requireContext(), "Выбран тариф: Оптимум (2 000 ₽)", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }
        dialogView.findViewById<View>(R.id.cardPremium).setOnClickListener {
            Toast.makeText(requireContext(), "Выбран тариф: Премиум (5 000 ₽)", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun addCard() {
        // 🔧 ЗДЕСЬ БУДЕТ ИНТЕГРАЦИЯ С ПЛАТЁЖНОЙ СИСТЕМОЙ
        Toast.makeText(requireContext(), "💳 Привязка карты (заглушка)", Toast.LENGTH_SHORT).show()
    }
}