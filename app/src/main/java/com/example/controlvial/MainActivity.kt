package com.example.controlvial

import android.Manifest
import android.app.DatePickerDialog
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.google.firebase.auth.FirebaseAuth
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    private lateinit var etPlaca: EditText
    private lateinit var spTipoVehiculo: Spinner
    private lateinit var spMarca: Spinner
    private lateinit var spModelo: Spinner
    private lateinit var spAnio: Spinner
    private lateinit var etColor: EditText
    private lateinit var etKilometraje: EditText
    private lateinit var btnGuardarVehiculo: Button

    private lateinit var tvVehiculosCantidad: TextView
    private lateinit var contenedorVehiculos: LinearLayout

    private lateinit var spVehiculoDocumento: Spinner
    private lateinit var spDocumento: Spinner
    private lateinit var etFecha: EditText
    private lateinit var btnGuardar: Button

    private lateinit var tvCantidad: TextView
    private lateinit var contenedorDocumentos: LinearLayout

    private lateinit var tvTotalVehiculos: TextView
    private lateinit var tvTotalDocumentos: TextView
    private lateinit var tvProximosVencer: TextView
    private lateinit var tvVencidos: TextView
    private lateinit var contenedorAlertas: LinearLayout

    private val vehiculos = mutableListOf<JSONObject>()
    private val documentos = mutableListOf<JSONObject>()
    private val firestoreSync by lazy { FirestoreSync() }

    private val preferencias by lazy {
        getSharedPreferences("ControlVial", Context.MODE_PRIVATE)
    }

    // Navegación
    private lateinit var raiz: LinearLayout
    private lateinit var vistasOriginales: List<View>
    private lateinit var cabecera: View
    private lateinit var piePagina: View

    private lateinit var seccionesInicio: List<View>
    private lateinit var seccionesVehiculos: List<View>
    private lateinit var seccionesDocumentos: List<View>
    private lateinit var seccionesVencimientos: List<View>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        conectarVistas()
        configurarSpinners() // <-- Llena los desplegables correctamente
        configurarNavegacion()

        cargarVehiculos()
        cargarDocumentos()
        recuperarVehiculosAntiguos()
        actualizarSelectorVehiculos()

        mostrarVehiculos()
        mostrarDocumentos()
        actualizarPanelResumen()

        configurarFecha()

        btnGuardarVehiculo.setOnClickListener {
            guardarVehiculo()
        }

        btnGuardar.setOnClickListener {
            guardarDocumento()
        }

        solicitarPermisoNotificaciones()
        ProgramadorRecordatorios.programar(this)
    }

    // =========================================================
    // NAVEGACIÓN PRINCIPAL
    // =========================================================

    private fun configurarNavegacion() {
        val contenido = findViewById<ViewGroup>(android.R.id.content)
        val scroll = contenido.getChildAt(0) as? ScrollView

        if (scroll == null) {
            Toast.makeText(
                this,
                "No se encontró el contenedor principal",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        raiz = scroll.getChildAt(0) as LinearLayout

        vistasOriginales = (0 until raiz.childCount).map {
            raiz.getChildAt(it)
        }

        /*
         * Estructura esperada de activity_main.xml:
         * 0: Encabezado
         * 1-7: Resumen, estadísticas y alertas
         * 8-11: Mi garaje y registro de vehículos
         * 12: Título "Mis vehículos" + contenedor
         * 13-16: Documentación y registro de documentos
         * 17: Título "Mis documentos" + contenedor
         * 18: Pie de página
         */
        if (vistasOriginales.size < 19) {
            Toast.makeText(
                this,
                "Revisa la estructura de activity_main.xml",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        cabecera = vistasOriginales[0]
        piePagina = vistasOriginales[18]

        seccionesInicio = vistasOriginales.subList(1, 8)
        seccionesVehiculos = vistasOriginales.subList(8, 13)
        seccionesDocumentos = vistasOriginales.subList(13, 18)

        seccionesVencimientos = listOf(
            vistasOriginales[1],
            vistasOriginales[2],
            vistasOriginales[5],
            vistasOriginales[6],
            vistasOriginales[7]
        )

        // Barra con 5 botones distribuidos equitativamente para verse en vertical sin scroll
        val barra = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.WHITE)
            setPadding(dpNavegacion(2), dpNavegacion(4), dpNavegacion(2), dpNavegacion(4))
            weightSum = 5f
        }

        barra.addView(crearBotonNavegacionAjustado("Inicio") {
            mostrarPantalla("inicio")
        })

        barra.addView(crearBotonNavegacionAjustado("Vehículos") {
            mostrarPantalla("vehiculos")
        })

        barra.addView(crearBotonNavegacionAjustado("Documentos") {
            mostrarPantalla("documentos")
        })

        barra.addView(crearBotonNavegacionAjustado("Alertas") {
            mostrarPantalla("vencimientos")
        })

        barra.addView(crearBotonNavegacionAjustado("Salir") {
            confirmarCerrarSesion()
        })

        raiz.addView(
            barra,
            1,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        mostrarPantalla("inicio")
    }

    private fun crearBotonNavegacionAjustado(
        titulo: String,
        accion: () -> Unit
    ): Button {
        return Button(this).apply {
            text = titulo
            textSize = 10.5f
            isAllCaps = false
            setTextColor(Color.rgb(18, 59, 99))
            setBackgroundColor(Color.rgb(232, 239, 247))
            minHeight = dpNavegacion(40)
            minimumHeight = dpNavegacion(40)
            setPadding(dpNavegacion(2), 0, dpNavegacion(2), 0)

            layoutParams = LinearLayout.LayoutParams(
                0,
                dpNavegacion(40)
            ).apply {
                weight = 1f
                setMargins(dpNavegacion(2), 0, dpNavegacion(2), 0)
            }

            setOnClickListener { accion() }
        }
    }

    private fun mostrarPantalla(pantalla: String) {
        if (!::vistasOriginales.isInitialized) return

        vistasOriginales.forEach {
            it.visibility = View.GONE
        }

        cabecera.visibility = View.VISIBLE
        piePagina.visibility = View.VISIBLE

        val secciones = when (pantalla) {
            "vehiculos" -> seccionesVehiculos
            "documentos" -> seccionesDocumentos
            "vencimientos" -> seccionesVencimientos
            else -> seccionesInicio
        }

        secciones.forEach {
            it.visibility = View.VISIBLE
        }

        val scroll = findViewById<ViewGroup>(android.R.id.content)
            .getChildAt(0) as? ScrollView

        scroll?.post {
            scroll.smoothScrollTo(0, 0)
        }
    }

    private fun confirmarCerrarSesion() {
        AlertDialog.Builder(this)
            .setTitle("Cerrar sesión")
            .setMessage("¿Deseas salir de tu cuenta de ControlVial?")
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Cerrar sesión") { _, _ ->
                FirebaseAuth.getInstance().signOut()

                val intent = Intent(this, LoginActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_CLEAR_TASK
                }

                startActivity(intent)
                finish()
            }
            .show()
    }

    private fun dpNavegacion(valor: Int): Int {
        return (valor * resources.displayMetrics.density).toInt()
    }

    // =========================================================
    // CONECTAR VISTAS Y SPINNERS
    // =========================================================

    private fun conectarVistas() {
        etPlaca = findViewById(R.id.etPlaca)
        spTipoVehiculo = findViewById(R.id.spTipoVehiculo)
        spMarca = findViewById(R.id.spMarca)
        spModelo = findViewById(R.id.spModelo)
        spAnio = findViewById(R.id.spAnio)
        etColor = findViewById(R.id.etColor)
        etKilometraje = findViewById(R.id.etKilometraje)
        btnGuardarVehiculo = findViewById(R.id.btnGuardarVehiculo)

        tvVehiculosCantidad = findViewById(R.id.tvVehiculosCantidad)
        contenedorVehiculos = findViewById(R.id.contenedorVehiculos)

        spVehiculoDocumento = findViewById(R.id.spVehiculoDocumento)
        spDocumento = findViewById(R.id.spDocumento)
        etFecha = findViewById(R.id.etFecha)
        btnGuardar = findViewById(R.id.btnGuardar)

        tvCantidad = findViewById(R.id.tvCantidad)
        contenedorDocumentos = findViewById(R.id.contenedorDocumentos)

        tvTotalVehiculos = findViewById(R.id.tvTotalVehiculos)
        tvTotalDocumentos = findViewById(R.id.tvTotalDocumentos)
        tvProximosVencer = findViewById(R.id.tvProximosVencer)
        tvVencidos = findViewById(R.id.tvVencidos)
        contenedorAlertas = findViewById(R.id.contenedorAlertas)
    }

    private fun configurarSpinners() {
        val tiposVehiculo = arrayOf("Carro", "Moto", "Camión", "Otro")
        val adapterVehiculo = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            tiposVehiculo
        )
        spTipoVehiculo.adapter = adapterVehiculo

        val marcasVehiculo = arrayOf(
            "Chevrolet", "Renault", "Mazda", "Kia", "Toyota", 
            "Ford", "Nissan", "Volkswagen", "Suzuki", "Hyundai", 
            "Honda", "Yamaha", "Bajaj", "AKT", "Otra"
        )
        val adapterMarca = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            marcasVehiculo
        )
        spMarca.adapter = adapterMarca

        val modelosPorMarca = mapOf(
            "Chevrolet" to arrayOf("Onix", "Spark", "Sail", "Tracker", "Beat", "Captiva", "Aveo", "Otro"),
            "Renault" to arrayOf("Logan", "Sandero", "Duster", "Stepway", "Kwid", "Clio", "Otro"),
            "Mazda" to arrayOf("2", "3", "CX-30", "CX-5", "6", "Otro"),
            "Kia" to arrayOf("Picanto", "Rio", "Sportage", "Cerato", "Seltos", "Otro"),
            "Toyota" to arrayOf("Corolla", "Hilux", "Fortuner", "Prado", "Yaris", "Otro"),
            "Ford" to arrayOf("Fiesta", "EcoSport", "Escape", "Explorer", "Ranger", "Otro"),
            "Nissan" to arrayOf("March", "Versa", "Sentra", "Kicks", "Frontier", "Otro"),
            "Volkswagen" to arrayOf("Gol", "Jetta", "Polo", "T-Cross", "Amarok", "Otro"),
            "Suzuki" to arrayOf("Swift", "Vitara", "Jimny", "S-Cross", "Alto", "Otro"),
            "Hyundai" to arrayOf("i10", "Accent", "Tucson", "Creta", "Kona", "Otro"),
            "Honda" to arrayOf("Civic", "CR-V", "HR-V", "Fit", "City", "Otro"),
            "Yamaha" to arrayOf("FZ", "MT-09", "NMAX", "XTZ", "R6", "BWS", "Otro"),
            "Bajaj" to arrayOf("Pulsar", "Boxer", "Discover", "Dominar", "Otro"),
            "AKT" to arrayOf("NKD", "CR4", "TT", "Dynamic", "Otro"),
            "Otra" to arrayOf("Generico", "Otro")
        )

        spMarca.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val marcaSeleccionada = parent?.getItemAtPosition(position).toString()
                val modelos = modelosPorMarca[marcaSeleccionada] ?: arrayOf("Otro")
                val adapterModelo = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, modelos)
                spModelo.adapter = adapterModelo
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        val anioActual = Calendar.getInstance().get(Calendar.YEAR)
        val anios = (anioActual + 1 downTo 1980).map { it.toString() }.toTypedArray()
        val adapterAnio = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, anios)
        spAnio.adapter = adapterAnio

        val tiposDocumento = arrayOf(
            // Documentos
            "SOAT",
            "Tecnomecánica",
            "Seguro Todo Riesgo",
            "Extintor",
            "Licencia",
            // Mantenimientos
            "Cambio de Aceite",
            "Filtros",
            "Frenos",
            "Llantas",
            "Batería",
            "Refrigeración",
            "Suspensión",
            "Transmisión",
            "Mantenimiento General",
            "Otro"
        )
        val adapterDocumento = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            tiposDocumento
        )
        spDocumento.adapter = adapterDocumento
    }

    // =========================================================
    // PERMISOS DE NOTIFICACIONES
    // =========================================================

    private fun solicitarPermisoNotificaciones() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    1001
                )
            }
        }
    }

    // =========================================================
    // SELECTOR DE FECHA
    // =========================================================

    private fun configurarFecha() {
        etFecha.setOnClickListener {
            val calendario = Calendar.getInstance()

            val dialogo = DatePickerDialog(
                this,
                { _, year, month, dayOfMonth ->
                    val fecha = String.format(
                        Locale.getDefault(),
                        "%02d/%02d/%04d",
                        dayOfMonth,
                        month + 1,
                        year
                    )
                    etFecha.setText(fecha)
                },
                calendario.get(Calendar.YEAR),
                calendario.get(Calendar.MONTH),
                calendario.get(Calendar.DAY_OF_MONTH)
            )

            dialogo.show()
        }
    }

    // =========================================================
    // GUARDAR VEHÍCULO
    // =========================================================

    private fun guardarVehiculo() {
        val placa = etPlaca.text.toString()
            .trim()
            .uppercase(Locale.getDefault())

        val tipo = spTipoVehiculo.selectedItem?.toString() ?: ""
        val marca = spMarca.selectedItem?.toString() ?: ""
        val modelo = spModelo.selectedItem?.toString() ?: ""
        val anioTexto = spAnio.selectedItem?.toString() ?: ""
        val color = etColor.text.toString().trim()
        val kilometrajeTexto = etKilometraje.text.toString().trim()

        val placaLimpia = placa.replace("-", "")

        if (!placaLimpia.matches(Regex("^[A-Z]{3}[0-9]{2}[A-Z0-9]$"))) {
            etPlaca.error = "Formato inválido (Ej: ABC123 o ABC12D)"
            etPlaca.requestFocus()
            return
        }

        if (vehiculos.any {
                it.optString("placa").equals(placaLimpia, ignoreCase = true)
            }
        ) {
            Toast.makeText(
                this,
                "Ya existe un vehículo con esa placa",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        if (marca.isEmpty()) {
            Toast.makeText(this, "Selecciona la marca", Toast.LENGTH_SHORT).show()
            spMarca.requestFocus()
            return
        }

        val anio = anioTexto.toIntOrNull() ?: Calendar.getInstance().get(Calendar.YEAR)

        if (color.isEmpty()) {
            etColor.error = "Ingresa el color"
            etColor.requestFocus()
            return
        }

        val kilometraje = kilometrajeTexto.toIntOrNull() ?: 0

        val vehiculo = JSONObject().apply {
            put("placa", placaLimpia)
            put("tipo", tipo)
            put("marca", marca)
            put("modelo", modelo)
            put("anio", anio)
            put("color", color)
            put("kilometraje", kilometraje)
        }

        vehiculos.add(vehiculo)
        guardarVehiculos()

        firestoreSync.guardarVehiculo(
            placaLimpia,
            mapOf(
                "placa" to placaLimpia,
                "tipo" to tipo,
                "marca" to marca,
                "modelo" to modelo,
                "anio" to anio,
                "color" to color,
                "kilometraje" to kilometraje
            )
        ) { exito, error ->
            if (!exito) {
                Toast.makeText(
                    this,
                    "Vehículo guardado localmente. Error en nube: " +
                            (error ?: "Error desconocido"),
                    Toast.LENGTH_LONG
                ).show()
            }
        }

        etPlaca.text.clear()
        etColor.text.clear()
        etKilometraje.text.clear()
        
        spTipoVehiculo.setSelection(0)
        spMarca.setSelection(0)
        spAnio.setSelection(0)

        actualizarSelectorVehiculos()
        mostrarVehiculos()
        actualizarPanelResumen()

        Toast.makeText(
            this,
            "Vehículo registrado correctamente",
            Toast.LENGTH_SHORT
        ).show()
    }

    // =========================================================
    // CARGAR Y GUARDAR VEHÍCULOS
    // =========================================================

    private fun cargarVehiculos() {
        vehiculos.clear()
        val contenido = preferencias.getString("vehiculos", "[]")

        try {
            val array = JSONArray(contenido)
            for (i in 0 until array.length()) {
                val vehiculo = array.optJSONObject(i)
                if (vehiculo != null) vehiculos.add(vehiculo)
            }
        } catch (_: Exception) {
        }
    }

    private fun guardarVehiculos() {
        val array = JSONArray()
        vehiculos.forEach { array.put(it) }

        preferencias.edit()
            .putString("vehiculos", array.toString())
            .apply()
    }

    private fun recuperarVehiculosAntiguos() {
        var huboCambios = false

        for (documento in documentos) {
            val placa = documento.optString("placa")
                .trim()
                .uppercase(Locale.getDefault())

            if (placa.isEmpty()) continue

            val existe = vehiculos.any {
                it.optString("placa").equals(placa, ignoreCase = true)
            }

            if (!existe) {
                val vehiculo = JSONObject().apply {
                    put("placa", placa)
                    put("tipo", documento.optString("vehiculo", "Carro"))
                    put("marca", "")
                    put("modelo", "")
                    put("anio", "")
                    put("color", "")
                }

                vehiculos.add(vehiculo)
                huboCambios = true
            }
        }

        if (huboCambios) guardarVehiculos()
    }

    private fun actualizarSelectorVehiculos() {
        val nombres = vehiculos.map {
            val placa = it.optString("placa")
            val marca = it.optString("marca")
            val modelo = it.optString("modelo")

            if (marca.isNotEmpty() && modelo.isNotEmpty()) {
                "$placa · $marca $modelo"
            } else {
                placa
            }
        }

        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            nombres
        )

        adapter.setDropDownViewResource(
            android.R.layout.simple_spinner_dropdown_item
        )

        spVehiculoDocumento.adapter = adapter
    }

    // =========================================================
    // GUARDAR DOCUMENTO
    // =========================================================

    private fun guardarDocumento() {
        if (vehiculos.isEmpty()) {
            Toast.makeText(
                this,
                "Primero registra al menos un vehículo",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        val posicion = spVehiculoDocumento.selectedItemPosition

        if (posicion < 0 || posicion >= vehiculos.size) return

        val vehiculo = vehiculos[posicion]
        val placa = vehiculo.optString("placa")
        val tipo = spDocumento.selectedItem?.toString() ?: ""
        val fecha = etFecha.text.toString().trim()

        if (fecha.isEmpty()) {
            etFecha.error = "Selecciona una fecha"
            etFecha.requestFocus()
            return
        }

        val formato = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        formato.isLenient = false

        try {
            formato.parse(fecha)
        } catch (_: Exception) {
            etFecha.error = "Fecha inválida"
            etFecha.requestFocus()
            return
        }

        val documento = JSONObject().apply {
            put("placa", placa)
            put("vehiculo", vehiculo.optString("tipo"))
            put("tipo", tipo)
            put("fecha", fecha)
        }

        documentos.add(documento)
        guardarDocumentos()

        firestoreSync.guardarDocumento(
            placa,
            tipo,
            fecha,
            mapOf(
                "placa" to placa,
                "vehiculo" to vehiculo.optString("tipo"),
                "tipo" to tipo,
                "fecha" to fecha
            )
        ) { exito, error ->
            if (!exito) {
                Toast.makeText(
                    this,
                    "Documento guardado localmente. Error en nube: " +
                            (error ?: "Error desconocido"),
                    Toast.LENGTH_LONG
                ).show()
            }
        }

        etFecha.text.clear()

        mostrarDocumentos()
        actualizarPanelResumen()
        ejecutarRevisionVencimientos()

        Toast.makeText(
            this,
            "Documento guardado correctamente",
            Toast.LENGTH_SHORT
        ).show()
    }

    // =========================================================
    // CARGAR Y GUARDAR DOCUMENTOS
    // =========================================================

    private fun cargarDocumentos() {
        documentos.clear()
        val contenido = preferencias.getString("documentos", "[]")

        try {
            val array = JSONArray(contenido)
            for (i in 0 until array.length()) {
                val documento = array.optJSONObject(i)
                if (documento != null) documentos.add(documento)
            }
        } catch (_: Exception) {
        }
    }

    private fun guardarDocumentos() {
        val array = JSONArray()
        documentos.forEach { array.put(it) }

        preferencias.edit()
            .putString("documentos", array.toString())
            .apply()
    }

    // =========================================================
    // REVISIÓN DE VENCIMIENTOS
    // =========================================================

    private fun ejecutarRevisionVencimientos() {
        val solicitud = OneTimeWorkRequestBuilder<RecordatorioWorker>()
            .build()

        WorkManager.getInstance(this).enqueue(solicitud)
    }

    // =========================================================
    // ACTUALIZAR PANEL DE RESUMEN
    // =========================================================

    private fun actualizarPanelResumen() {
        tvTotalVehiculos.text = vehiculos.size.toString()
        tvTotalDocumentos.text = documentos.size.toString()

        val formato = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        formato.isLenient = false

        val hoy = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val limite30 = Calendar.getInstance().apply {
            timeInMillis = hoy
            add(Calendar.DAY_OF_YEAR, 30)
        }.timeInMillis

        var proximos = 0
        var vencidos = 0
        val alertas = mutableListOf<Pair<String, String>>()

        for (documento in documentos) {
            val tipo = documento.optString("tipo", "Documento")
            val placa = documento.optString("placa", "")
            val fechaTexto = documento.optString("fecha", "")

            val fecha = try {
                formato.parse(fechaTexto)?.time
            } catch (_: Exception) {
                null
            } ?: continue

            val fechaNormalizada = Calendar.getInstance().apply {
                timeInMillis = fecha
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis

            when {
                fechaNormalizada < hoy -> {
                    vencidos++
                    alertas.add(
                        "VENCIDO" to
                                "$tipo · $placa\nVenció el $fechaTexto"
                    )
                }

                fechaNormalizada <= limite30 -> {
                    proximos++

                    val dias = TimeUnit.MILLISECONDS
                        .toDays(fechaNormalizada - hoy)
                        .toInt()

                    val mensaje = when (dias) {
                        0 -> "Vence hoy"
                        1 -> "Vence mañana"
                        else -> "Vence en $dias días"
                    }

                    alertas.add(
                        "PRÓXIMO" to
                                "$tipo · $placa\n$mensaje ($fechaTexto)"
                    )
                }
            }
        }

        tvProximosVencer.text = proximos.toString()
        tvVencidos.text = vencidos.toString()
        mostrarAlertas(alertas)
    }

    // =========================================================
    // MOSTRAR ALERTAS
    // =========================================================

    private fun mostrarAlertas(alertas: List<Pair<String, String>>) {
        contenedorAlertas.removeAllViews()

        if (alertas.isEmpty()) {
            val mensaje = TextView(this).apply {
                text = "✓ Todo en orden. No hay documentos vencidos ni próximos a vencer."
                textSize = 14f
                setTextColor(Color.rgb(22, 101, 52))
                setBackgroundColor(Color.rgb(240, 253, 244))
                setPadding(20, 22, 20, 22)
                gravity = Gravity.CENTER
            }

            contenedorAlertas.addView(mensaje)
            return
        }

        alertas.forEach { alerta ->
            val estado = alerta.first
            val detalle = alerta.second

            val tarjeta = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(18, 16, 18, 16)
                setBackgroundColor(
                    if (estado == "VENCIDO") {
                        Color.rgb(254, 242, 242)
                    } else {
                        Color.rgb(255, 251, 235)
                    }
                )
            }

            val titulo = TextView(this).apply {
                text = if (estado == "VENCIDO") {
                    "⚠ Documento vencido"
                } else {
                    "⏳ Próximo vencimiento"
                }

                textSize = 15f
                setTextColor(
                    if (estado == "VENCIDO") {
                        Color.rgb(185, 28, 28)
                    } else {
                        Color.rgb(146, 64, 14)
                    }
                )
                setTypeface(null, android.graphics.Typeface.BOLD)
            }

            val descripcion = TextView(this).apply {
                text = detalle
                textSize = 14f
                setTextColor(Color.rgb(51, 65, 85))
                setPadding(0, 6, 0, 0)
            }

            tarjeta.addView(titulo)
            tarjeta.addView(descripcion)

            val parametros = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 12
            }

            contenedorAlertas.addView(tarjeta, parametros)
        }
    }

    // =========================================================
    // MOSTRAR VEHÍCULOS
    // =========================================================

    private fun mostrarVehiculos() {
        contenedorVehiculos.removeAllViews()
        tvVehiculosCantidad.text = "Mis vehículos (${vehiculos.size})"

        for (vehiculo in vehiculos) {
            val placa = vehiculo.optString("placa")
            val tipo = vehiculo.optString("tipo")
            val marca = vehiculo.optString("marca")
            val modelo = vehiculo.optString("modelo")
            val anio = vehiculo.optString("anio")
            val color = vehiculo.optString("color")

            val cantidadDocumentos = documentos.count {
                it.optString("placa").equals(placa, ignoreCase = true)
            }

            val tarjeta = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(Color.WHITE)
                setPadding(18, 18, 18, 18)
            }

            val titulo = TextView(this).apply {
                text = placa
                textSize = 21f
                setTextColor(Color.rgb(23, 54, 83))
                setTypeface(null, android.graphics.Typeface.BOLD)
            }

            val descripcion = TextView(this).apply {
                text = buildString {
                    if (tipo.isNotEmpty()) append("Tipo: $tipo\n")

                    if (marca.isNotEmpty() || modelo.isNotEmpty()) {
                        append("Vehículo: $marca $modelo\n")
                    }

                    if (anio.isNotEmpty()) append("Año: $anio\n")
                    if (color.isNotEmpty()) append("Color: $color\n")

                    append("Documentos: $cantidadDocumentos")
                }

                textSize = 14f
                setTextColor(Color.rgb(71, 85, 105))
                setPadding(0, 8, 0, 8)
            }

            val eliminar = Button(this).apply {
                text = "Eliminar vehículo"
                setAllCaps(false)
                setTextColor(Color.rgb(185, 28, 28))
                setOnClickListener {
                    confirmarEliminarVehiculo(placa)
                }
            }

            tarjeta.addView(titulo)
            tarjeta.addView(descripcion)
            tarjeta.addView(eliminar)

            val parametros = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 12
            }

            contenedorVehiculos.addView(tarjeta, parametros)
        }
    }

    // =========================================================
    // ELIMINAR VEHÍCULO
    // =========================================================

    private fun confirmarEliminarVehiculo(placa: String) {
        AlertDialog.Builder(this)
            .setTitle("Eliminar vehículo")
            .setMessage(
                "¿Deseas eliminar el vehículo $placa?\n\n" +
                        "También se eliminarán los documentos asociados."
            )
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Eliminar") { _: DialogInterface, _: Int ->
                vehiculos.removeAll {
                    it.optString("placa").equals(placa, ignoreCase = true)
                }

                documentos.removeAll {
                    it.optString("placa").equals(placa, ignoreCase = true)
                }

                guardarVehiculos()
                guardarDocumentos()
                firestoreSync.eliminarVehiculo(placa) { exito, error ->
                    if (!exito) {
                        Toast.makeText(
                            this,
                            "Eliminado del teléfono. Error en nube: " +
                                    (error ?: "Error desconocido"),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
                actualizarSelectorVehiculos()
                mostrarVehiculos()
                mostrarDocumentos()
                actualizarPanelResumen()

                Toast.makeText(
                    this,
                    "Vehículo eliminado",
                    Toast.LENGTH_SHORT
                ).show()
            }
            .show()
    }

    // =========================================================
    // MOSTRAR DOCUMENTOS
    // =========================================================

    private fun mostrarDocumentos() {
        contenedorDocumentos.removeAllViews()
        tvCantidad.text = "Mis documentos (${documentos.size})"

        val formato = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        formato.isLenient = false

        val hoy = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        for (indice in documentos.indices) {
            val documento = documentos[indice]
            val tipo = documento.optString("tipo")
            val placa = documento.optString("placa")
            val fechaTexto = documento.optString("fecha")

            var diasRestantes: Int? = null

            try {
                val fecha = formato.parse(fechaTexto)?.time

                if (fecha != null) {
                    val fechaNormalizada = Calendar.getInstance().apply {
                        timeInMillis = fecha
                        set(Calendar.HOUR_OF_DAY, 0)
                        set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }.timeInMillis

                    diasRestantes = TimeUnit.MILLISECONDS
                        .toDays(fechaNormalizada - hoy)
                        .toInt()
                }
            } catch (_: Exception) {
            }

            val estado = when {
                diasRestantes == null -> "Fecha inválida"
                diasRestantes < 0 -> "Vencido"
                diasRestantes == 0 -> "Vence hoy"
                diasRestantes == 1 -> "Vence mañana"
                diasRestantes <= 30 -> "Vence en $diasRestantes días"
                else -> "Vigente"
            }

            val tarjeta = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(18, 18, 18, 18)

                setBackgroundColor(
                    when {
                        estado == "Vencido" ->
                            Color.rgb(254, 242, 242)

                        diasRestantes != null && diasRestantes in 0..30 ->
                            Color.rgb(255, 251, 235)

                        else -> Color.WHITE
                    }
                )
            }

            val titulo = TextView(this).apply {
                text = "$tipo · $placa"
                textSize = 18f
                setTextColor(Color.rgb(23, 54, 83))
                setTypeface(null, android.graphics.Typeface.BOLD)
            }

            val fecha = TextView(this).apply {
                text = "Vencimiento: $fechaTexto"
                textSize = 14f
                setTextColor(Color.rgb(71, 85, 105))
                setPadding(0, 6, 0, 2)
            }

            val estadoTexto = TextView(this).apply {
                text = estado
                textSize = 14f
                setTypeface(null, android.graphics.Typeface.BOLD)

                setTextColor(
                    when {
                        estado == "Vencido" ->
                            Color.rgb(185, 28, 28)

                        diasRestantes != null && diasRestantes in 0..30 ->
                            Color.rgb(180, 83, 9)

                        else -> Color.rgb(22, 101, 52)
                    }
                )
            }

            val eliminar = Button(this).apply {
                text = "Eliminar documento"
                setAllCaps(false)
                setTextColor(Color.rgb(185, 28, 28))
                setOnClickListener {
                    confirmarEliminarDocumento(indice)
                }
            }

            tarjeta.addView(titulo)
            tarjeta.addView(fecha)
            tarjeta.addView(estadoTexto)
            tarjeta.addView(eliminar)

            val parametros = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 12
            }

            contenedorDocumentos.addView(tarjeta, parametros)
        }
    }

    // =========================================================
    // ELIMINAR DOCUMENTO
    // =========================================================

    private fun confirmarEliminarDocumento(indice: Int) {
        if (indice < 0 || indice >= documentos.size) return

        val documento = documentos[indice]
        val tipo = documento.optString("tipo")
        val placa = documento.optString("placa")

        AlertDialog.Builder(this)
            .setTitle("Eliminar documento")
            .setMessage("¿Deseas eliminar $tipo del vehículo $placa?")
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Eliminar") { _: DialogInterface, _: Int ->
                documentos.removeAt(indice)
                guardarDocumentos()
                firestoreSync.eliminarDocumento(
                    placa,
                    tipo,
                    documento.optString("fecha")
                ) { exito, error ->
                    if (!exito) {
                        Toast.makeText(
                            this,
                            "Eliminado del teléfono. Error en nube: " +
                                    (error ?: "Error desconocido"),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }

                mostrarDocumentos()
                mostrarVehiculos()
                actualizarPanelResumen()

                Toast.makeText(
                    this,
                    "Documento eliminado",
                    Toast.LENGTH_SHORT
                ).show()
            }
            .show()
    }
}