package com.example.controlvial

import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/** Lista de todas las actividades vencidas o próximas a vencer. */
class AlertasFragment : Fragment(R.layout.fragment_alertas) {

    override fun onResume() {
        super.onResume()
        actualizar()
    }

    private fun actualizar() {
        val vista = view ?: return
        val repo = RepositorioMiCarro(requireContext())
        val alertas = repo.alertas()

        val vencidas = alertas.count { it.estado == EstadoActividad.VENCIDA }
        val proximas = alertas.count { it.estado == EstadoActividad.PROXIMA }

        vista.findViewById<TextView>(R.id.tvResumenAlertas).text = getString(
            R.string.resumen_alertas,
            resources.getQuantityString(R.plurals.n_vencidas, vencidas, vencidas),
            resources.getQuantityString(R.plurals.n_proximas, proximas, proximas)
        )

        vista.findViewById<View>(R.id.tvSinAlertasLista).visibility =
            if (alertas.isEmpty()) View.VISIBLE else View.GONE

        // Alertas pospuestas (RF-31): siguen existiendo, solo están ocultas un tiempo.
        val pospuestas = repo.cantidadPospuestas()
        vista.findViewById<View>(R.id.grupoPospuestas).visibility =
            if (pospuestas > 0) View.VISIBLE else View.GONE
        vista.findViewById<TextView>(R.id.tvPospuestas).text =
            resources.getQuantityString(R.plurals.n_pospuestas, pospuestas, pospuestas)
        vista.findViewById<View>(R.id.btnQuitarPospuestas).setOnClickListener {
            repo.quitarPosposiciones()
            actualizar()
        }

        val contenedor = vista.findViewById<LinearLayout>(R.id.contenedorAlertas)
        contenedor.removeAllViews()
        alertas.forEach {
            contenedor.addView(
                Vistas.tarjetaAlerta(layoutInflater, contenedor, it) { alerta ->
                    elegirPosposicion(repo, alerta)
                }
            )
        }
    }

    private fun elegirPosposicion(repo: RepositorioMiCarro, alerta: AlertaItem) {
        val opciones = arrayOf(
            getString(R.string.posponer_1_dia),
            getString(R.string.posponer_3_dias),
            getString(R.string.posponer_7_dias)
        )
        val dias = intArrayOf(1, 3, 7)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.posponer_titulo, alerta.titulo))
            .setItems(opciones) { _, posicion ->
                val hasta = repo.posponerAlerta(alerta.idOrigen, dias[posicion])
                Toast.makeText(
                    requireContext(),
                    getString(R.string.alerta_pospuesta, hasta),
                    Toast.LENGTH_LONG
                ).show()
                actualizar()
            }
            .setNegativeButton(R.string.cancelar, null)
            .show()
    }
}
