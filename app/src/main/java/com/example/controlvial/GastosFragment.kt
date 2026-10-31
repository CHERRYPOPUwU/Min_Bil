package com.example.controlvial

import android.os.Bundle
import android.view.View
import android.widget.AutoCompleteTextView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.google.android.material.progressindicator.LinearProgressIndicator
import java.util.Calendar
import java.util.Date

/** Resumen de gastos por vehículo, categoría y periodo (RF-36). */
class GastosFragment : Fragment(R.layout.fragment_gastos) {

    private lateinit var repo: RepositorioMiCarro
    private lateinit var acVehiculo: AutoCompleteTextView
    private lateinit var acPeriodo: AutoCompleteTextView

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        repo = RepositorioMiCarro(requireContext())
        acVehiculo = view.findViewById(R.id.acFiltroGastosVehiculo)
        acPeriodo = view.findViewById(R.id.acFiltroPeriodo)

        val periodos = resources.getStringArray(R.array.periodos_gastos).toList()
        Ayudas.asignar(requireContext(), acPeriodo, periodos, periodos.first()) { actualizar() }
    }

    override fun onResume() {
        super.onResume()
        val todos = getString(R.string.todos_vehiculos)
        val placas = repo.obtenerVehiculos().map { it.optString("placa") }
        val actual = acVehiculo.text.toString()
        val inicial = if (actual in placas) actual else todos
        Ayudas.asignar(requireContext(), acVehiculo, listOf(todos) + placas, inicial) { actualizar() }
        actualizar()
    }

    /** Fecha desde la cual se cuentan los gastos según el periodo elegido (null = siempre). */
    private fun fechaInicio(): Date? {
        val periodos = resources.getStringArray(R.array.periodos_gastos)
        val ahora = Calendar.getInstance()
        ahora.set(Calendar.HOUR_OF_DAY, 0)
        ahora.set(Calendar.MINUTE, 0)
        ahora.set(Calendar.SECOND, 0)
        ahora.set(Calendar.MILLISECOND, 0)

        return when (periodos.indexOf(acPeriodo.text.toString())) {
            1 -> { // este mes
                ahora.set(Calendar.DAY_OF_MONTH, 1)
                ahora.time
            }
            2 -> { // últimos 3 meses
                ahora.add(Calendar.MONTH, -3)
                ahora.time
            }
            3 -> { // este año
                ahora.set(Calendar.DAY_OF_YEAR, 1)
                ahora.time
            }
            else -> null
        }
    }

    private fun actualizar() {
        val vista = view ?: return
        val todos = getString(R.string.todos_vehiculos)
        val placa = acVehiculo.text.toString().takeIf { it.isNotEmpty() && it != todos }

        val resumen = repo.resumenGastos(placa, fechaInicio())

        vista.findViewById<TextView>(R.id.tvGastosTotal).text = Formato.moneda(resumen.total)
        vista.findViewById<View>(R.id.tvSinGastosResumen).visibility =
            if (resumen.total <= 0.0) View.VISIBLE else View.GONE

        llenar(vista.findViewById(R.id.contenedorPorVehiculo), resumen.porVehiculo, resumen.total)
        llenar(vista.findViewById(R.id.contenedorPorCategoria), resumen.porCategoria, resumen.total)
        llenar(vista.findViewById(R.id.contenedorPorMes), resumen.porMes.take(12), resumen.total)
    }

    private fun llenar(contenedor: LinearLayout, datos: List<Pair<String, Double>>, total: Double) {
        contenedor.removeAllViews()
        val maximo = datos.maxOfOrNull { it.second } ?: 0.0

        for ((etiqueta, valor) in datos) {
            val fila = layoutInflater.inflate(R.layout.item_fila_gasto, contenedor, false)
            val porcentaje = if (total > 0) Math.round(valor / total * 100).toInt() else 0

            fila.findViewById<TextView>(R.id.tvFilaEtiqueta).text = etiqueta
            fila.findViewById<TextView>(R.id.tvFilaMonto).text =
                getString(R.string.gasto_fila_monto, Formato.moneda(valor), porcentaje)
            fila.findViewById<LinearProgressIndicator>(R.id.barraFila).progress =
                if (maximo > 0) Math.round(valor / maximo * 100).toInt() else 0

            contenedor.addView(fila)
        }
    }
}
