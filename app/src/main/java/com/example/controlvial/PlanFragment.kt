package com.example.controlvial

import android.content.DialogInterface
import android.os.Bundle
import android.view.View
import android.widget.AutoCompleteTextView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import org.json.JSONObject
import java.util.Date

/** Plan de mantenimiento: actividades programadas por fecha, kilometraje o ambos. */
class PlanFragment : Fragment(R.layout.fragment_plan) {

    private lateinit var repo: RepositorioMiCarro
    private lateinit var acFiltro: AutoCompleteTextView
    private lateinit var contenedor: LinearLayout
    private lateinit var tvSinActividades: View

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        repo = RepositorioMiCarro(requireContext())

        acFiltro = view.findViewById(R.id.acFiltroPlan)
        contenedor = view.findViewById(R.id.contenedorActividades)
        tvSinActividades = view.findViewById(R.id.tvSinActividades)

        view.findViewById<View>(R.id.btnNuevaActividad).setOnClickListener { mostrarFormulario(null) }
        view.findViewById<View>(R.id.btnCategorias).setOnClickListener { mostrarCategorias() }
    }

    override fun onResume() {
        super.onResume()
        actualizarFiltro()
        mostrarActividades()
    }

    // ---------------------------------------------------------------
    // Lista
    // ---------------------------------------------------------------

    /** Por defecto se muestra el plan del vehículo principal. */
    private fun actualizarFiltro() {
        val todos = getString(R.string.todos_vehiculos)
        val placas = repo.obtenerVehiculos().map { it.optString("placa") }
        val actual = acFiltro.text.toString()
        val inicial = when {
            actual == todos || actual in placas -> actual
            else -> repo.vehiculoPrincipal()?.optString("placa") ?: todos
        }
        Ayudas.asignar(requireContext(), acFiltro, listOf(todos) + placas, inicial) {
            mostrarActividades()
        }
    }

    private fun orden(actividad: JSONObject, estado: EstadoActividad): Int {
        if (actividad.optBoolean("pausada", false)) return 5
        return when (estado) {
            EstadoActividad.VENCIDA -> 0
            EstadoActividad.PROXIMA -> 1
            EstadoActividad.AL_DIA -> 2
            EstadoActividad.SIN_PROGRAMACION -> 3
        }
    }

    private fun mostrarActividades() {
        val todos = getString(R.string.todos_vehiculos)
        val filtro = acFiltro.text.toString().ifEmpty { todos }
        val vehiculos = repo.obtenerVehiculos()

        val actividades = repo.obtenerPlan()
            .filter { filtro == todos || it.optString("placa") == filtro }
            .map { it to repo.estadoActividadPlan(it, vehiculos) }
            .sortedBy { (actividad, estado) -> orden(actividad, estado.general) }

        tvSinActividades.visibility = if (actividades.isEmpty()) View.VISIBLE else View.GONE
        contenedor.removeAllViews()
        actividades.forEach { (actividad, estado) ->
            contenedor.addView(crearTarjeta(actividad, estado))
        }
    }

    private fun textoEstado(estado: EstadoActividad): String = getString(
        when (estado) {
            EstadoActividad.AL_DIA -> R.string.plan_estado_al_dia
            EstadoActividad.PROXIMA -> R.string.plan_estado_proxima
            EstadoActividad.VENCIDA -> R.string.plan_estado_vencida
            EstadoActividad.SIN_PROGRAMACION -> R.string.plan_estado_sin_programacion
        }
    )

    private fun detalleActividad(actividad: JSONObject, estado: EstadoRegistro): String {
        val lineas = mutableListOf<String>()
        lineas.add(getString(R.string.det_vehiculo, actividad.optString("placa")))

        val descripcion = actividad.optString("descripcion")
        if (descripcion.isNotBlank()) lineas.add(descripcion)

        val proximaFecha = actividad.optString("proxima_fecha")
        val proximoKm = actividad.optInt("proximo_km", 0)

        if (proximaFecha.isNotEmpty()) {
            val dias = estado.diasRestantes
            val relativo = when {
                dias == null -> ""
                dias < 0 -> getString(R.string.plan_hace_dias, -dias)
                dias == 0 -> getString(R.string.plan_hoy)
                else -> getString(R.string.plan_en_dias, dias)
            }
            lineas.add(getString(R.string.plan_proxima_fecha, proximaFecha) + relativo)
        }

        if (proximoKm > 0) {
            val diferencia = proximoKm - estado.kmActual
            val relativo = if (diferencia >= 0) {
                getString(R.string.plan_faltan_km, Formato.kilometros(diferencia))
            } else {
                getString(R.string.plan_superado_km, Formato.kilometros(-diferencia))
            }
            lineas.add(getString(R.string.plan_proximo_km, Formato.kilometros(proximoKm)) + relativo)
        }

        if (proximaFecha.isEmpty() && proximoKm <= 0) {
            lineas.add(getString(R.string.plan_sin_programacion))
        }

        val intervaloDias = actividad.optInt("intervalo_dias", 0)
        val intervaloKm = actividad.optInt("intervalo_km", 0)
        val repeticion = mutableListOf<String>()
        if (intervaloDias > 0) repeticion.add(getString(R.string.plan_cada_dias, intervaloDias))
        if (intervaloKm > 0) repeticion.add(getString(R.string.plan_cada_km, Formato.kilometros(intervaloKm)))
        if (repeticion.isNotEmpty()) {
            lineas.add(getString(R.string.plan_repite, repeticion.joinToString(" / ")))
        }

        val ultimaFecha = actividad.optString("ultima_fecha")
        if (ultimaFecha.isNotEmpty()) {
            lineas.add(
                getString(
                    R.string.plan_ultimo_servicio,
                    ultimaFecha,
                    Formato.kilometros(actividad.optInt("ultimo_km", 0))
                )
            )
        }

        if (!actividad.optBoolean("alertas_activas", true)) {
            lineas.add(getString(R.string.plan_alertas_desactivadas))
        }
        return lineas.joinToString("\n")
    }

    private fun crearTarjeta(actividad: JSONObject, estado: EstadoRegistro): View {
        val tarjeta = layoutInflater.inflate(R.layout.item_actividad, contenedor, false)
        val pausada = actividad.optBoolean("pausada", false)

        tarjeta.findViewById<TextView>(R.id.tvActNombre).text = actividad.optString("nombre")

        val categoria = tarjeta.findViewById<TextView>(R.id.tvActCategoria)
        categoria.text = actividad.optString("categoria")
        categoria.visibility = if (categoria.text.isNullOrBlank()) View.GONE else View.VISIBLE

        tarjeta.findViewById<TextView>(R.id.tvActDetalle).text = detalleActividad(actividad, estado)

        val etiqueta = tarjeta.findViewById<TextView>(R.id.tvActEstado)
        if (pausada) {
            Estilos.aplicarEtiqueta(
                etiqueta,
                EstadoActividad.SIN_PROGRAMACION,
                getString(R.string.plan_estado_pausada)
            )
        } else {
            Estilos.aplicarEtiqueta(etiqueta, estado.general, textoEstado(estado.general))
        }

        val botonServicio = tarjeta.findViewById<View>(R.id.btnRegistrarServicio)
        botonServicio.visibility = if (pausada) View.GONE else View.VISIBLE
        botonServicio.setOnClickListener { mostrarServicio(actividad) }

        val botonMas = tarjeta.findViewById<View>(R.id.btnActMas)
        botonMas.setOnClickListener { mostrarMenu(botonMas, actividad) }
        return tarjeta
    }

    private fun mostrarMenu(ancla: View, actividad: JSONObject) {
        val pausada = actividad.optBoolean("pausada", false)
        val id = actividad.optString("id")

        val menu = PopupMenu(requireContext(), ancla)
        menu.menu.add(0, ID_EDITAR, 0, R.string.editar)
        menu.menu.add(0, ID_PAUSA, 1, if (pausada) R.string.reactivar else R.string.pausar)
        menu.menu.add(0, ID_ELIMINAR, 2, R.string.eliminar)

        menu.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                ID_EDITAR -> mostrarFormulario(actividad)
                ID_PAUSA -> {
                    repo.cambiarPausa(id, !pausada)
                    mostrarActividades()
                    Toast.makeText(
                        requireContext(),
                        if (pausada) R.string.actividad_reactivada else R.string.actividad_pausada,
                        Toast.LENGTH_SHORT
                    ).show()
                }
                ID_ELIMINAR -> confirmarEliminar(actividad)
            }
            true
        }
        menu.show()
    }

    private fun confirmarEliminar(actividad: JSONObject) {
        val id = actividad.optString("id")
        if (repo.actividadTieneHistorial(id)) {
            // RF-15: una actividad con historial no se elimina; se puede pausar.
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.actividad_eliminar_titulo)
                .setMessage(R.string.actividad_con_historial)
                .setPositiveButton(R.string.cerrar, null)
                .show()
            return
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.actividad_eliminar_titulo)
            .setMessage(getString(R.string.actividad_eliminar_mensaje, actividad.optString("nombre")))
            .setNegativeButton(R.string.cancelar, null)
            .setPositiveButton(R.string.eliminar) { _, _ ->
                repo.eliminarActividad(id)
                mostrarActividades()
                Toast.makeText(requireContext(), R.string.actividad_eliminada, Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    // ---------------------------------------------------------------
    // Crear y editar una actividad
    // ---------------------------------------------------------------

    private fun etiquetaVehiculo(vehiculo: JSONObject): String {
        val nombre = "${vehiculo.optString("marca")} ${vehiculo.optString("modelo")}".trim()
        val placa = vehiculo.optString("placa")
        return if (nombre.isEmpty()) placa else "$placa$SEPARADOR$nombre"
    }

    private fun mostrarFormulario(existente: JSONObject?) {
        val contexto = requireContext()
        val activos = repo.obtenerVehiculos().filter { !repo.esArchivado(it) }
        if (existente == null && activos.isEmpty()) {
            Toast.makeText(contexto, R.string.registrar_primero_vehiculo, Toast.LENGTH_SHORT).show()
            return
        }

        val vista = layoutInflater.inflate(R.layout.dialog_actividad, null)
        val tilVehiculo = vista.findViewById<TextInputLayout>(R.id.tilActVehiculo)
        val acVehiculo = vista.findViewById<AutoCompleteTextView>(R.id.acActVehiculo)
        val acPlantilla = vista.findViewById<AutoCompleteTextView>(R.id.acActPlantilla)
        val tilNombre = vista.findViewById<TextInputLayout>(R.id.tilActNombre)
        val etNombre = vista.findViewById<TextInputEditText>(R.id.etActNombre)
        val tilCategoria = vista.findViewById<TextInputLayout>(R.id.tilActCategoria)
        val acCategoria = vista.findViewById<AutoCompleteTextView>(R.id.acActCategoria)
        val etDescripcion = vista.findViewById<TextInputEditText>(R.id.etActDescripcion)
        val tilFecha = vista.findViewById<TextInputLayout>(R.id.tilActFecha)
        val etFecha = vista.findViewById<TextInputEditText>(R.id.etActFecha)
        val etIntervaloDias = vista.findViewById<TextInputEditText>(R.id.etActIntervaloDias)
        val etProximoKm = vista.findViewById<TextInputEditText>(R.id.etActProximoKm)
        val etIntervaloKm = vista.findViewById<TextInputEditText>(R.id.etActIntervaloKm)
        val etAntDias = vista.findViewById<TextInputEditText>(R.id.etActAntDias)
        val etAntKm = vista.findViewById<TextInputEditText>(R.id.etActAntKm)
        val interruptor = vista.findViewById<MaterialSwitch>(R.id.switchActAlertas)

        // Vehículo
        val etiquetas: List<String>
        val etiquetaInicial: String
        if (existente != null) {
            val placa = existente.optString("placa")
            etiquetas = listOf(placa)
            etiquetaInicial = placa
            tilVehiculo.isEnabled = false // el vehículo de una actividad no se cambia
        } else {
            etiquetas = activos.map { etiquetaVehiculo(it) }
            val filtroPlaca = acFiltro.text.toString()
            etiquetaInicial = activos.firstOrNull { it.optString("placa") == filtroPlaca }
                ?.let { etiquetaVehiculo(it) }
                ?: repo.vehiculoPrincipal()?.let { etiquetaVehiculo(it) }
                ?: etiquetas.first()
        }
        Ayudas.asignar(contexto, acVehiculo, etiquetas, etiquetaInicial)

        fun placaElegida(): String = acVehiculo.text.toString().substringBefore(SEPARADOR).trim()

        // Categoría
        val categorias = repo.obtenerCategorias()
        val categoriaInicial = existente?.optString("categoria")?.takeIf { it.isNotEmpty() }
            ?: categorias.firstOrNull() ?: ""
        Ayudas.asignar(contexto, acCategoria, categorias, categoriaInicial)

        // Plantillas (RF-16): rellenan el formulario; todo se puede editar después
        val plantillas = resources.getStringArray(R.array.plantillas_plan).map { it.split("|") }
        val sinPlantilla = getString(R.string.sin_plantilla)
        Ayudas.asignar(
            contexto, acPlantilla, listOf(sinPlantilla) + plantillas.map { it[0] }, sinPlantilla
        ) { elegido ->
            val plantilla = plantillas.firstOrNull { it[0] == elegido } ?: return@asignar
            val kmCada = plantilla[2].toIntOrNull() ?: 0
            val diasCada = plantilla[3].toIntOrNull() ?: 0
            val kmActual = repo.buscarVehiculo(placaElegida())?.optInt("kilometraje", 0) ?: 0

            etNombre.setText(plantilla[0])
            acCategoria.setText(
                if (categorias.contains(plantilla[1])) plantilla[1] else categorias.firstOrNull() ?: "",
                false
            )
            etIntervaloKm.setText(if (kmCada > 0) kmCada.toString() else "")
            etIntervaloDias.setText(if (diasCada > 0) diasCada.toString() else "")
            etProximoKm.setText(if (kmCada > 0 && kmActual > 0) (kmActual + kmCada).toString() else "")
            etFecha.setText(
                if (diasCada > 0) repo.formatearFecha(ReglasMantenimiento.sumarDias(Date(), diasCada)) else ""
            )
        }

        // Valores iniciales
        etAntDias.setText(
            (existente?.optInt("anticipacion_dias", ReglasMantenimiento.DIAS_ANTICIPACION)
                ?: ReglasMantenimiento.DIAS_ANTICIPACION).toString()
        )
        etAntKm.setText(
            (existente?.optInt("anticipacion_km", ReglasMantenimiento.KM_ANTICIPACION)
                ?: ReglasMantenimiento.KM_ANTICIPACION).toString()
        )
        interruptor.isChecked = existente?.optBoolean("alertas_activas", true) ?: true

        if (existente != null) {
            etNombre.setText(existente.optString("nombre"))
            etDescripcion.setText(existente.optString("descripcion"))
            etFecha.setText(existente.optString("proxima_fecha"))
            val intervaloDias = existente.optInt("intervalo_dias", 0)
            val proximoKm = existente.optInt("proximo_km", 0)
            val intervaloKm = existente.optInt("intervalo_km", 0)
            if (intervaloDias > 0) etIntervaloDias.setText(intervaloDias.toString())
            if (proximoKm > 0) etProximoKm.setText(proximoKm.toString())
            if (intervaloKm > 0) etIntervaloKm.setText(intervaloKm.toString())
        }

        etFecha.setOnClickListener { Ayudas.elegirFecha(contexto, etFecha, tilFecha) }
        vista.findViewById<View>(R.id.btnQuitarFecha).setOnClickListener { etFecha.setText("") }

        val dialogo = MaterialAlertDialogBuilder(contexto)
            .setTitle(if (existente == null) R.string.nueva_actividad_titulo else R.string.editar_actividad_titulo)
            .setView(vista)
            .setNegativeButton(R.string.cancelar, null)
            .setPositiveButton(R.string.guardar, null)
            .create()

        dialogo.setOnShowListener {
            dialogo.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener {
                tilNombre.error = null
                tilCategoria.error = null
                tilFecha.error = null

                val nombre = etNombre.text.toString().trim()
                if (nombre.isEmpty()) {
                    tilNombre.error = getString(R.string.error_nombre_actividad)
                    return@setOnClickListener
                }

                val categoria = acCategoria.text.toString().trim()
                if (categoria.isEmpty()) {
                    tilCategoria.error = getString(R.string.error_categoria)
                    return@setOnClickListener
                }

                val fechaTexto = etFecha.text.toString().trim()
                if (fechaTexto.isNotEmpty() && repo.parsearFecha(fechaTexto) == null) {
                    tilFecha.error = getString(R.string.error_fecha_invalida)
                    return@setOnClickListener
                }

                val actividad = existente ?: JSONObject()
                actividad.put("placa", if (existente != null) existente.optString("placa") else placaElegida())
                actividad.put("nombre", nombre)
                actividad.put("categoria", categoria)
                actividad.put("descripcion", etDescripcion.text.toString().trim())
                actividad.put("proxima_fecha", fechaTexto)
                actividad.put("intervalo_dias", etIntervaloDias.text.toString().trim().toIntOrNull() ?: 0)
                actividad.put("proximo_km", etProximoKm.text.toString().trim().toIntOrNull() ?: 0)
                actividad.put("intervalo_km", etIntervaloKm.text.toString().trim().toIntOrNull() ?: 0)
                actividad.put(
                    "anticipacion_dias",
                    etAntDias.text.toString().trim().toIntOrNull() ?: ReglasMantenimiento.DIAS_ANTICIPACION
                )
                actividad.put(
                    "anticipacion_km",
                    etAntKm.text.toString().trim().toIntOrNull() ?: ReglasMantenimiento.KM_ANTICIPACION
                )
                actividad.put("alertas_activas", interruptor.isChecked)
                if (existente == null) actividad.put("pausada", false)

                if (existente == null) repo.agregarActividad(actividad) else repo.actualizarActividad(actividad)

                dialogo.dismiss()
                actualizarFiltro()
                mostrarActividades()
                Toast.makeText(contexto, R.string.actividad_guardada, Toast.LENGTH_SHORT).show()
            }
        }
        dialogo.show()
    }

    // ---------------------------------------------------------------
    // Registrar un servicio realizado (RF-21, RN-03)
    // ---------------------------------------------------------------

    private fun mostrarServicio(actividad: JSONObject) {
        val contexto = requireContext()
        val placa = actividad.optString("placa")
        val kmActual = repo.buscarVehiculo(placa)?.optInt("kilometraje", 0) ?: 0
        val requiereKm = actividad.optInt("intervalo_km", 0) > 0

        val vista = layoutInflater.inflate(R.layout.dialog_servicio, null)
        val tilFecha = vista.findViewById<TextInputLayout>(R.id.tilSrvFecha)
        val etFecha = vista.findViewById<TextInputEditText>(R.id.etSrvFecha)
        val tilKm = vista.findViewById<TextInputLayout>(R.id.tilSrvKm)
        val etKm = vista.findViewById<TextInputEditText>(R.id.etSrvKm)
        val etCosto = vista.findViewById<TextInputEditText>(R.id.etSrvCosto)
        val etTaller = vista.findViewById<TextInputEditText>(R.id.etSrvTaller)

        vista.findViewById<TextView>(R.id.tvSrvInfo).text = getString(R.string.servicio_info)
        etFecha.setText(repo.formatearFecha(Date()))
        if (kmActual > 0) etKm.setText(kmActual.toString())
        etFecha.setOnClickListener { Ayudas.elegirFecha(contexto, etFecha, tilFecha) }

        val dialogo = MaterialAlertDialogBuilder(contexto)
            .setTitle(getString(R.string.registrar_servicio_titulo, actividad.optString("nombre")))
            .setView(vista)
            .setNegativeButton(R.string.cancelar, null)
            .setPositiveButton(R.string.guardar, null)
            .create()

        dialogo.setOnShowListener {
            dialogo.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener {
                tilFecha.error = null
                tilKm.error = null

                val fechaTexto = etFecha.text.toString().trim()
                val fecha = repo.parsearFecha(fechaTexto)
                if (fecha == null) {
                    tilFecha.error = getString(R.string.error_fecha_invalida)
                    return@setOnClickListener
                }
                // RN-05: un mantenimiento no puede tener una fecha futura de realización.
                if (ReglasMantenimiento.diasHasta(fecha, Date()) > 0) {
                    tilFecha.error = getString(R.string.error_fecha_servicio_futura)
                    return@setOnClickListener
                }

                val km = etKm.text.toString().trim().toIntOrNull() ?: 0
                if (requiereKm && km <= 0) {
                    tilKm.error = getString(R.string.error_km_requerido)
                    return@setOnClickListener
                }

                val costo = etCosto.text.toString().trim().toDoubleOrNull() ?: 0.0
                val taller = etTaller.text.toString().trim()

                fun guardar() {
                    repo.registrarServicioPlan(actividad, fechaTexto, km, costo, taller)
                    dialogo.dismiss()
                    mostrarActividades()
                    Toast.makeText(contexto, R.string.servicio_registrado, Toast.LENGTH_LONG).show()
                }

                // RN-06: un kilometraje menor al registrado exige confirmación.
                if (km > 0 && !ReglasMantenimiento.validarKilometraje(km, kmActual)) {
                    MaterialAlertDialogBuilder(contexto)
                        .setTitle(R.string.km_menor_titulo)
                        .setMessage(
                            getString(
                                R.string.km_menor_mensaje,
                                Formato.kilometros(km),
                                Formato.kilometros(kmActual)
                            )
                        )
                        .setNegativeButton(R.string.corregir, null)
                        .setPositiveButton(R.string.guardar_igual) { _, _ -> guardar() }
                        .show()
                } else {
                    guardar()
                }
            }
        }
        dialogo.show()
    }

    // ---------------------------------------------------------------
    // Categorías editables (RF-12)
    // ---------------------------------------------------------------

    private fun mostrarCategorias() {
        val categorias = repo.obtenerCategorias()
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.categorias_titulo)
            .setItems(categorias.toTypedArray()) { _, posicion ->
                opcionesCategoria(categorias[posicion])
            }
            .setPositiveButton(R.string.agregar) { _, _ ->
                pedirTexto(R.string.categoria_nueva, "") { nombre ->
                    if (!repo.agregarCategoria(nombre)) {
                        Toast.makeText(requireContext(), R.string.categoria_invalida, Toast.LENGTH_SHORT).show()
                    }
                    mostrarCategorias()
                }
            }
            .setNegativeButton(R.string.cerrar, null)
            .show()
    }

    private fun opcionesCategoria(nombre: String) {
        val opciones = arrayOf(getString(R.string.renombrar), getString(R.string.eliminar))
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(nombre)
            .setItems(opciones) { _, posicion ->
                if (posicion == 0) {
                    pedirTexto(R.string.categoria_renombrar, nombre) { nuevo ->
                        if (!repo.renombrarCategoria(nombre, nuevo)) {
                            Toast.makeText(requireContext(), R.string.categoria_invalida, Toast.LENGTH_SHORT).show()
                        }
                        mostrarActividades()
                        mostrarCategorias()
                    }
                } else {
                    if (!repo.eliminarCategoria(nombre)) {
                        Toast.makeText(requireContext(), R.string.categoria_en_uso, Toast.LENGTH_LONG).show()
                    }
                    mostrarCategorias()
                }
            }
            .setNegativeButton(R.string.cancelar) { _, _ -> mostrarCategorias() }
            .show()
    }

    private fun pedirTexto(titulo: Int, inicial: String, alAceptar: (String) -> Unit) {
        val vista = layoutInflater.inflate(R.layout.dialog_texto, null)
        val campo = vista.findViewById<TextInputEditText>(R.id.etTexto)
        campo.setText(inicial)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(titulo)
            .setView(vista)
            .setNegativeButton(R.string.cancelar) { _, _ -> mostrarCategorias() }
            .setPositiveButton(R.string.guardar) { _, _ -> alAceptar(campo.text.toString()) }
            .show()
    }

    companion object {
        private const val SEPARADOR = " · "
        private const val ID_EDITAR = 1
        private const val ID_PAUSA = 2
        private const val ID_ELIMINAR = 3
    }
}
