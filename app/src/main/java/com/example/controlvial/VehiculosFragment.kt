package com.example.controlvial

import android.content.Intent
import android.os.Bundle
import android.content.DialogInterface
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import org.json.JSONObject
import java.util.Calendar
import java.util.Locale

/** Registro y lista de vehículos: principal, editar, archivar, kilometraje y eliminar. */
class VehiculosFragment : Fragment(R.layout.fragment_vehiculos) {

    // tipo -> (marca -> líneas)
    private val tiposYMarcas = mutableMapOf<String, Map<String, List<String>>>()
    private lateinit var repo: RepositorioMiCarro

    private lateinit var tarjetaFormulario: View
    private lateinit var tilPlaca: TextInputLayout
    private lateinit var etPlaca: TextInputEditText
    private lateinit var acTipo: AutoCompleteTextView
    private lateinit var acMarca: AutoCompleteTextView
    private lateinit var acModelo: AutoCompleteTextView
    private lateinit var acAnio: AutoCompleteTextView
    private lateinit var tilColor: TextInputLayout
    private lateinit var etColor: TextInputEditText
    private lateinit var etKilometraje: TextInputEditText
    private lateinit var tilVin: TextInputLayout
    private lateinit var etVin: TextInputEditText
    private lateinit var acCombustible: AutoCompleteTextView
    private lateinit var etCilindraje: TextInputEditText
    private lateinit var ivFotoForm: ImageView
    private lateinit var tvTitulo: TextView
    private lateinit var tvSinVehiculos: View
    private lateinit var contenedor: LinearLayout

    /** Foto elegida para el vehículo que se está registrando (dirección de la imagen). */
    private var fotoForm = ""

    /** Qué hacer con la foto cuando el usuario elija una (formulario o diálogo de edición). */
    private var alElegirFoto: ((String) -> Unit)? = null

    private val selectorFoto =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                // Se pide permiso persistente para poder volver a leer la foto más adelante.
                try {
                    requireContext().contentResolver.takePersistableUriPermission(
                        uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (_: Exception) {
                }
                alElegirFoto?.invoke(uri.toString())
            }
        }

    private fun pedirFoto(alElegir: (String) -> Unit) {
        alElegirFoto = alElegir
        selectorFoto.launch(arrayOf("image/*"))
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        repo = RepositorioMiCarro(requireContext())

        tarjetaFormulario = view.findViewById(R.id.cardFormVehiculo)
        tilPlaca = view.findViewById(R.id.tilPlaca)
        etPlaca = view.findViewById(R.id.etPlaca)
        acTipo = view.findViewById(R.id.acTipo)
        acMarca = view.findViewById(R.id.acMarca)
        acModelo = view.findViewById(R.id.acModelo)
        acAnio = view.findViewById(R.id.acAnio)
        tilColor = view.findViewById(R.id.tilColor)
        etColor = view.findViewById(R.id.etColor)
        etKilometraje = view.findViewById(R.id.etKilometraje)
        tilVin = view.findViewById(R.id.tilVin)
        etVin = view.findViewById(R.id.etVin)
        acCombustible = view.findViewById(R.id.acCombustible)
        etCilindraje = view.findViewById(R.id.etCilindraje)
        ivFotoForm = view.findViewById(R.id.ivFotoForm)
        tvTitulo = view.findViewById(R.id.tvTituloVehiculos)
        tvSinVehiculos = view.findViewById(R.id.tvSinVehiculos)
        contenedor = view.findViewById(R.id.contenedorVehiculos)

        cargarCatalogo()
        prepararFormulario()

        view.findViewById<View>(R.id.btnNuevoVehiculo).setOnClickListener {
            tarjetaFormulario.visibility =
                if (tarjetaFormulario.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }
        view.findViewById<View>(R.id.btnGuardarVehiculo).setOnClickListener { guardarVehiculo() }
        view.findViewById<View>(R.id.btnElegirFoto).setOnClickListener {
            pedirFoto { direccion ->
                fotoForm = direccion
                Ayudas.mostrarFoto(requireContext(), ivFotoForm, direccion)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        mostrarVehiculos()
    }

    // ---------------------------------------------------------------
    // Formulario de alta
    // ---------------------------------------------------------------

    private fun cargarCatalogo() {
        tiposYMarcas.clear()
        try {
            val texto = resources.openRawResource(R.raw.tipos_marcas_lineas)
                .bufferedReader().use { it.readText() }
            val json = JSONObject(texto)

            val tipos = json.keys()
            while (tipos.hasNext()) {
                val tipo = tipos.next()
                if (tipo.equals("Otro", ignoreCase = true)) continue

                val marcasJson = json.getJSONObject(tipo)
                val marcas = mutableMapOf<String, List<String>>()
                val iteradorMarcas = marcasJson.keys()
                while (iteradorMarcas.hasNext()) {
                    val marca = iteradorMarcas.next()
                    val lineasJson = marcasJson.getJSONArray(marca)
                    val lineas = mutableListOf<String>()
                    for (i in 0 until lineasJson.length()) lineas.add(lineasJson.getString(i))
                    marcas[marca] = lineas
                }
                tiposYMarcas[tipo] = marcas
            }
        } catch (e: Exception) {
            tiposYMarcas.clear()
            tiposYMarcas["Automóvil"] = mapOf("CHEVROLET" to listOf("SPARK", "ONIX"))
        }
    }

    /** Llena una lista desplegable y selecciona un valor inicial. */
    private fun asignar(
        lista: AutoCompleteTextView,
        opciones: List<String>,
        seleccionInicial: String? = opciones.firstOrNull(),
        alElegir: (String) -> Unit = {}
    ) {
        lista.setAdapter(
            ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, opciones)
        )
        lista.setText(seleccionInicial ?: "", false)
        lista.setOnItemClickListener { padre, _, posicion, _ ->
            alElegir(padre.getItemAtPosition(posicion).toString())
        }
    }

    private fun prepararFormulario() {
        val tipos = tiposYMarcas.keys.sorted()
        asignar(acTipo, tipos) { tipo -> cargarMarcas(tipo) }
        cargarMarcas(acTipo.text.toString())

        val anioActual = Calendar.getInstance().get(Calendar.YEAR)
        val anios = (anioActual + 1 downTo 1960).map { it.toString() }
        asignar(acAnio, anios, anioActual.toString())

        val combustibles = resources.getStringArray(R.array.combustibles).toList()
        asignar(acCombustible, combustibles, combustibles.first())
    }

    private fun cargarMarcas(tipo: String) {
        val marcas = tiposYMarcas[tipo]?.keys?.sorted() ?: listOf("OTRA")
        asignar(acMarca, marcas) { marca -> cargarModelos(tipo, marca) }
        cargarModelos(tipo, acMarca.text.toString())
    }

    private fun cargarModelos(tipo: String, marca: String) {
        val modelos = tiposYMarcas[tipo]?.get(marca) ?: listOf("OTRA")
        asignar(acModelo, modelos)
    }

    private fun limpiarFormulario() {
        etPlaca.text?.clear()
        etColor.text?.clear()
        etKilometraje.text?.clear()
        tilPlaca.error = null
        tilColor.error = null
        etVin.text?.clear()
        etCilindraje.text?.clear()
        tilVin.error = null
        fotoForm = ""
        Ayudas.mostrarFoto(requireContext(), ivFotoForm, "")
        prepararFormulario()
    }

    private fun guardarVehiculo() {
        tilPlaca.error = null
        tilColor.error = null

        val placa = etPlaca.text.toString().trim()
            .uppercase(Locale.getDefault())
            .replace("-", "")
        val tipo = acTipo.text.toString()
        val marca = acMarca.text.toString()
        val modelo = acModelo.text.toString()

        if (!placa.matches(Regex("^[A-Z]{3}[0-9]{2}[A-Z0-9]$"))) {
            tilPlaca.error = getString(R.string.error_placa_formato)
            etPlaca.requestFocus()
            return
        }

        if (repo.existePlaca(placa)) {
            tilPlaca.error = getString(R.string.error_placa_duplicada)
            etPlaca.requestFocus()
            return
        }

        if (marca.isBlank()) {
            Toast.makeText(requireContext(), R.string.error_marca, Toast.LENGTH_SHORT).show()
            return
        }

        val color = formatearColor(etColor.text.toString())
        if (color.isNotEmpty() && !colorValido(color)) {
            tilColor.error = getString(R.string.error_color)
            etColor.requestFocus()
            return
        }

        val vin = etVin.text.toString().trim().uppercase(Locale.getDefault())
        if (vin.isNotEmpty() && !vinValido(vin)) {
            tilVin.error = getString(R.string.error_vin)
            etVin.requestFocus()
            return
        }

        val anio = acAnio.text.toString().toIntOrNull() ?: Calendar.getInstance().get(Calendar.YEAR)
        val kilometraje = etKilometraje.text.toString().trim().toIntOrNull() ?: 0

        val vehiculo = JSONObject().apply {
            put("placa", placa)
            put("tipo", tipo)
            put("marca", marca)
            put("modelo", modelo)
            put("anio", anio)
            put("color", color)
            put("kilometraje", kilometraje)
            put("vin", vin)
            put("combustible", combustibleElegido(acCombustible.text.toString()))
            put("cilindraje", etCilindraje.text.toString().trim().toIntOrNull() ?: 0)
            put("foto", fotoForm)
        }

        repo.agregarVehiculo(vehiculo)

        limpiarFormulario()
        tarjetaFormulario.visibility = View.GONE
        mostrarVehiculos()

        Toast.makeText(requireContext(), R.string.vehiculo_registrado, Toast.LENGTH_SHORT).show()
    }

    /** El color es opcional (RF-02); se escribe con la primera letra de cada palabra en mayúscula. */
    private fun formatearColor(texto: String): String =
        texto.trim()
            .split(Regex("\\s+"))
            .filter { it.isNotEmpty() }
            .joinToString(" ") { palabra ->
                palabra.lowercase().replaceFirstChar { letra -> letra.uppercase() }
            }

    /** RF-02: un VIN tiene 17 caracteres y nunca usa las letras I, O ni Q. */
    private fun vinValido(vin: String): Boolean =
        vin.matches(Regex("^[A-HJ-NPR-Z0-9]{17}$"))

    /** La primera opción de la lista ("Sin especificar") se guarda como vacío. */
    private fun combustibleElegido(texto: String): String {
        val combustibles = resources.getStringArray(R.array.combustibles)
        return if (texto == combustibles.first()) "" else texto
    }

    private fun colorValido(color: String): Boolean =
        color.matches(Regex("^[a-zA-ZáéíóúÁÉÍÓÚñÑ\\s]+$"))

    // ---------------------------------------------------------------
    // Lista
    // ---------------------------------------------------------------

    private fun mostrarVehiculos() {
        val vehiculos = repo.obtenerVehiculos()
        val documentos = repo.obtenerDocumentos()
        val activos = vehiculos.filter { !repo.esArchivado(it) }
        val archivados = vehiculos.filter { repo.esArchivado(it) }

        tvTitulo.text = getString(R.string.mis_vehiculos_n, activos.size)
        tvSinVehiculos.visibility = if (vehiculos.isEmpty()) View.VISIBLE else View.GONE
        contenedor.removeAllViews()

        activos.forEach { contenedor.addView(crearTarjeta(it, documentos)) }

        if (archivados.isNotEmpty()) {
            val encabezado = layoutInflater.inflate(
                R.layout.item_encabezado, contenedor, false
            ) as TextView
            encabezado.text = getString(R.string.archivados_n, archivados.size)
            contenedor.addView(encabezado)

            archivados.forEach { contenedor.addView(crearTarjeta(it, documentos)) }
        }
    }

    private fun crearTarjeta(vehiculo: JSONObject, documentos: List<JSONObject>): View {
        val contexto = requireContext()
        val placa = vehiculo.optString("placa")
        val archivado = repo.esArchivado(vehiculo)
        val esPrincipal = vehiculo.optBoolean("principal", false)
        val cantidadRegistros = documentos.count {
            it.optString("placa").equals(placa, ignoreCase = true)
        }

        val tarjeta = layoutInflater.inflate(
            R.layout.item_vehiculo, contenedor, false
        ) as MaterialCardView

        tarjeta.findViewById<TextView>(R.id.tvVehPlaca).text = placa
        Ayudas.mostrarFoto(
            contexto,
            tarjeta.findViewById(R.id.ivVehFoto),
            vehiculo.optString("foto"),
            56
        )
        tarjeta.findViewById<TextView>(R.id.tvVehDetalle).text =
            detalleVehiculo(vehiculo, cantidadRegistros)

        val pendiente = tarjeta.findViewById<TextView>(R.id.tvVehPendiente)
        if (vehiculo.optBoolean("pendiente_sync", false)) {
            Estilos.aplicarEtiqueta(
                pendiente, EstadoActividad.PROXIMA, getString(R.string.pendiente_sync)
            )
            pendiente.visibility = View.VISIBLE
        } else {
            pendiente.visibility = View.GONE
        }

        tarjeta.findViewById<View>(R.id.tvVehPrincipal).visibility =
            if (esPrincipal) View.VISIBLE else View.GONE
        tarjeta.findViewById<View>(R.id.tvVehArchivado).visibility =
            if (archivado) View.VISIBLE else View.GONE

        val botonKm = tarjeta.findViewById<View>(R.id.btnActualizarKm)
        botonKm.visibility = if (archivado) View.GONE else View.VISIBLE
        botonKm.setOnClickListener {
            DialogoKilometraje.mostrar(this, placa) { mostrarVehiculos() }
        }

        val botonPrincipal = tarjeta.findViewById<View>(R.id.btnHacerPrincipal)
        botonPrincipal.visibility = if (archivado || esPrincipal) View.GONE else View.VISIBLE
        botonPrincipal.setOnClickListener {
            repo.marcarPrincipal(placa)
            mostrarVehiculos()
            Toast.makeText(
                requireContext(),
                getString(R.string.vehiculo_ahora_principal, placa),
                Toast.LENGTH_SHORT
            ).show()
        }

        val botonReactivar = tarjeta.findViewById<View>(R.id.btnReactivar)
        botonReactivar.visibility = if (archivado) View.VISIBLE else View.GONE
        botonReactivar.setOnClickListener {
            repo.reactivarVehiculo(placa)
            mostrarVehiculos()
            Toast.makeText(requireContext(), R.string.vehiculo_reactivado, Toast.LENGTH_SHORT).show()
        }

        val botonMas = tarjeta.findViewById<View>(R.id.btnMas)
        botonMas.setOnClickListener { mostrarMenu(botonMas, placa, archivado) }

        if (esPrincipal) {
            tarjeta.strokeColor = ContextCompat.getColor(contexto, R.color.primary)
            tarjeta.strokeWidth = Estilos.dp(contexto, 2)
        }

        return tarjeta
    }

    private fun detalleVehiculo(vehiculo: JSONObject, cantidadRegistros: Int): String {
        val lineas = mutableListOf<String>()

        val tipo = vehiculo.optString("tipo")
        val marca = vehiculo.optString("marca")
        val modelo = vehiculo.optString("modelo")
        val anio = vehiculo.optString("anio")
        val color = vehiculo.optString("color")

        if (tipo.isNotBlank()) lineas.add(getString(R.string.det_tipo, tipo))
        if (marca.isNotBlank() || modelo.isNotBlank()) {
            lineas.add(getString(R.string.det_vehiculo, "$marca $modelo".trim()))
        }
        if (anio.isNotBlank()) lineas.add(getString(R.string.det_anio, anio))
        if (color.isNotBlank()) lineas.add(getString(R.string.det_color, color))
        val combustible = vehiculo.optString("combustible")
        if (combustible.isNotBlank()) lineas.add(getString(R.string.det_combustible, combustible))
        val cilindraje = vehiculo.optInt("cilindraje", 0)
        if (cilindraje > 0) lineas.add(getString(R.string.det_cilindraje, cilindraje))
        val vin = vehiculo.optString("vin")
        if (vin.isNotBlank()) lineas.add(getString(R.string.det_vin, vin))
        lineas.add(
            getString(R.string.det_km, Formato.kilometros(vehiculo.optInt("kilometraje", 0)))
        )
        lineas.add(getString(R.string.det_registros, cantidadRegistros))

        return lineas.joinToString("\n")
    }

    // ---------------------------------------------------------------
    // Menú "más opciones"
    // ---------------------------------------------------------------

    private fun mostrarMenu(ancla: View, placa: String, archivado: Boolean) {
        val menu = PopupMenu(requireContext(), ancla)
        menu.menu.add(0, ID_EDITAR, 0, R.string.editar)
        menu.menu.add(0, ID_LECTURAS, 1, R.string.ver_lecturas)
        if (!archivado) menu.menu.add(0, ID_ARCHIVAR, 2, R.string.archivar)
        menu.menu.add(0, ID_ELIMINAR, 3, R.string.eliminar)

        menu.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                ID_EDITAR -> editarVehiculo(placa)
                ID_LECTURAS -> verLecturas(placa)
                ID_ARCHIVAR -> confirmarArchivar(placa)
                ID_ELIMINAR -> confirmarEliminar(placa)
            }
            true
        }
        menu.show()
    }

    private fun verLecturas(placa: String) {
        val lecturas = repo.obtenerLecturas(placa)
        val dialogo = MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.lecturas_titulo, placa))
            .setPositiveButton(R.string.cerrar, null)

        if (lecturas.isEmpty()) {
            dialogo.setMessage(R.string.sin_lecturas)
        } else {
            val items = lecturas.map {
                getString(
                    R.string.lectura_item,
                    it.optString("fecha"),
                    Formato.kilometros(it.optInt("km", 0))
                )
            }.toTypedArray()
            dialogo.setItems(items, null)
        }
        dialogo.show()
    }

    private fun editarVehiculo(placa: String) {
        val vehiculo = repo.buscarVehiculo(placa) ?: return
        val vista = layoutInflater.inflate(R.layout.dialog_editar_vehiculo, null)

        val etMarca = vista.findViewById<TextInputEditText>(R.id.etEdMarca)
        val etLinea = vista.findViewById<TextInputEditText>(R.id.etEdLinea)
        val tilAnio = vista.findViewById<TextInputLayout>(R.id.tilEdAnio)
        val etAnio = vista.findViewById<TextInputEditText>(R.id.etEdAnio)
        val tilColorEd = vista.findViewById<TextInputLayout>(R.id.tilEdColor)
        val etColorEd = vista.findViewById<TextInputEditText>(R.id.etEdColor)

        etMarca.setText(vehiculo.optString("marca"))
        etLinea.setText(vehiculo.optString("modelo"))
        etAnio.setText(vehiculo.optString("anio"))
        etColorEd.setText(vehiculo.optString("color"))

        val tilVinEd = vista.findViewById<TextInputLayout>(R.id.tilEdVin)
        val etVinEd = vista.findViewById<TextInputEditText>(R.id.etEdVin)
        val acCombustibleEd = vista.findViewById<AutoCompleteTextView>(R.id.acEdCombustible)
        val etCilindrajeEd = vista.findViewById<TextInputEditText>(R.id.etEdCilindraje)
        val ivFotoEd = vista.findViewById<ImageView>(R.id.ivEdFoto)

        val combustibles = resources.getStringArray(R.array.combustibles).toList()
        val combustibleActual = vehiculo.optString("combustible")
        asignar(
            acCombustibleEd,
            combustibles,
            if (combustibleActual.isNotEmpty() && combustibles.contains(combustibleActual)) {
                combustibleActual
            } else {
                combustibles.first()
            }
        )
        etVinEd.setText(vehiculo.optString("vin"))
        val cilindrajeActual = vehiculo.optInt("cilindraje", 0)
        if (cilindrajeActual > 0) etCilindrajeEd.setText(cilindrajeActual.toString())

        var fotoEd = vehiculo.optString("foto")
        Ayudas.mostrarFoto(requireContext(), ivFotoEd, fotoEd)
        vista.findViewById<View>(R.id.btnEdFoto).setOnClickListener {
            pedirFoto { direccion ->
                fotoEd = direccion
                Ayudas.mostrarFoto(requireContext(), ivFotoEd, direccion)
            }
        }

        val dialogo: AlertDialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.editar_titulo, placa))
            .setView(vista)
            .setNegativeButton(R.string.cancelar, null)
            .setPositiveButton(R.string.guardar, null)
            .create()

        dialogo.setOnShowListener {
            dialogo.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener {
                tilAnio.error = null
                tilColorEd.error = null
                tilVinEd.error = null

                val anioMaximo = Calendar.getInstance().get(Calendar.YEAR) + 1
                val anio = etAnio.text.toString().trim().toIntOrNull()
                if (anio == null || anio < 1960 || anio > anioMaximo) {
                    tilAnio.error = getString(R.string.error_anio, anioMaximo)
                    return@setOnClickListener
                }

                val color = formatearColor(etColorEd.text.toString())
                if (color.isNotEmpty() && !colorValido(color)) {
                    tilColorEd.error = getString(R.string.error_color)
                    return@setOnClickListener
                }

                val vin = etVinEd.text.toString().trim().uppercase(Locale.getDefault())
                if (vin.isNotEmpty() && !vinValido(vin)) {
                    tilVinEd.error = getString(R.string.error_vin)
                    return@setOnClickListener
                }

                repo.editarVehiculo(
                    placa,
                    etMarca.text.toString().trim().uppercase(Locale.getDefault()),
                    etLinea.text.toString().trim().uppercase(Locale.getDefault()),
                    anio,
                    color,
                    vin,
                    combustibleElegido(acCombustibleEd.text.toString()),
                    etCilindrajeEd.text.toString().trim().toIntOrNull() ?: 0,
                    fotoEd
                )
                dialogo.dismiss()
                mostrarVehiculos()
                Toast.makeText(requireContext(), R.string.vehiculo_actualizado, Toast.LENGTH_SHORT).show()
            }
        }
        dialogo.show()
    }

    private fun confirmarArchivar(placa: String) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.archivar_titulo)
            .setMessage(getString(R.string.archivar_mensaje, placa))
            .setNegativeButton(R.string.cancelar, null)
            .setPositiveButton(R.string.archivar) { _, _ ->
                repo.archivarVehiculo(placa)
                mostrarVehiculos()
                Toast.makeText(requireContext(), R.string.vehiculo_archivado, Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun confirmarEliminar(placa: String) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.eliminar_vehiculo_titulo)
            .setMessage(getString(R.string.eliminar_vehiculo_mensaje, placa))
            .setNegativeButton(R.string.cancelar, null)
            .setPositiveButton(R.string.eliminar) { _, _ ->
                repo.eliminarVehiculo(placa)
                mostrarVehiculos()
                Toast.makeText(requireContext(), R.string.vehiculo_eliminado, Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    companion object {
        private const val ID_EDITAR = 1
        private const val ID_LECTURAS = 2
        private const val ID_ARCHIVAR = 3
        private const val ID_ELIMINAR = 4
    }
}
