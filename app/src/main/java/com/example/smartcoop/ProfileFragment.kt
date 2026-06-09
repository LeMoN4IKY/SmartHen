package com.example.smartcoop

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.SwitchCompat
import androidx.fragment.app.Fragment
import com.example.smartcoop.utils.LocaleHelper

class ProfileFragment : Fragment() {

    private lateinit var userName: EditText
    private lateinit var coopName: EditText
    private lateinit var userEmail: EditText
    private lateinit var saveBtn: Button
    private lateinit var themeSwitch: SwitchCompat
    private lateinit var prefs: SharedPreferences

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_profile, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        userName = view.findViewById(R.id.userName)
        coopName = view.findViewById(R.id.coopName)
        userEmail = view.findViewById(R.id.userEmail)
        saveBtn = view.findViewById(R.id.saveProfileBtn)
        themeSwitch = view.findViewById(R.id.themeSwitch)

        prefs = requireActivity().getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)

        loadSavedData()
        setupThemeSwitch()
        setupLanguageSpinner(view)

        saveBtn.setOnClickListener { saveData() }
    }

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

    private fun loadSavedData() {
        userName.setText(prefs.getString("userName", "Петр Иванов"))
        coopName.setText(prefs.getString("coopName", "Курятник №1"))
        userEmail.setText(prefs.getString("userEmail", "user@coop.ru"))
        val isDark = prefs.getBoolean("dark_theme", false)
        themeSwitch.isChecked = isDark
    }

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

    private fun saveData() {
        val editor = prefs.edit()
        editor.putString("userName", userName.text.toString())
        editor.putString("coopName", coopName.text.toString())
        editor.putString("userEmail", userEmail.text.toString())
        editor.apply()
        Toast.makeText(requireContext(), "Настройки сохранены", Toast.LENGTH_SHORT).show()
    }
}