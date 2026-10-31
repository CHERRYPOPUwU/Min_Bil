package com.example.controlvial

import android.os.Bundle
import android.view.View
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.google.android.material.materialswitch.MaterialSwitch
import org.json.JSONObject

/** Repuestos instalados y en qué mantenimiento se registraron (RF-27). */
class RepuestosFragment : Fragment(R.layout.fragment_repuestos) {

    private lateinit var repo: RepositorioMiCarro
    private lateinit var acFiltro: AutoCompleteTextView
    private lateinit var interruptor: MaterialSwitch
    private lateinit var contenedor: LinearLayout
    private lateinit var tvSinRepuestos: View

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        repo = RepositorioMiCarro(requireContext())

        acFiltro = view.findViewById(R.id.acFiltroRepuestos)
        interruptor = view.findViewById(R.id.switchRetirados)
        contenedor = view.findViewById(R.id.contenedorRepuestos)
        tvSinRepuestos = view.findViewById(R.id.tvSinRepuestos)

        interruptor.setOnCheckedChangeListener { _, _ -> mostrarRepuestos() }
    }

    override fun onResume() {
        super.onResume()
        actualizarFiltro()
        mostrarRepuestos()
    }

    private fun actualizarFiltro() {
        val todos = getString(R.string.todos_vehiculos)
        val placas = repo.obtenerVehiculos().map { it.optString("placa") }
        val actual = acFiltro.text.toString()
        val inicial = when {
            actual == todos || actual in placas -> actual
            else -> repo.vehiculoPrincipal()?.optString("placa") ?: todos
        }
        Ayudas.asignar(requireContext(), acFiltro, listOf(todos) + placas, inicial) {
            mostrarRepuestos()
        }
    }

    private fun mostrarRepuestos() {
        val todos = getString(R.string.todos_vehiculos)
        val filtro = acFiltro.text.toString().ifEmpty { todos }
        val verRetirados = interruptor.isChecked
        val vehiculos = repo.obtenerVehiculos()

        val repuestos = repo.obtenerRepuestos()
            .filter { filtro == todos || it.optString("placa") == filtro }
            .filter { verRetirados || it.optBoolean("instalado", true) }
            .sortedByDescending {
                repo.parsearFecha(it.optString("fecha_instalacion"))?.time ?: 0L
            }

        tvSinRepuestos.visibility = if (repuestos.isEmpty()) View.VISIBLE else View.GONE
        contenedor.removeAllViews()
        repuestos.forEach { contenedor.addView(crearTarjeta(it, vehiculos)) }
    }

    private fun detalle(repuesto: JSONObject, kmActual: Int): String {
        val lineas = mutableListOf<String>()
        val placa = repuesto.optString("placa")
        lineas.add(getString(R.string.det_vehiculo, placa))

        val marcaReferencia = listOf(repuesto.optString("marca"), repuesto.optString("referencia"))
            .filter { it.isNotBlank() }.joinToString(" · ")
        if (marcaReferencia.isNotEmpty()) lineas.add(marcaReferencia)

        val proveedor = repuesto.optString("proveedor")
        if (proveedor.isNotEmpty()) lineas.add(getString(R.string.rep_proveedor, proveedor))

        lineas.add(getString(R.string.rep_valor, Formato.moneda(repo.valorRepuesto(repuesto))))
        lineas.add(getString(R.string.rep_instalado_el, repuesto.optString("fecha_instalacion")))

        // En qué mantenimiento fue registrado (RF-27)
        val registro = repo.buscarDocumento(repuesto.optString("registro_id"))
        if (registro != null) {
            lineas.add(
                getString(
                    R.string.rep_en_mantenimiento,
                    registro.optString("tipo"),
                    registro.optString("fecha")
                )
            )
        }

        val meses = repuesto.optInt("garantia_meses", 0)
        if (meses > 0) {
            val fecha = repo.parsearFecha(repuesto.optString("fecha_instalacion"))
            if (fecha != null) {
                lineas.add(
                    getString(
                        R.string.rep_garantia_hasta_fecha,
                        repo.formatearFecha(ReglasMantenimiento.agregarMeses(fecha, meses))
                    )
                )
            }
        }
        val garantiaKm = repuesto.optInt("garantia_km", 0)
        if (garantiaKm > 0) {
            lineas.add(
                getString(
                    R.string.rep_garantia_hasta_km,
                    Formato.kilometros(repuesto.optInt("km_instalacion", 0) + garantiaKm),
                    Formato.kilometros(kmActual)
                )
            )
        }

        val observaciones = repuesto.optString("observaciones")
        if (observaciones.isNotEmpty()) lineas.add(observaciones)
        return lineas.joinToString("\n")
    }

    private fun crearTarjeta(repuesto: JSONObject, vehiculos: List<JSONObject>): View {
        val tarjeta = layoutInflater.inflate(R.layout.item_repuesto, contenedor, false)
        val instalado = repuesto.optBoolean("instalado", true)
        val kmActual = vehiculos.firstOrNull {
            it.optString("placa").equals(repuesto.optString("placa"), ignoreCase = true)
        }?.optInt("kilometraje", 0) ?: 0

        val titulo = getString(
            R.string.repuesto_nombre_cantidad,
            repuesto.optString("nombre"),
            repuesto.optInt("cantidad", 1)
        )
        tarjeta.findViewById<TextView>(R.id.tvRepNombre).text =
            if (instalado) titulo else titulo + getString(R.string.repuesto_retirado_sufijo)
        tarjeta.findViewById<TextView>(R.id.tvRepDetalle).text = detalle(repuesto, kmActual)

        val etiqueta = tarjeta.findViewById<TextView>(R.id.tvRepGarantia)
        when (repo.estadoGarantiaRepuesto(repuesto, kmActual)) {
            EstadoGarantia.VIGENTE ->
                Estilos.aplicarEtiqueta(etiqueta, EstadoActividad.AL_DIA, getString(R.string.garantia_vigente))
            EstadoGarantia.VENCIDA ->
                Estilos.aplicarEtiqueta(etiqueta, EstadoActividad.VENCIDA, getString(R.string.garantia_vencida))
            EstadoGarantia.SIN_GARANTIA ->
                Estilos.aplicarEtiqueta(
                    etiqueta, EstadoActividad.SIN_PROGRAMACION, getString(R.string.garantia_sin)
                )
        }

        val boton = tarjeta.findViewById<Button>(R.id.btnRepInstalado)
        boton.setText(if (instalado) R.string.marcar_retirado else R.string.reinstalar)
        boton.setOnClickListener {
            repo.cambiarInstalado(repuesto.optString("id"), !instalado)
            mostrarRepuestos()
        }
        return tarjeta
    }
}
