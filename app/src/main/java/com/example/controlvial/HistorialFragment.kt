package com.example.controlvial

import android.content.ActivityNotFoundException
import android.content.DialogInterface
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.View
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import org.json.JSONObject
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Date

/** Registro y consulta de documentos y servicios (RF-17 a RF-22, RF-34). */
class HistorialFragment : Fragment(R.layout.fragment_historial) {

    private lateinit var repo: RepositorioMiCarro

    private lateinit var scroll: ScrollView
    private lateinit var tarjetaFormulario: View
    private lateinit var tvTituloFormulario: TextView
    private lateinit var tilVehiculo: TextInputLayout
    private lateinit var acVehiculo: AutoCompleteTextView
    private lateinit var acTipoRegistro: AutoCompleteTextView
    private lateinit var grupoServicio: View
    private lateinit var grupoCostos: View
    private lateinit var acClase: AutoCompleteTextView
    private lateinit var tilDescripcion: TextInputLayout
    private lateinit var etDescripcion: TextInputEditText
    private lateinit var tilFecha: TextInputLayout
    private lateinit var etFecha: TextInputEditText
    private lateinit var tilKm: TextInputLayout
    private lateinit var etKm: TextInputEditText
    private lateinit var etProximoKm: TextInputEditText
    private lateinit var etManoObra: TextInputEditText
    private lateinit var etCostoRepuestos: TextInputEditText
    private lateinit var etOtrosCostos: TextInputEditText
    private lateinit var etCosto: TextInputEditText
    private lateinit var etTaller: TextInputEditText
    private lateinit var botonGuardar: Button
    private lateinit var botonCancelar: View
    private lateinit var acFiltro: AutoCompleteTextView
    private lateinit var tvTitulo: TextView
    private lateinit var tvSinRegistros: View
    private lateinit var contenedor: LinearLayout
    private lateinit var botonMas: View
    private lateinit var botonFiltros: Button

    /** Filtros avanzados activos (RF-35). */
    private var filtroAvanzado = FiltroHistorial()

    /** Registro al que se le está adjuntando un archivo (RF-20). */
    private var registroAdjunto = ""

    private val selectorAdjunto =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            val id = registroAdjunto
            if (uri != null && id.isNotEmpty()) {
                // Permiso persistente para poder volver a abrir el archivo más adelante.
                try {
                    requireContext().contentResolver.takePersistableUriPermission(
                        uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (_: Exception) {
                }
                repo.agregarAdjunto(id, nombreDe(uri), uri.toString())
                mostrarRegistros()
                mostrarAdjuntos(id)
            }
        }

    /** Exportación del historial a un archivo CSV (RF-38). */
    private val selectorCsv =
        registerForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
            if (uri != null) escribirCsv(uri)
        }

    /** Registro que se está editando (null = se está creando uno nuevo). */
    private var editando: JSONObject? = null
    private var tipoAnterior = ""
    private var fechaAnterior = ""

    /** Cantidad de registros que se muestran; "Mostrar más" la aumenta (rendimiento, RNF-03). */
    private var limite = TAMANO_PAGINA

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        repo = RepositorioMiCarro(requireContext())

        scroll = view.findViewById(R.id.scrollHistorial)
        tarjetaFormulario = view.findViewById(R.id.cardFormRegistro)
        tvTituloFormulario = view.findViewById(R.id.tvTituloFormulario)
        tilVehiculo = view.findViewById(R.id.tilVehiculo)
        acVehiculo = view.findViewById(R.id.acVehiculo)
        acTipoRegistro = view.findViewById(R.id.acTipoRegistro)
        grupoServicio = view.findViewById(R.id.grupoServicio)
        grupoCostos = view.findViewById(R.id.grupoCostos)
        acClase = view.findViewById(R.id.acClase)
        tilDescripcion = view.findViewById(R.id.tilDescripcionReg)
        etDescripcion = view.findViewById(R.id.etDescripcionReg)
        tilFecha = view.findViewById(R.id.tilFecha)
        etFecha = view.findViewById(R.id.etFecha)
        tilKm = view.findViewById(R.id.tilKm)
        etKm = view.findViewById(R.id.etKm)
        etProximoKm = view.findViewById(R.id.etProximoKm)
        etManoObra = view.findViewById(R.id.etManoObra)
        etCostoRepuestos = view.findViewById(R.id.etCostoRepuestos)
        etOtrosCostos = view.findViewById(R.id.etOtrosCostos)
        etCosto = view.findViewById(R.id.etCosto)
        etTaller = view.findViewById(R.id.etTaller)
        botonGuardar = view.findViewById(R.id.btnGuardarRegistro)
        botonCancelar = view.findViewById(R.id.btnCancelarEdicion)
        acFiltro = view.findViewById(R.id.acFiltro)
        tvTitulo = view.findViewById(R.id.tvTituloHistorial)
        tvSinRegistros = view.findViewById(R.id.tvSinRegistros)
        contenedor = view.findViewById(R.id.contenedorRegistros)
        botonMas = view.findViewById(R.id.btnMasRegistros)
        botonFiltros = view.findViewById(R.id.btnFiltros)
        botonFiltros.setOnClickListener { mostrarFiltros() }
        view.findViewById<View>(R.id.btnExportarCsv).setOnClickListener {
            selectorCsv.launch(getString(R.string.csv_nombre_archivo))
        }

        prepararFormulario()

        view.findViewById<View>(R.id.btnNuevoRegistro).setOnClickListener {
            if (tarjetaFormulario.visibility == View.VISIBLE) cerrarFormulario() else abrirNuevo()
        }
        botonGuardar.setOnClickListener { guardarRegistro() }
        botonCancelar.setOnClickListener { cerrarFormulario() }
        botonMas.setOnClickListener {
            limite += TAMANO_PAGINA
            mostrarRegistros()
        }
    }

    override fun onResume() {
        super.onResume()
        actualizarOpcionesVehiculo()
        mostrarRegistros()
    }

    // ---------------------------------------------------------------
    // Formulario
    // ---------------------------------------------------------------

    private fun prepararFormulario() {
        val contexto = requireContext()
        val primero = Catalogo.TIPOS_REGISTRO.first()

        Ayudas.asignar(contexto, acTipoRegistro, Catalogo.TIPOS_REGISTRO, primero) {
            actualizarCamposPorTipo(it)
        }
        Ayudas.asignar(contexto, acClase, Catalogo.CLASES, Catalogo.CLASES.first())
        actualizarCamposPorTipo(primero)

        etFecha.setOnClickListener { Ayudas.elegirFecha(contexto, etFecha, tilFecha) }

        // RN-04: el total se calcula solo con los tres costos; el usuario puede ajustarlo después.
        etManoObra.doAfterTextChanged { recalcularTotal() }
        etCostoRepuestos.doAfterTextChanged { recalcularTotal() }
        etOtrosCostos.doAfterTextChanged { recalcularTotal() }
    }

    private fun numero(campo: TextInputEditText): Double =
        campo.text.toString().trim().toDoubleOrNull() ?: 0.0

    private fun recalcularTotal() {
        val suma = ReglasMantenimiento.costoTotal(
            numero(etManoObra), numero(etCostoRepuestos), numero(etOtrosCostos)
        )
        etCosto.setText(textoNumero(suma))
    }

    private fun textoNumero(valor: Double): String =
        if (valor <= 0.0) "" else BigDecimal(valor).setScale(2, RoundingMode.HALF_UP)
            .stripTrailingZeros().toPlainString()

    /** Un documento usa la fecha como vencimiento; un servicio, como fecha de realización. */
    private fun actualizarCamposPorTipo(tipo: String) {
        val esDocumento = Catalogo.tieneVencimiento(tipo)
        grupoServicio.visibility = if (esDocumento) View.GONE else View.VISIBLE
        grupoCostos.visibility = if (esDocumento) View.GONE else View.VISIBLE
        tilFecha.hint = getString(
            if (esDocumento) R.string.hint_fecha_vencimiento else R.string.hint_fecha_servicio
        )
    }

    private fun etiquetaVehiculo(vehiculo: JSONObject): String {
        val placa = vehiculo.optString("placa")
        val nombre = "${vehiculo.optString("marca")} ${vehiculo.optString("modelo")}".trim()
        return if (nombre.isEmpty()) placa else "$placa$SEPARADOR$nombre"
    }

    private fun placaDeEtiqueta(etiqueta: String): String = etiqueta.substringBefore(SEPARADOR).trim()

    /** Rellena la lista de vehículos del formulario y del filtro, conservando lo elegido. */
    private fun actualizarOpcionesVehiculo() {
        val contexto = requireContext()
        val vehiculos = repo.obtenerVehiculos()

        if (editando == null) {
            // Solo los vehículos activos se ofrecen para registrar; el filtro incluye todos.
            val etiquetas = vehiculos.filter { !repo.esArchivado(it) }.map { etiquetaVehiculo(it) }
            val actual = acVehiculo.text.toString()
            val seleccion = when {
                etiquetas.contains(actual) -> actual
                else -> repo.vehiculoPrincipal()?.let { etiquetaVehiculo(it) } ?: ""
            }
            Ayudas.asignar(contexto, acVehiculo, etiquetas, seleccion)
        }

        val todos = getString(R.string.todos_vehiculos)
        val placas = vehiculos.map { it.optString("placa") }
        val filtroActual = acFiltro.text.toString()
        val filtro = if (filtroActual in placas) filtroActual else todos
        Ayudas.asignar(contexto, acFiltro, listOf(todos) + placas, filtro) {
            limite = TAMANO_PAGINA
            mostrarRegistros()
        }
    }

    private fun limpiarFormulario() {
        listOf(
            etDescripcion, etFecha, etKm, etProximoKm,
            etManoObra, etCostoRepuestos, etOtrosCostos, etCosto, etTaller
        ).forEach { it.text?.clear() }
        listOf(tilDescripcion, tilFecha, tilKm).forEach { it.error = null }
        acClase.setText(Catalogo.CLASES.first(), false)
        acTipoRegistro.setText(Catalogo.TIPOS_REGISTRO.first(), false)
        actualizarCamposPorTipo(Catalogo.TIPOS_REGISTRO.first())
    }

    private fun abrirNuevo() {
        editando = null
        limpiarFormulario()
        tilVehiculo.isEnabled = true
        actualizarOpcionesVehiculo()
        tvTituloFormulario.setText(R.string.nuevo_registro_titulo)
        botonGuardar.setText(R.string.guardar_registro)
        botonCancelar.visibility = View.GONE
        tarjetaFormulario.visibility = View.VISIBLE
    }

    private fun cerrarFormulario() {
        editando = null
        limpiarFormulario()
        tilVehiculo.isEnabled = true
        tarjetaFormulario.visibility = View.GONE
        actualizarOpcionesVehiculo()
    }

    /** RF-22: carga un registro existente en el formulario para modificarlo. */
    private fun iniciarEdicion(registro: JSONObject) {
        limpiarFormulario()
        editando = JSONObject(registro.toString())
        tipoAnterior = registro.optString("tipo")
        fechaAnterior = registro.optString("fecha")

        val placa = registro.optString("placa")
        Ayudas.asignar(requireContext(), acVehiculo, listOf(placa), placa)
        tilVehiculo.isEnabled = false // el vehículo de un registro no se cambia

        val tipo = registro.optString("tipo")
        acTipoRegistro.setText(tipo, false)
        actualizarCamposPorTipo(tipo)

        acClase.setText(
            registro.optString("clase").ifEmpty { Catalogo.CLASES.first() }, false
        )
        etDescripcion.setText(registro.optString("descripcion"))
        etFecha.setText(registro.optString("fecha"))

        val km = registro.optInt("kilometraje", 0)
        val proximoKm = registro.optInt("proximo_kilometraje", 0)
        if (km > 0) etKm.setText(km.toString())
        if (proximoKm > 0) etProximoKm.setText(proximoKm.toString())

        etManoObra.setText(textoNumero(registro.optDouble("mano_obra", 0.0)))
        etCostoRepuestos.setText(textoNumero(registro.optDouble("costo_repuestos", 0.0)))
        etOtrosCostos.setText(textoNumero(registro.optDouble("otros_costos", 0.0)))
        // El total se escribe al final para conservar un ajuste hecho antes por el usuario.
        etCosto.setText(textoNumero(registro.optDouble("costo", 0.0)))
        etTaller.setText(registro.optString("taller_repuestos"))

        tvTituloFormulario.setText(R.string.editar_registro_titulo)
        botonGuardar.setText(R.string.guardar_cambios)
        botonCancelar.visibility = View.VISIBLE
        tarjetaFormulario.visibility = View.VISIBLE
        scroll.post { scroll.smoothScrollTo(0, 0) }
    }

    // ---------------------------------------------------------------
    // Guardar
    // ---------------------------------------------------------------

    private fun guardarRegistro() {
        listOf(tilDescripcion, tilFecha, tilKm).forEach { it.error = null }

        val vehiculos = repo.obtenerVehiculos()
        if (vehiculos.isEmpty()) {
            Toast.makeText(requireContext(), R.string.registrar_primero_vehiculo, Toast.LENGTH_SHORT).show()
            return
        }

        val placa = placaDeEtiqueta(acVehiculo.text.toString())
        val vehiculo = vehiculos.firstOrNull { it.optString("placa").equals(placa, ignoreCase = true) }
        if (vehiculo == null) {
            Toast.makeText(requireContext(), R.string.elige_vehiculo, Toast.LENGTH_SHORT).show()
            return
        }

        val tipo = acTipoRegistro.text.toString()
        val esServicio = !Catalogo.tieneVencimiento(tipo)

        val fechaTexto = etFecha.text.toString().trim()
        if (fechaTexto.isEmpty()) {
            tilFecha.error = getString(R.string.error_fecha_vacia)
            return
        }
        val fecha = repo.parsearFecha(fechaTexto)
        if (fecha == null) {
            tilFecha.error = getString(R.string.error_fecha_invalida)
            return
        }

        val km = etKm.text.toString().trim().toIntOrNull() ?: 0
        val descripcion = etDescripcion.text.toString().trim()

        if (esServicio) {
            // RN-05: un mantenimiento no puede tener una fecha futura de realización.
            if (ReglasMantenimiento.diasHasta(fecha, Date()) > 0) {
                tilFecha.error = getString(R.string.error_fecha_servicio_futura)
                return
            }
            // RF-18: vehículo, fecha, kilometraje, tipo de servicio y descripción son obligatorios.
            if (descripcion.isEmpty()) {
                tilDescripcion.error = getString(R.string.error_descripcion)
                return
            }
            if (km <= 0) {
                tilKm.error = getString(R.string.error_km_servicio)
                return
            }
        }

        val manoObra = if (esServicio) numero(etManoObra) else 0.0
        val repuestos = if (esServicio) numero(etCostoRepuestos) else 0.0
        val otros = if (esServicio) numero(etOtrosCostos) else 0.0
        val totalEscrito = etCosto.text.toString().trim().toDoubleOrNull()
        val total = totalEscrito ?: ReglasMantenimiento.costoTotal(manoObra, repuestos, otros)

        val registro = editando ?: JSONObject()
        if (editando == null) {
            registro.put("placa", vehiculo.optString("placa"))
            registro.put("vehiculo", vehiculo.optString("tipo"))
        }
        registro.put("tipo", tipo)
        if (!registro.has("actividad_id")) registro.put("categoria", Catalogo.categoriaDe(tipo))
        registro.put("clase", if (esServicio) acClase.text.toString() else "")
        registro.put("descripcion", if (esServicio) descripcion else "")
        registro.put("fecha", fechaTexto)
        registro.put("kilometraje", km)
        registro.put("proximo_kilometraje", etProximoKm.text.toString().trim().toIntOrNull() ?: 0)
        registro.put("mano_obra", manoObra)
        registro.put("costo_repuestos", repuestos)
        registro.put("otros_costos", otros)
        registro.put("costo", total)
        registro.put("taller_repuestos", etTaller.text.toString().trim())

        // RN-06: un kilometraje menor al registrado exige confirmación (solo al crear).
        val kmVehiculo = vehiculo.optInt("kilometraje", 0)
        if (editando == null && km > 0 && !ReglasMantenimiento.validarKilometraje(km, kmVehiculo)) {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.km_menor_titulo)
                .setMessage(
                    getString(
                        R.string.km_menor_mensaje,
                        Formato.kilometros(km),
                        Formato.kilometros(kmVehiculo)
                    )
                )
                .setNegativeButton(R.string.cancelar, null)
                .setPositiveButton(R.string.guardar_igual) { _, _ -> persistir(registro) }
                .show()
            return
        }

        persistir(registro)
    }

    private fun persistir(registro: JSONObject) {
        val seEditaba = editando != null
        if (seEditaba) {
            repo.actualizarDocumento(registro, tipoAnterior, fechaAnterior)
        } else {
            repo.agregarDocumento(registro)
        }

        cerrarFormulario()
        mostrarRegistros()

        // Revisa de inmediato si hay vencimientos para avisar al usuario.
        WorkManager.getInstance(requireContext())
            .enqueue(OneTimeWorkRequestBuilder<RecordatorioWorker>().build())

        Toast.makeText(
            requireContext(),
            if (seEditaba) R.string.registro_actualizado else R.string.registro_guardado,
            Toast.LENGTH_SHORT
        ).show()
    }

    // ---------------------------------------------------------------
    // Lista
    // ---------------------------------------------------------------

    private fun mostrarRegistros() {
        val todos = getString(R.string.todos_vehiculos)
        val filtro = acFiltro.text.toString().ifEmpty { todos }

        val vehiculos = repo.obtenerVehiculos()
        val documentos = repo.obtenerDocumentos()

        val activos = filtroAvanzado.cantidadActivos()
        botonFiltros.text =
            if (activos > 0) getString(R.string.filtros_n, activos) else getString(R.string.filtros)

        val visibles = registrosFiltrados(documentos)

        tvTitulo.text = getString(R.string.historial_n, visibles.size)
        tvSinRegistros.visibility = if (visibles.isEmpty()) View.VISIBLE else View.GONE
        botonMas.visibility = if (visibles.size > limite) View.VISIBLE else View.GONE
        contenedor.removeAllViews()

        for ((indice, registro) in visibles.take(limite)) {
            contenedor.addView(crearTarjeta(indice, registro, vehiculos))
        }
    }

    /** Registros que cumplen el filtro de vehículo y los filtros avanzados, del más reciente al más antiguo. */
    private fun registrosFiltrados(documentos: List<JSONObject>): List<IndexedValue<JSONObject>> {
        val todos = getString(R.string.todos_vehiculos)
        val filtro = acFiltro.text.toString().ifEmpty { todos }

        // Se conserva el índice original para poder eliminar el registro correcto.
        return documentos.withIndex()
            .filter { filtro == todos || it.value.optString("placa") == filtro }
            .filter { cumpleFiltroAvanzado(it.value) }
            .sortedByDescending { repo.parsearFecha(it.value.optString("fecha"))?.time ?: 0L }
    }

    private fun cumpleFiltroAvanzado(registro: JSONObject): Boolean =
        filtroAvanzado.cumple(
            repo.categoriaDeRegistro(registro),
            registro.optString("clase"),
            registro.optString("taller_repuestos"),
            registro.optDouble("costo", 0.0),
            repo.parsearFecha(registro.optString("fecha"))
        )

    // ---------------------------------------------------------------
    // Filtros avanzados (RF-35)
    // ---------------------------------------------------------------

    private fun mostrarFiltros() {
        val contexto = requireContext()
        val vista = layoutInflater.inflate(R.layout.dialog_filtros, null)

        val acCategoria = vista.findViewById<AutoCompleteTextView>(R.id.acFilCategoria)
        val acClaseFiltro = vista.findViewById<AutoCompleteTextView>(R.id.acFilClase)
        val etTallerFiltro = vista.findViewById<TextInputEditText>(R.id.etFilTaller)
        val etMin = vista.findViewById<TextInputEditText>(R.id.etFilCostoMin)
        val etMax = vista.findViewById<TextInputEditText>(R.id.etFilCostoMax)
        val tilDesde = vista.findViewById<TextInputLayout>(R.id.tilFilDesde)
        val etDesde = vista.findViewById<TextInputEditText>(R.id.etFilDesde)
        val tilHasta = vista.findViewById<TextInputLayout>(R.id.tilFilHasta)
        val etHasta = vista.findViewById<TextInputEditText>(R.id.etFilHasta)

        val cualquiera = getString(R.string.filtro_cualquiera)
        val categorias = listOf(cualquiera) +
                (repo.obtenerCategorias() + Catalogo.CATEGORIA_DOCUMENTOS).distinct()
        Ayudas.asignar(
            contexto, acCategoria, categorias,
            filtroAvanzado.categoria.ifEmpty { cualquiera }
        )
        Ayudas.asignar(
            contexto, acClaseFiltro, listOf(cualquiera) + Catalogo.CLASES,
            filtroAvanzado.clase.ifEmpty { cualquiera }
        )

        etTallerFiltro.setText(filtroAvanzado.taller)
        filtroAvanzado.costoMin?.let { etMin.setText(textoNumero(it)) }
        filtroAvanzado.costoMax?.let { etMax.setText(textoNumero(it)) }
        filtroAvanzado.desde?.let { etDesde.setText(repo.formatearFecha(it)) }
        filtroAvanzado.hasta?.let { etHasta.setText(repo.formatearFecha(it)) }

        etDesde.setOnClickListener { Ayudas.elegirFecha(contexto, etDesde, tilDesde) }
        etHasta.setOnClickListener { Ayudas.elegirFecha(contexto, etHasta, tilHasta) }

        val dialogo = MaterialAlertDialogBuilder(contexto)
            .setTitle(R.string.filtros_titulo)
            .setView(vista)
            .setNegativeButton(R.string.cancelar, null)
            .setNeutralButton(R.string.limpiar_filtros) { _, _ ->
                filtroAvanzado = FiltroHistorial()
                limite = TAMANO_PAGINA
                mostrarRegistros()
            }
            .setPositiveButton(R.string.aplicar, null)
            .create()

        dialogo.setOnShowListener {
            dialogo.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener {
                tilDesde.error = null
                tilHasta.error = null

                val desdeTexto = etDesde.text.toString().trim()
                val hastaTexto = etHasta.text.toString().trim()
                val desde = if (desdeTexto.isEmpty()) null else repo.parsearFecha(desdeTexto)
                val hasta = if (hastaTexto.isEmpty()) null else repo.parsearFecha(hastaTexto)

                if (desde != null && hasta != null && ReglasMantenimiento.diasHasta(hasta, desde) < 0) {
                    tilHasta.error = getString(R.string.error_rango_fechas)
                    return@setOnClickListener
                }

                filtroAvanzado = FiltroHistorial(
                    categoria = acCategoria.text.toString().takeIf { it != cualquiera } ?: "",
                    clase = acClaseFiltro.text.toString().takeIf { it != cualquiera } ?: "",
                    taller = etTallerFiltro.text.toString().trim(),
                    costoMin = etMin.text.toString().trim().toDoubleOrNull(),
                    costoMax = etMax.text.toString().trim().toDoubleOrNull(),
                    desde = desde,
                    hasta = hasta
                )
                limite = TAMANO_PAGINA
                dialogo.dismiss()
                mostrarRegistros()
            }
        }
        dialogo.show()
    }

    private fun detalleRegistro(registro: JSONObject): String {
        val tipo = registro.optString("tipo")
        val lineas = mutableListOf<String>()

        val clase = registro.optString("clase")
        if (clase.isNotEmpty()) lineas.add(getString(R.string.det_clase, clase))
        val descripcion = registro.optString("descripcion")
        if (descripcion.isNotEmpty()) lineas.add(descripcion)

        val esVencimiento = Catalogo.tieneVencimiento(tipo) && !registro.has("actividad_id")
        lineas.add(
            getString(
                if (esVencimiento) R.string.det_vencimiento else R.string.det_fecha_servicio,
                registro.optString("fecha")
            )
        )

        val km = registro.optInt("kilometraje", 0)
        val proximoKm = registro.optInt("proximo_kilometraje", 0)
        if (km > 0) lineas.add(getString(R.string.det_km_servicio, Formato.kilometros(km)))
        if (proximoKm > 0) lineas.add(getString(R.string.det_proximo_km, Formato.kilometros(proximoKm)))

        val manoObra = registro.optDouble("mano_obra", 0.0)
        val repuestos = registro.optDouble("costo_repuestos", 0.0)
        val otros = registro.optDouble("otros_costos", 0.0)
        if (manoObra > 0 || repuestos > 0 || otros > 0) {
            lineas.add(
                getString(
                    R.string.det_desglose,
                    Formato.moneda(manoObra),
                    Formato.moneda(repuestos),
                    Formato.moneda(otros)
                )
            )
        }

        val costo = registro.optDouble("costo", 0.0)
        if (costo > 0.0) lineas.add(getString(R.string.det_costo, Formato.moneda(costo)))

        val taller = registro.optString("taller_repuestos")
        if (taller.isNotEmpty()) lineas.add(getString(R.string.det_taller, taller))
        return lineas.joinToString("\n")
    }

    private fun crearTarjeta(indice: Int, registro: JSONObject, vehiculos: List<JSONObject>): View {
        val tarjeta = layoutInflater.inflate(R.layout.item_registro, contenedor, false)
        val tipo = registro.optString("tipo")
        val placa = registro.optString("placa")

        tarjeta.findViewById<TextView>(R.id.tvRegTitulo).text =
            getString(R.string.titulo_registro, tipo, placa)
        tarjeta.findViewById<TextView>(R.id.tvRegDetalle).text = detalleRegistro(registro)

        val estado = repo.estadoRegistro(registro, vehiculos)
        Estilos.aplicarEtiqueta(
            tarjeta.findViewById(R.id.tvRegEstado),
            estado.general,
            textoEstado(estado)
        )

        val pendiente = tarjeta.findViewById<TextView>(R.id.tvRegPendiente)
        if (registro.optBoolean("pendiente_sync", false)) {
            Estilos.aplicarEtiqueta(
                pendiente, EstadoActividad.PROXIMA, getString(R.string.pendiente_sync)
            )
            pendiente.visibility = View.VISIBLE
        } else {
            pendiente.visibility = View.GONE
        }

        // Los repuestos se asocian a servicios, no a documentos (SOAT, etc.).
        val botonRepuestos = tarjeta.findViewById<Button>(R.id.btnRepuestos)
        val esServicio = !Catalogo.tieneVencimiento(tipo) || registro.has("actividad_id")
        botonRepuestos.visibility = if (esServicio) View.VISIBLE else View.GONE
        botonRepuestos.text = getString(
            R.string.repuestos_n,
            repo.repuestosDeRegistro(registro.optString("id")).size
        )
        botonRepuestos.setOnClickListener {
            DialogoRepuestos.mostrar(this, registro.optString("id")) { mostrarRegistros() }
        }

        val botonAdjuntos = tarjeta.findViewById<Button>(R.id.btnAdjuntos)
        botonAdjuntos.text = getString(R.string.adjuntos_n, repo.adjuntosDe(registro).size)
        botonAdjuntos.setOnClickListener { mostrarAdjuntos(registro.optString("id")) }

        tarjeta.findViewById<View>(R.id.btnEditarRegistro).setOnClickListener { iniciarEdicion(registro) }
        tarjeta.findViewById<View>(R.id.btnEliminarRegistro).setOnClickListener {
            confirmarEliminar(indice, tipo, placa)
        }
        return tarjeta
    }

    private fun textoEstado(estado: EstadoRegistro): String = when (estado.general) {
        EstadoActividad.VENCIDA ->
            if (estado.porFecha == EstadoActividad.VENCIDA) getString(R.string.estado_vencido)
            else getString(R.string.estado_vencido_km)

        EstadoActividad.PROXIMA ->
            if (estado.porFecha == EstadoActividad.PROXIMA) {
                when (val dias = estado.diasRestantes ?: 0) {
                    0 -> getString(R.string.estado_vence_hoy)
                    1 -> getString(R.string.estado_vence_manana)
                    else -> getString(R.string.estado_vence_en_dias, dias)
                }
            } else {
                getString(R.string.estado_proximo_km)
            }

        EstadoActividad.AL_DIA -> getString(R.string.estado_vigente)
        EstadoActividad.SIN_PROGRAMACION -> getString(R.string.estado_registrado)
    }

    // ---------------------------------------------------------------
    // Adjuntos: fotos y documentos de soporte (RF-20)
    // ---------------------------------------------------------------

    private fun nombreDe(uri: Uri): String {
        try {
            requireContext().contentResolver
                .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val columna = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (columna >= 0) return cursor.getString(columna) ?: ""
                    }
                }
        } catch (_: Exception) {
        }
        return getString(R.string.adjunto_sin_nombre)
    }

    private fun mostrarAdjuntos(idRegistro: String) {
        val registro = repo.buscarDocumento(idRegistro) ?: return
        val adjuntos = repo.adjuntosDe(registro)

        val dialogo = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.adjuntos_titulo)
            .setPositiveButton(R.string.adjuntar_archivo) { _, _ ->
                registroAdjunto = idRegistro
                selectorAdjunto.launch(arrayOf("image/*", "application/pdf"))
            }
            .setNegativeButton(R.string.cerrar, null)

        if (adjuntos.isEmpty()) {
            dialogo.setMessage(R.string.sin_adjuntos)
        } else {
            val nombres = adjuntos.map { it.optString("nombre") }.toTypedArray()
            dialogo.setItems(nombres) { _, posicion ->
                opcionesAdjunto(idRegistro, adjuntos[posicion], posicion)
            }
        }
        dialogo.show()
    }

    private fun opcionesAdjunto(idRegistro: String, adjunto: JSONObject, posicion: Int) {
        val acciones = arrayOf(getString(R.string.abrir), getString(R.string.eliminar))
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(adjunto.optString("nombre"))
            .setItems(acciones) { _, cual ->
                if (cual == 0) {
                    abrirAdjunto(adjunto.optString("uri"))
                } else {
                    repo.quitarAdjunto(idRegistro, posicion)
                    mostrarRegistros()
                    mostrarAdjuntos(idRegistro)
                }
            }
            .setNegativeButton(R.string.cancelar) { _, _ -> mostrarAdjuntos(idRegistro) }
            .show()
    }

    private fun abrirAdjunto(direccion: String) {
        val contexto = requireContext()
        try {
            val uri = Uri.parse(direccion)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, contexto.contentResolver.getType(uri) ?: "*/*")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(contexto, R.string.adjunto_sin_aplicacion, Toast.LENGTH_LONG).show()
        } catch (_: Exception) {
            Toast.makeText(contexto, R.string.adjunto_no_disponible, Toast.LENGTH_LONG).show()
        }
    }

    // ---------------------------------------------------------------
    // Exportar a CSV (RF-38)
    // ---------------------------------------------------------------

    private fun escribirCsv(uri: Uri) {
        val contexto = requireContext()
        val registros = registrosFiltrados(repo.obtenerDocumentos()).map { it.value }

        val encabezados = resources.getStringArray(R.array.csv_encabezados).toList()
        val filas = registros.map { r ->
            listOf(
                r.optString("placa"),
                r.optString("tipo"),
                repo.categoriaDeRegistro(r),
                r.optString("clase"),
                r.optString("descripcion"),
                r.optString("fecha"),
                r.optInt("kilometraje", 0).toString(),
                r.optInt("proximo_kilometraje", 0).toString(),
                Csv.numero(r.optDouble("mano_obra", 0.0)),
                Csv.numero(r.optDouble("costo_repuestos", 0.0)),
                Csv.numero(r.optDouble("otros_costos", 0.0)),
                Csv.numero(r.optDouble("costo", 0.0)),
                r.optString("taller_repuestos")
            )
        }

        try {
            contexto.contentResolver.openOutputStream(uri)?.use {
                it.write(Csv.generar(encabezados, filas).toByteArray(Charsets.UTF_8))
            }
            Toast.makeText(
                contexto,
                getString(R.string.csv_exportado, registros.size),
                Toast.LENGTH_LONG
            ).show()
        } catch (e: Exception) {
            Bitacora.error("Exportar", "No se pudo guardar el CSV", e)
            Toast.makeText(contexto, R.string.csv_error, Toast.LENGTH_LONG).show()
        }
    }

    private fun confirmarEliminar(indice: Int, tipo: String, placa: String) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.eliminar_registro_titulo)
            .setMessage(getString(R.string.eliminar_registro_mensaje, tipo, placa))
            .setNegativeButton(R.string.cancelar, null)
            .setPositiveButton(R.string.eliminar) { _, _ ->
                repo.eliminarDocumento(indice)
                mostrarRegistros()
                Toast.makeText(requireContext(), R.string.registro_eliminado, Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    companion object {
        private const val SEPARADOR = " · "
        private const val TAMANO_PAGINA = 50
    }
}
