package com.example.smartcoop

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment

class ErrorsFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_errors, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val containerLayout = view.findViewById<LinearLayout>(R.id.errorsContainer)

        val systems = arrayOf(
            arrayOf("Вентиляция", "Исправна", "✅"),
            arrayOf("Кормушка", "Исправна", "✅"),
            arrayOf("Автопоилка", "Исправна", "✅"),
            arrayOf("Датчик температуры", "Исправен", "✅"),
            arrayOf("Датчик воздуха", "Исправен", "✅"),
            arrayOf("Отопитель", "Исправен", "✅"),
            arrayOf("Датчик воды", "Исправен", "✅"),
            arrayOf("Датчик корма", "Исправен", "✅"),
            arrayOf("Камера", "Исправна", "✅")
        )

        for (system in systems) {
            val itemView = layoutInflater.inflate(R.layout.item_system_status, containerLayout, false)
            val icon = itemView.findViewById<TextView>(R.id.systemIcon)
            val name = itemView.findViewById<TextView>(R.id.systemName)
            val status = itemView.findViewById<TextView>(R.id.systemStatus)

            icon.text = system[2]
            name.text = system[0]
            status.text = system[1]
            status.setTextColor(resources.getColor(android.R.color.holo_green_dark))

            containerLayout.addView(itemView)
        }
    }
}