package com.example.controlvial

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButtonToggleGroup

/** Pestaña "Historial": cambia entre Registros, Repuestos y Resumen de gastos. */
class HistorialContenedorFragment : Fragment(R.layout.fragment_historial_contenedor) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val grupo = view.findViewById<MaterialButtonToggleGroup>(R.id.grupoHistorial)
        grupo.addOnButtonCheckedListener { _, idBoton, marcado ->
            if (!marcado) return@addOnButtonCheckedListener

            val fragmento: Fragment = when (idBoton) {
                R.id.btnVerRepuestos -> RepuestosFragment()
                R.id.btnVerGastos -> GastosFragment()
                else -> HistorialFragment()
            }
            childFragmentManager.beginTransaction()
                .replace(R.id.contenedorHistorial, fragmento)
                .commit()
        }

        if (savedInstanceState == null) {
            grupo.check(R.id.btnVerRegistros)
        }
    }
}
