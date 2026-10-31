package com.example.controlvial

import android.content.Context
import android.content.SharedPreferences
import android.widget.Toast
import com.google.firebase.auth.FirebaseAuth
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Una alerta lista para mostrarse en pantalla. */
data class AlertaItem(
    val estado: EstadoActividad,
    val placa: String,
    val titulo: String,
    val detalle: String,
    /** Identificador estable de la alerta del plan (vacío para documentos). */
    val clave: String = "",
    /** Id del registro o de la actividad del plan que originó la alerta. */
    val idOrigen: String = ""
)

/** Resultado de evaluar un registro (documento o servicio). */
data class EstadoRegistro(
    val general: EstadoActividad,
    val porFecha: EstadoActividad,
    val porKm: EstadoActividad,
    val diasRestantes: Int?,
    val kmActual: Int
)

/** Totales de gasto agrupados (RF-36). */
data class ResumenGastos(
    val total: Double,
    val porVehiculo: List<Pair<String, Double>>,
    val porCategoria: List<Pair<String, Double>>,
    val porMes: List<Pair<String, Double>>
)

/**
 * Centraliza el acceso a los datos: SharedPreferences "ControlVial" (llaves "vehiculos",
 * "documentos" y "lecturas") y la sincronización con Firestore mediante FirestoreSync,
 * que no se modifica.
 */
class RepositorioMiCarro(context: Context) {

    private val contexto = context.applicationContext
    private val preferencias = abrirPreferencias(contexto)
    private val nube by lazy { FirestoreSync() }

    companion object {
        private const val NOMBRE_BASE = "ControlVial"

        /**
         * Cada usuario tiene sus propios datos locales ("ControlVial_<uid>"), así que otra persona
         * que inicie sesión en el mismo teléfono no ve los datos de la anterior (RNF-09).
         * Los datos de versiones anteriores, que estaban en un solo lugar, pasan al primer
         * usuario que inicie sesión.
         */
        private fun abrirPreferencias(contexto: Context): SharedPreferences {
            val antiguas = contexto.getSharedPreferences(NOMBRE_BASE, Context.MODE_PRIVATE)
            val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return antiguas

            val propias = contexto.getSharedPreferences(NOMBRE_BASE + "_" + uid, Context.MODE_PRIVATE)
            if (antiguas.all.isNotEmpty() && propias.all.isEmpty()) {
                val editor = propias.edit()
                for ((llave, valor) in antiguas.all) {
                    when (valor) {
                        is String -> editor.putString(llave, valor)
                        is Boolean -> editor.putBoolean(llave, valor)
                        is Int -> editor.putInt(llave, valor)
                        is Long -> editor.putLong(llave, valor)
                        is Float -> editor.putFloat(llave, valor)
                    }
                }
                editor.apply()
                antiguas.edit().clear().apply()
            }
            return propias
        }

        private const val LLAVE_VEHICULOS = "vehiculos"
        private const val LLAVE_DOCUMENTOS = "documentos"
        private const val LLAVE_LECTURAS = "lecturas"
        private const val LLAVE_PLAN = "plan"
        private const val LLAVE_REPUESTOS = "repuestos"
        private const val LLAVE_POSPUESTAS = "pospuestas"
        private const val LLAVE_MONEDA = "moneda"

        /** Listas que se guardan en la copia de respaldo. */
        private val LLAVES_RESPALDO = listOf(
            "vehiculos", "documentos", "lecturas", "plan", "categorias", "repuestos"
        )
        private const val LLAVE_CATEGORIAS = "categorias"
    }

    // ---------------------------------------------------------------
    // Lectura y escritura local
    // ---------------------------------------------------------------

    /** Preferencias del usuario actual (las usa también el servicio de avisos). */
    fun preferenciasActuales(): SharedPreferences = preferencias

    private fun leerLista(llave: String): MutableList<JSONObject> {
        val lista = mutableListOf<JSONObject>()
        try {
            val arreglo = JSONArray(preferencias.getString(llave, "[]"))
            for (i in 0 until arreglo.length()) {
                val elemento = arreglo.optJSONObject(i)
                if (elemento != null) lista.add(elemento)
            }
        } catch (e: Exception) {
            Bitacora.error("Repositorio", "No se pudo leer la lista $llave", e)
        }
        return lista
    }

    private fun escribirLista(llave: String, lista: List<JSONObject>) {
        val arreglo = JSONArray()
        lista.forEach { arreglo.put(it) }
        preferencias.edit().putString(llave, arreglo.toString()).apply()
    }

    fun esArchivado(vehiculo: JSONObject): Boolean = vehiculo.optBoolean("archivado", false)

    /**
     * Garantiza que exista exactamente un vehículo principal entre los ACTIVOS (RF-04) y que
     * ningún vehículo archivado lo sea. Devuelve true si tuvo que corregir algo.
     */
    private fun normalizarPrincipal(lista: List<JSONObject>): Boolean {
        var huboCambios = false

        lista.filter { esArchivado(it) && it.optBoolean("principal", false) }.forEach {
            it.put("principal", false)
            huboCambios = true
        }

        val activos = lista.filter { !esArchivado(it) }
        if (activos.isNotEmpty()) {
            val principales = activos.filter { it.optBoolean("principal", false) }
            if (principales.size != 1) {
                val elegido = principales.firstOrNull() ?: activos[0]
                activos.forEach { it.put("principal", it === elegido) }
                huboCambios = true
            }
        }
        return huboCambios
    }

    /** Devuelve todos los vehículos (activos y archivados). */
    fun obtenerVehiculos(): MutableList<JSONObject> {
        val lista = leerLista(LLAVE_VEHICULOS)
        if (normalizarPrincipal(lista)) escribirLista(LLAVE_VEHICULOS, lista)
        return lista
    }

    private fun guardarVehiculos(lista: List<JSONObject>) =
        escribirLista(LLAVE_VEHICULOS, lista)

    /** Devuelve los registros; a los antiguos sin identificador se les asigna uno. */
    fun obtenerDocumentos(): MutableList<JSONObject> {
        val lista = leerLista(LLAVE_DOCUMENTOS)
        var huboCambios = false
        lista.filter { !it.has("id") }.forEach {
            it.put("id", java.util.UUID.randomUUID().toString())
            huboCambios = true
        }
        if (huboCambios) escribirLista(LLAVE_DOCUMENTOS, lista)
        return lista
    }

    private fun guardarDocumentos(lista: List<JSONObject>) =
        escribirLista(LLAVE_DOCUMENTOS, lista)

    fun vehiculoPrincipal(): JSONObject? =
        obtenerVehiculos().firstOrNull { it.optBoolean("principal", false) }

    fun buscarVehiculo(placa: String): JSONObject? =
        obtenerVehiculos().firstOrNull { it.optString("placa").equals(placa, ignoreCase = true) }

    fun existePlaca(placa: String): Boolean = buscarVehiculo(placa) != null

    // ---------------------------------------------------------------
    // Sincronización con Firestore
    // ---------------------------------------------------------------

    private fun avisarErrorNube(mensajeBase: Int, error: String?) {
        Bitacora.error("Nube", "Falló la sincronización con Firestore: " + (error ?: "sin detalle"))
        Toast.makeText(
            contexto,
            contexto.getString(mensajeBase, error ?: contexto.getString(R.string.error_desconocido)),
            Toast.LENGTH_LONG
        ).show()
    }

    private fun valorSeguro(valor: Any?): Any =
        if (valor == null || valor == JSONObject.NULL) "" else valor

    /** Mapa COMPLETO del vehículo (evita que set() en Firestore borre campos). */
    fun mapaVehiculo(vehiculo: JSONObject): Map<String, Any> = mapOf(
        "placa" to vehiculo.optString("placa"),
        "tipo" to vehiculo.optString("tipo"),
        "marca" to vehiculo.optString("marca"),
        "modelo" to vehiculo.optString("modelo"),
        "anio" to valorSeguro(vehiculo.opt("anio")),
        "color" to vehiculo.optString("color"),
        "kilometraje" to vehiculo.optInt("kilometraje", 0),
        "principal" to vehiculo.optBoolean("principal", false),
        "archivado" to esArchivado(vehiculo),
        "vin" to vehiculo.optString("vin"),
        "combustible" to vehiculo.optString("combustible"),
        "cilindraje" to vehiculo.optInt("cilindraje", 0)
    )

    /** Marca o desmarca un vehículo como "pendiente de sincronizar" sin tocar nada más. */
    private fun marcarVehiculoPendiente(placa: String, pendiente: Boolean) {
        val lista = leerLista(LLAVE_VEHICULOS)
        val vehiculo = lista.firstOrNull {
            it.optString("placa").equals(placa, ignoreCase = true)
        } ?: return
        if (vehiculo.optBoolean("pendiente_sync", false) == pendiente) return
        vehiculo.put("pendiente_sync", pendiente)
        escribirLista(LLAVE_VEHICULOS, lista)
    }

    private fun marcarDocumentoPendiente(id: String, pendiente: Boolean) {
        val lista = leerLista(LLAVE_DOCUMENTOS)
        val documento = lista.firstOrNull { it.optString("id") == id } ?: return
        if (documento.optBoolean("pendiente_sync", false) == pendiente) return
        documento.put("pendiente_sync", pendiente)
        escribirLista(LLAVE_DOCUMENTOS, lista)
    }

    /**
     * El guardado en Firestore solo se confirma cuando el servidor lo recibe. Mientras tanto
     * (por ejemplo, sin internet) el elemento queda marcado como pendiente.
     */
    private fun sincronizarVehiculo(vehiculo: JSONObject) {
        val placa = vehiculo.optString("placa")
        marcarVehiculoPendiente(placa, true)
        nube.guardarVehiculo(placa, mapaVehiculo(vehiculo)) { exito, error ->
            if (exito) {
                marcarVehiculoPendiente(placa, false)
            } else {
                avisarErrorNube(R.string.error_nube_guardar_vehiculo, error)
            }
        }
    }

    /** Cantidad de vehículos y registros que aún no se han confirmado en la nube. */
    fun cantidadPendientes(): Int =
        leerLista(LLAVE_VEHICULOS).count { it.optBoolean("pendiente_sync", false) } +
                leerLista(LLAVE_DOCUMENTOS).count { it.optBoolean("pendiente_sync", false) }

    /** Al abrir la app se reenvía lo que quedó pendiente (guardar de nuevo es seguro). */
    fun reintentarPendientes() {
        leerLista(LLAVE_VEHICULOS)
            .filter { it.optBoolean("pendiente_sync", false) }
            .forEach { sincronizarVehiculo(it) }
        leerLista(LLAVE_DOCUMENTOS)
            .filter { it.optBoolean("pendiente_sync", false) }
            .forEach { sincronizarDocumento(it) }
    }

    // ---------------------------------------------------------------
    // Vehículos
    // ---------------------------------------------------------------

    fun formatearFecha(fecha: Date): String =
        SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(fecha)

    /** El primer vehículo activo registrado queda como principal. */
    fun agregarVehiculo(vehiculo: JSONObject) {
        val lista = obtenerVehiculos()
        vehiculo.put("archivado", false)
        vehiculo.put("principal", lista.none { !esArchivado(it) })
        lista.add(vehiculo)
        guardarVehiculos(lista)
        sincronizarVehiculo(vehiculo)

        val km = vehiculo.optInt("kilometraje", 0)
        if (km > 0) anotarLectura(vehiculo.optString("placa"), km, formatearFecha(Date()))
    }

    /** Solo un vehículo activo puede ser el principal a la vez. */
    fun marcarPrincipal(placa: String) {
        val lista = obtenerVehiculos()
        val objetivo = lista.firstOrNull {
            it.optString("placa").equals(placa, ignoreCase = true)
        } ?: return
        if (esArchivado(objetivo)) return

        val cambiados = mutableListOf<JSONObject>()
        for (vehiculo in lista) {
            if (esArchivado(vehiculo)) continue
            val debeSerPrincipal = vehiculo === objetivo
            if (vehiculo.optBoolean("principal", false) != debeSerPrincipal) {
                vehiculo.put("principal", debeSerPrincipal)
                cambiados.add(vehiculo)
            }
        }
        if (cambiados.isEmpty()) return

        guardarVehiculos(lista)
        cambiados.forEach { sincronizarVehiculo(it) }
    }

    /**
     * RF-02 / RF-03: corrige los datos del vehículo (la placa y el tipo no se editan).
     * [foto] es la dirección (URI) de la imagen elegida; vacío = sin foto.
     */
    fun editarVehiculo(
        placa: String,
        marca: String,
        modelo: String,
        anio: Int,
        color: String,
        vin: String,
        combustible: String,
        cilindraje: Int,
        foto: String
    ) {
        val lista = obtenerVehiculos()
        val vehiculo = lista.firstOrNull {
            it.optString("placa").equals(placa, ignoreCase = true)
        } ?: return

        vehiculo.put("marca", marca)
        vehiculo.put("modelo", modelo)
        vehiculo.put("anio", anio)
        vehiculo.put("color", color)
        vehiculo.put("vin", vin)
        vehiculo.put("combustible", combustible)
        vehiculo.put("cilindraje", cilindraje)
        vehiculo.put("foto", foto)

        guardarVehiculos(lista)
        sincronizarVehiculo(vehiculo)
    }

    /**
     * RF-03 / RN-07: un vehículo archivado conserva su historial y sus cálculos, pero deja de
     * ofrecerse en los formularios y no puede ser el principal.
     */
    fun archivarVehiculo(placa: String) {
        val lista = obtenerVehiculos()
        val vehiculo = lista.firstOrNull {
            it.optString("placa").equals(placa, ignoreCase = true)
        } ?: return
        if (esArchivado(vehiculo)) return

        val principalAntes = lista.firstOrNull { it.optBoolean("principal", false) }
        vehiculo.put("archivado", true)
        normalizarPrincipal(lista)
        guardarVehiculos(lista)
        sincronizarVehiculo(vehiculo)

        val principalDespues = lista.firstOrNull { it.optBoolean("principal", false) }
        if (principalDespues != null &&
            principalDespues !== vehiculo &&
            principalDespues !== principalAntes
        ) {
            sincronizarVehiculo(principalDespues)
        }
    }

    fun reactivarVehiculo(placa: String) {
        val lista = obtenerVehiculos()
        val vehiculo = lista.firstOrNull {
            it.optString("placa").equals(placa, ignoreCase = true)
        } ?: return
        if (!esArchivado(vehiculo)) return

        vehiculo.put("archivado", false)
        normalizarPrincipal(lista) // si no había vehículos activos, este pasa a ser el principal
        guardarVehiculos(lista)
        sincronizarVehiculo(vehiculo)
    }

    /** Elimina el vehículo y sus registros; si era el principal, otro activo lo reemplaza. */
    fun eliminarVehiculo(placa: String) {
        val lista = obtenerVehiculos()
        val eliminado = lista.firstOrNull {
            it.optString("placa").equals(placa, ignoreCase = true)
        } ?: return

        val eraPrincipal = eliminado.optBoolean("principal", false)
        lista.remove(eliminado)

        val documentos = obtenerDocumentos()
        documentos.removeAll { it.optString("placa").equals(placa, ignoreCase = true) }

        val lecturas = leerLista(LLAVE_LECTURAS)
        lecturas.removeAll { it.optString("placa").equals(placa, ignoreCase = true) }

        val plan = obtenerPlan()
        plan.removeAll { it.optString("placa").equals(placa, ignoreCase = true) }

        val repuestos = obtenerRepuestos()
        repuestos.removeAll { it.optString("placa").equals(placa, ignoreCase = true) }
        guardarRepuestos(repuestos)

        normalizarPrincipal(lista)

        guardarVehiculos(lista)
        guardarDocumentos(documentos)
        escribirLista(LLAVE_LECTURAS, lecturas)
        guardarPlan(plan)

        nube.eliminarVehiculo(eliminado.optString("placa")) { exito, error ->
            if (!exito) avisarErrorNube(R.string.error_nube_eliminar, error)
        }

        if (eraPrincipal) {
            lista.firstOrNull { it.optBoolean("principal", false) }?.let { sincronizarVehiculo(it) }
        }
    }

    /** Crea vehículos "básicos" para documentos antiguos cuya placa no esté registrada. */
    fun recuperarVehiculosAntiguos() {
        val lista = obtenerVehiculos()
        var huboCambios = false

        for (documento in obtenerDocumentos()) {
            val placa = documento.optString("placa").trim().uppercase(Locale.getDefault())
            if (placa.isEmpty()) continue

            val existe = lista.any { it.optString("placa").equals(placa, ignoreCase = true) }
            if (!existe) {
                lista.add(JSONObject().apply {
                    put("placa", placa)
                    put("tipo", documento.optString("vehiculo", "Carro"))
                    put("marca", "")
                    put("modelo", "")
                    put("anio", "")
                    put("color", "")
                    put("kilometraje", 0)
                    put("principal", false)
                    put("archivado", false)
                })
                huboCambios = true
            }
        }

        if (huboCambios) {
            normalizarPrincipal(lista)
            guardarVehiculos(lista)
        }
    }

    // ---------------------------------------------------------------
    // Kilometraje (RF-06 a RF-09)
    // ---------------------------------------------------------------

    private fun anotarLectura(placa: String, km: Int, fecha: String) {
        val lecturas = leerLista(LLAVE_LECTURAS)
        lecturas.add(JSONObject().apply {
            put("placa", placa)
            put("fecha", fecha)
            put("km", km)
        })
        escribirLista(LLAVE_LECTURAS, lecturas)
    }

    /** Historial de lecturas del odómetro, de la más reciente a la más antigua. */
    fun obtenerLecturas(placa: String): List<JSONObject> {
        return leerLista(LLAVE_LECTURAS)
            .filter { it.optString("placa").equals(placa, ignoreCase = true) }
            .withIndex()
            .sortedWith(
                compareByDescending<IndexedValue<JSONObject>> {
                    parsearFecha(it.value.optString("fecha"))?.time ?: 0L
                }.thenByDescending { it.index }
            )
            .map { it.value }
    }

    /**
     * Guarda una lectura del odómetro y actualiza el kilometraje del vehículo.
     * La confirmación cuando la lectura es menor (RF-08) la pide la pantalla antes de llamar aquí.
     */
    fun registrarLectura(placa: String, km: Int, fecha: String) {
        anotarLectura(placa, km, fecha)

        val vehiculos = obtenerVehiculos()
        val vehiculo = vehiculos.firstOrNull {
            it.optString("placa").equals(placa, ignoreCase = true)
        } ?: return

        vehiculo.put("kilometraje", km)
        guardarVehiculos(vehiculos)
        sincronizarVehiculo(vehiculo)
    }

    // ---------------------------------------------------------------
    // Documentos y servicios
    // ---------------------------------------------------------------

    private fun sincronizarDocumento(documento: JSONObject) {
        val id = documento.optString("id")
        marcarDocumentoPendiente(id, true)
        nube.guardarDocumento(
            documento.optString("placa"),
            documento.optString("tipo"),
            documento.optString("fecha"),
            mapOf(
                "id" to documento.optString("id"),
                "placa" to documento.optString("placa"),
                "vehiculo" to documento.optString("vehiculo"),
                "tipo" to documento.optString("tipo"),
                "categoria" to documento.optString("categoria"),
                "clase" to documento.optString("clase"),
                "descripcion" to documento.optString("descripcion"),
                "fecha" to documento.optString("fecha"),
                "fecha_registro" to documento.optString("fecha_registro"),
                "kilometraje" to documento.optInt("kilometraje", 0),
                "proximo_kilometraje" to documento.optInt("proximo_kilometraje", 0),
                "mano_obra" to documento.optDouble("mano_obra", 0.0),
                "costo_repuestos" to documento.optDouble("costo_repuestos", 0.0),
                "otros_costos" to documento.optDouble("otros_costos", 0.0),
                "costo" to documento.optDouble("costo", 0.0),
                "taller_repuestos" to documento.optString("taller_repuestos")
            )
        ) { exito, error ->
            if (exito) {
                marcarDocumentoPendiente(id, false)
            } else {
                avisarErrorNube(R.string.error_nube_guardar_registro, error)
            }
        }
    }

    fun agregarDocumento(documento: JSONObject) {
        if (!documento.has("id")) documento.put("id", java.util.UUID.randomUUID().toString())
        if (!documento.has("fecha_registro")) {
            documento.put("fecha_registro", formatearFecha(Date()))
        }

        val documentos = obtenerDocumentos()
        documentos.add(documento)
        guardarDocumentos(documentos)

        val placa = documento.optString("placa")
        val km = documento.optInt("kilometraje", 0)

        // Si el kilometraje del servicio es mayor, se actualiza el del vehículo (mapa completo).
        val vehiculos = obtenerVehiculos()
        val vehiculo = vehiculos.firstOrNull {
            it.optString("placa").equals(placa, ignoreCase = true)
        }
        if (vehiculo != null && km > vehiculo.optInt("kilometraje", 0)) {
            vehiculo.put("kilometraje", km)
            guardarVehiculos(vehiculos)
            sincronizarVehiculo(vehiculo)
            anotarLectura(placa, km, formatearFecha(Date()))
        }

        sincronizarDocumento(documento)
    }

    fun buscarDocumento(id: String): JSONObject? =
        obtenerDocumentos().firstOrNull { it.optString("id") == id }

    /**
     * RF-22: guarda los cambios de un registro. Si cambió el tipo o la fecha, cambia también
     * su identificador en Firestore, así que se borra el anterior y se guarda el nuevo.
     */
    fun actualizarDocumento(documento: JSONObject, tipoAnterior: String, fechaAnterior: String) {
        val documentos = obtenerDocumentos()
        val indice = documentos.indexOfFirst { it.optString("id") == documento.optString("id") }
        if (indice < 0) return
        documentos[indice] = documento
        guardarDocumentos(documentos)

        // Un kilometraje mayor actualiza el del vehículo, igual que al crear el registro.
        val placa = documento.optString("placa")
        val km = documento.optInt("kilometraje", 0)
        val vehiculos = obtenerVehiculos()
        val vehiculo = vehiculos.firstOrNull { it.optString("placa").equals(placa, ignoreCase = true) }
        if (vehiculo != null && km > vehiculo.optInt("kilometraje", 0)) {
            vehiculo.put("kilometraje", km)
            guardarVehiculos(vehiculos)
            sincronizarVehiculo(vehiculo)
            anotarLectura(placa, km, formatearFecha(Date()))
        }

        if (tipoAnterior != documento.optString("tipo") || fechaAnterior != documento.optString("fecha")) {
            nube.eliminarDocumento(placa, tipoAnterior, fechaAnterior) { exito, error ->
                if (!exito) avisarErrorNube(R.string.error_nube_eliminar, error)
            }
        }
        sincronizarDocumento(documento)
    }

    fun eliminarDocumento(indice: Int) {
        val documentos = obtenerDocumentos()
        if (indice < 0 || indice >= documentos.size) return

        val documento = documentos.removeAt(indice)
        guardarDocumentos(documentos)

        val repuestos = obtenerRepuestos()
        repuestos.removeAll { it.optString("registro_id") == documento.optString("id") }
        guardarRepuestos(repuestos)

        nube.eliminarDocumento(
            documento.optString("placa"),
            documento.optString("tipo"),
            documento.optString("fecha")
        ) { exito, error ->
            if (!exito) avisarErrorNube(R.string.error_nube_eliminar, error)
        }
    }

    // ---------------------------------------------------------------
    // Plan de mantenimiento (RF-10 a RF-16, RF-21)
    // ---------------------------------------------------------------

    fun obtenerPlan(): MutableList<JSONObject> = leerLista(LLAVE_PLAN)

    private fun guardarPlan(lista: List<JSONObject>) = escribirLista(LLAVE_PLAN, lista)

    fun agregarActividad(actividad: JSONObject) {
        if (!actividad.has("id")) actividad.put("id", java.util.UUID.randomUUID().toString())
        val plan = obtenerPlan()
        plan.add(actividad)
        guardarPlan(plan)
    }

    /** Reemplaza la actividad que tenga el mismo "id". */
    fun actualizarActividad(actividad: JSONObject) {
        val plan = obtenerPlan()
        val indice = plan.indexOfFirst { it.optString("id") == actividad.optString("id") }
        if (indice < 0) return
        plan[indice] = actividad
        guardarPlan(plan)
    }

    fun cambiarPausa(id: String, pausada: Boolean) {
        val plan = obtenerPlan()
        val actividad = plan.firstOrNull { it.optString("id") == id } ?: return
        actividad.put("pausada", pausada)
        guardarPlan(plan)
    }

    fun actividadTieneHistorial(id: String): Boolean =
        obtenerDocumentos().any { it.optString("actividad_id") == id }

    /** RF-15: solo se puede eliminar una actividad sin historial asociado. */
    fun eliminarActividad(id: String): Boolean {
        if (actividadTieneHistorial(id)) return false
        val plan = obtenerPlan()
        plan.removeAll { it.optString("id") == id }
        guardarPlan(plan)
        return true
    }

    /** RF-14: estado de una actividad según su próxima fecha y su próximo kilometraje. */
    fun estadoActividadPlan(
        actividad: JSONObject,
        vehiculos: List<JSONObject>,
        hoy: Date = Date()
    ): EstadoRegistro {
        val fecha = parsearFecha(actividad.optString("proxima_fecha"))
        val dias = fecha?.let { ReglasMantenimiento.diasHasta(it, hoy) }

        val estadoFecha = ReglasMantenimiento.estadoPorFecha(
            fecha,
            hoy,
            actividad.optInt("anticipacion_dias", ReglasMantenimiento.DIAS_ANTICIPACION)
        )

        val kmActual = vehiculos.firstOrNull {
            it.optString("placa").equals(actividad.optString("placa"), ignoreCase = true)
        }?.optInt("kilometraje", 0) ?: 0

        val estadoKm = ReglasMantenimiento.estadoPorKilometraje(
            kmActual,
            actividad.optInt("proximo_km", 0),
            actividad.optInt("anticipacion_km", ReglasMantenimiento.KM_ANTICIPACION)
        )

        return EstadoRegistro(
            general = ReglasMantenimiento.estadoGeneral(estadoFecha, estadoKm),
            porFecha = estadoFecha,
            porKm = estadoKm,
            diasRestantes = dias,
            kmActual = kmActual
        )
    }

    /**
     * Registra un servicio realizado de una actividad del plan y calcula el siguiente
     * vencimiento desde la fecha y el kilometraje REALES (RN-03).
     */
    fun registrarServicioPlan(
        actividad: JSONObject,
        fechaReal: String,
        kmReal: Int,
        costo: Double,
        taller: String
    ) {
        val placa = actividad.optString("placa")
        val vehiculo = buscarVehiculo(placa)

        agregarDocumento(JSONObject().apply {
            put("placa", placa)
            put("vehiculo", vehiculo?.optString("tipo") ?: "")
            put("tipo", actividad.optString("nombre"))
            put("fecha", fechaReal)
            put("kilometraje", kmReal)
            put("proximo_kilometraje", 0) // el próximo vencimiento lo controla el plan
            put("costo", costo)
            put("taller_repuestos", taller)
            put("actividad_id", actividad.optString("id"))
            put("categoria", actividad.optString("categoria"))
            put("clase", "Preventivo")
        })

        val fecha = parsearFecha(fechaReal)
        val siguienteFecha = if (fecha != null) {
            ReglasMantenimiento.siguienteFecha(fecha, actividad.optInt("intervalo_dias", 0))
        } else {
            null
        }

        actividad.put("proxima_fecha", if (siguienteFecha != null) formatearFecha(siguienteFecha) else "")
        actividad.put(
            "proximo_km",
            ReglasMantenimiento.siguienteKm(kmReal, actividad.optInt("intervalo_km", 0))
        )
        actividad.put("ultima_fecha", fechaReal)
        actividad.put("ultimo_km", kmReal)
        actualizarActividad(actividad)
    }

    // ---------------------------------------------------------------
    // Categorías editables (RF-12)
    // ---------------------------------------------------------------

    private fun guardarCategorias(lista: List<String>) {
        val arreglo = JSONArray()
        lista.forEach { arreglo.put(it) }
        preferencias.edit().putString(LLAVE_CATEGORIAS, arreglo.toString()).apply()
    }

    fun obtenerCategorias(): MutableList<String> {
        val guardado = preferencias.getString(LLAVE_CATEGORIAS, null)
        if (guardado != null) {
            try {
                val arreglo = JSONArray(guardado)
                val lista = mutableListOf<String>()
                for (i in 0 until arreglo.length()) lista.add(arreglo.getString(i))
                return lista
            } catch (_: Exception) {
            }
        }
        val iniciales = contexto.resources.getStringArray(R.array.categorias_iniciales).toMutableList()
        guardarCategorias(iniciales)
        return iniciales
    }

    fun agregarCategoria(nombre: String): Boolean {
        val limpio = nombre.trim()
        val lista = obtenerCategorias()
        if (limpio.isEmpty() || lista.any { it.equals(limpio, ignoreCase = true) }) return false
        lista.add(limpio)
        guardarCategorias(lista)
        return true
    }

    fun renombrarCategoria(viejo: String, nuevo: String): Boolean {
        val limpio = nuevo.trim()
        val lista = obtenerCategorias()
        val indice = lista.indexOf(viejo)
        if (indice < 0 || limpio.isEmpty()) return false
        if (lista.any { it.equals(limpio, ignoreCase = true) && it != viejo }) return false

        lista[indice] = limpio
        guardarCategorias(lista)

        val plan = obtenerPlan()
        plan.filter { it.optString("categoria") == viejo }.forEach { it.put("categoria", limpio) }
        guardarPlan(plan)

        val documentos = obtenerDocumentos()
        documentos.filter { it.optString("categoria") == viejo }.forEach { it.put("categoria", limpio) }
        guardarDocumentos(documentos)
        return true
    }

    /** Una categoría en uso por alguna actividad no se puede eliminar. */
    fun eliminarCategoria(nombre: String): Boolean {
        if (obtenerPlan().any { it.optString("categoria") == nombre }) return false
        val lista = obtenerCategorias()
        if (!lista.remove(nombre)) return false
        guardarCategorias(lista)
        return true
    }

    // ---------------------------------------------------------------
    // Repuestos (RF-24 a RF-27)
    // ---------------------------------------------------------------

    fun obtenerRepuestos(): MutableList<JSONObject> = leerLista(LLAVE_REPUESTOS)

    private fun guardarRepuestos(lista: List<JSONObject>) = escribirLista(LLAVE_REPUESTOS, lista)

    fun repuestosDeRegistro(idRegistro: String): List<JSONObject> =
        obtenerRepuestos().filter { it.optString("registro_id") == idRegistro }

    fun valorRepuesto(repuesto: JSONObject): Double =
        repuesto.optInt("cantidad", 1) * repuesto.optDouble("valor_unitario", 0.0)

    /**
     * RN-04: si el registro tiene repuestos, su costo de repuestos es la suma de ellos y el
     * total se recalcula como mano de obra + repuestos + otros costos.
     */
    private fun recalcularCostoRegistro(idRegistro: String) {
        val partes = repuestosDeRegistro(idRegistro)
        if (partes.isEmpty()) return

        val documentos = obtenerDocumentos()
        val registro = documentos.firstOrNull { it.optString("id") == idRegistro } ?: return

        val costoRepuestos = partes.sumOf { valorRepuesto(it) }
        registro.put("costo_repuestos", costoRepuestos)
        registro.put(
            "costo",
            ReglasMantenimiento.costoTotal(
                registro.optDouble("mano_obra", 0.0),
                costoRepuestos,
                registro.optDouble("otros_costos", 0.0)
            )
        )
        guardarDocumentos(documentos)
        sincronizarDocumento(registro)
    }

    fun agregarRepuesto(repuesto: JSONObject) {
        if (!repuesto.has("id")) repuesto.put("id", java.util.UUID.randomUUID().toString())
        repuesto.put("instalado", true)
        val lista = obtenerRepuestos()
        lista.add(repuesto)
        guardarRepuestos(lista)
        recalcularCostoRegistro(repuesto.optString("registro_id"))
    }

    fun eliminarRepuesto(id: String) {
        val lista = obtenerRepuestos()
        val repuesto = lista.firstOrNull { it.optString("id") == id } ?: return
        lista.remove(repuesto)
        guardarRepuestos(lista)
        recalcularCostoRegistro(repuesto.optString("registro_id"))
    }

    /** Un repuesto "retirado" ya no está instalado actualmente en el vehículo. */
    fun cambiarInstalado(id: String, instalado: Boolean) {
        val lista = obtenerRepuestos()
        val repuesto = lista.firstOrNull { it.optString("id") == id } ?: return
        repuesto.put("instalado", instalado)
        guardarRepuestos(lista)
    }

    fun estadoGarantiaRepuesto(repuesto: JSONObject, kmActual: Int): EstadoGarantia =
        ReglasMantenimiento.estadoGarantia(
            parsearFecha(repuesto.optString("fecha_instalacion")),
            repuesto.optInt("garantia_meses", 0),
            repuesto.optInt("km_instalacion", 0),
            repuesto.optInt("garantia_km", 0),
            kmActual,
            Date()
        )

    // ---------------------------------------------------------------
    // Resumen de gastos (RF-36)
    // ---------------------------------------------------------------

    /** Los documentos se cuentan en la fecha en que se registraron; los servicios, en la suya. */
    fun fechaDeGasto(registro: JSONObject): Date? {
        val esDocumento = !registro.has("actividad_id") &&
                Catalogo.tieneVencimiento(registro.optString("tipo"))
        val texto = if (esDocumento) {
            registro.optString("fecha_registro").ifEmpty { registro.optString("fecha") }
        } else {
            registro.optString("fecha")
        }
        return parsearFecha(texto)
    }

    fun categoriaDeRegistro(registro: JSONObject): String =
        registro.optString("categoria").ifEmpty { Catalogo.categoriaDe(registro.optString("tipo")) }

    /** Suma los costos por vehículo, categoría y mes. [placa] y [desde] nulos = sin filtro. */
    fun resumenGastos(placa: String?, desde: Date?): ResumenGastos {
        val porVehiculo = mutableMapOf<String, Double>()
        val porCategoria = mutableMapOf<String, Double>()
        val porMes = mutableMapOf<String, Double>()
        var total = 0.0

        for (registro in obtenerDocumentos()) {
            val costo = registro.optDouble("costo", 0.0)
            if (costo <= 0.0) continue
            if (placa != null && !registro.optString("placa").equals(placa, ignoreCase = true)) continue

            val fecha = fechaDeGasto(registro)
            if (desde != null && (fecha == null || fecha.before(desde))) continue

            total += costo
            val vehiculo = registro.optString("placa")
            porVehiculo[vehiculo] = (porVehiculo[vehiculo] ?: 0.0) + costo
            val categoria = categoriaDeRegistro(registro)
            porCategoria[categoria] = (porCategoria[categoria] ?: 0.0) + costo

            if (fecha != null) {
                val clave = SimpleDateFormat("yyyy-MM", Locale.US).format(fecha)
                porMes[clave] = (porMes[clave] ?: 0.0) + costo
            }
        }

        val etiquetaMes = SimpleDateFormat("MMM yyyy", Locale("es", "CO"))
        val formatoClave = SimpleDateFormat("yyyy-MM", Locale.US)
        return ResumenGastos(
            total = total,
            porVehiculo = porVehiculo.toList().sortedByDescending { it.second },
            porCategoria = porCategoria.toList().sortedByDescending { it.second },
            porMes = porMes.toList().sortedByDescending { it.first }.map { (clave, valor) ->
                val fecha = formatoClave.parse(clave)
                (if (fecha != null) etiquetaMes.format(fecha) else clave) to valor
            }
        )
    }

    // ---------------------------------------------------------------
    // Adjuntos de un registro: fotos y documentos de soporte (RF-20)
    // ---------------------------------------------------------------

    fun adjuntosDe(registro: JSONObject): List<JSONObject> {
        val arreglo = registro.optJSONArray("adjuntos") ?: return emptyList()
        return (0 until arreglo.length()).mapNotNull { arreglo.optJSONObject(it) }
    }

    fun agregarAdjunto(idRegistro: String, nombre: String, direccion: String) {
        val documentos = obtenerDocumentos()
        val registro = documentos.firstOrNull { it.optString("id") == idRegistro } ?: return

        val arreglo = registro.optJSONArray("adjuntos") ?: JSONArray()
        arreglo.put(JSONObject().apply {
            put("nombre", nombre)
            put("uri", direccion)
        })
        registro.put("adjuntos", arreglo)
        guardarDocumentos(documentos)
    }

    fun quitarAdjunto(idRegistro: String, posicion: Int) {
        val documentos = obtenerDocumentos()
        val registro = documentos.firstOrNull { it.optString("id") == idRegistro } ?: return
        val arreglo = registro.optJSONArray("adjuntos") ?: return
        if (posicion < 0 || posicion >= arreglo.length()) return

        arreglo.remove(posicion)
        guardarDocumentos(documentos)
    }

    // ---------------------------------------------------------------
    // Moneda (RNF-17)
    // ---------------------------------------------------------------

    fun obtenerMoneda(): String = preferencias.getString(LLAVE_MONEDA, "COP") ?: "COP"

    fun guardarMoneda(codigo: String) {
        preferencias.edit().putString(LLAVE_MONEDA, codigo).apply()
        Formato.codigoMoneda = codigo
    }

    // ---------------------------------------------------------------
    // Copia de respaldo (RF-39)
    // ---------------------------------------------------------------

    /** Todos los datos locales en un solo texto JSON. */
    fun exportarRespaldo(): String {
        val datos = JSONObject()
        for (llave in LLAVES_RESPALDO) {
            if (preferencias.contains(llave)) {
                try {
                    datos.put(llave, JSONArray(preferencias.getString(llave, "[]")))
                } catch (_: Exception) {
                }
            }
        }
        datos.put("moneda", obtenerMoneda())
        datos.put("pospuestas", leerPospuestas())

        return JSONObject().apply {
            put("aplicacion", "Min Bil")
            put("version", 1)
            put("fecha", formatearFecha(Date()))
            put("datos", datos)
        }.toString(2)
    }

    /**
     * Reemplaza los datos locales por los de una copia de respaldo y vuelve a sincronizarlos
     * con Firestore (los identificadores no cambian, así que no se crean duplicados).
     * Devuelve false si el archivo no es una copia válida.
     */
    fun restaurarRespaldo(texto: String): Boolean {
        val datos = try {
            JSONObject(texto).optJSONObject("datos")
        } catch (_: Exception) {
            null
        } ?: return false

        if (datos.optJSONArray("vehiculos") == null || datos.optJSONArray("documentos") == null) {
            return false
        }

        val editor = preferencias.edit()
        for (llave in LLAVES_RESPALDO) {
            val arreglo = datos.optJSONArray(llave)
            if (arreglo != null) editor.putString(llave, arreglo.toString()) else editor.remove(llave)
        }

        val moneda = datos.optString("moneda")
        if (moneda.isNotEmpty()) editor.putString(LLAVE_MONEDA, moneda)

        val pospuestas = datos.optJSONObject("pospuestas")
        if (pospuestas != null) editor.putString(LLAVE_POSPUESTAS, pospuestas.toString())
        else editor.remove(LLAVE_POSPUESTAS)
        editor.apply()

        Formato.codigoMoneda = obtenerMoneda()

        obtenerVehiculos().forEach { sincronizarVehiculo(it) }
        obtenerDocumentos().forEach { sincronizarDocumento(it) }
        return true
    }

    // ---------------------------------------------------------------
    // Borrado total (RF-40)
    // ---------------------------------------------------------------

    /** Elimina TODOS los datos del usuario: en la nube (por vehículo) y en el teléfono. */
    fun borrarTodo() {
        val placas = (obtenerVehiculos().map { it.optString("placa") } +
                obtenerDocumentos().map { it.optString("placa") })
            .filter { it.isNotBlank() }
            .toSet()

        placas.forEach { placa ->
            nube.eliminarVehiculo(placa) { exito, error ->
                if (!exito) avisarErrorNube(R.string.error_nube_eliminar, error)
            }
        }

        // También borra los avisos ya enviados, para que no queden restos.
        preferencias.edit().clear().apply()
        Formato.codigoMoneda = "COP"
    }

    // ---------------------------------------------------------------
    // Estados, alertas y resúmenes
    // ---------------------------------------------------------------

    fun parsearFecha(texto: String): Date? {
        return try {
            val formato = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
            formato.isLenient = false
            formato.parse(texto)
        } catch (_: Exception) {
            null
        }
    }

    fun estadoRegistro(
        registro: JSONObject,
        vehiculos: List<JSONObject>,
        hoy: Date = Date()
    ): EstadoRegistro {
        val tipo = registro.optString("tipo")
        val placa = registro.optString("placa")
        val fecha = parsearFecha(registro.optString("fecha"))

        val dias = if (fecha != null) ReglasMantenimiento.diasHasta(fecha, hoy) else null
        val esServicioDelPlan = registro.has("actividad_id")
        val estadoFecha = if (!esServicioDelPlan && Catalogo.tieneVencimiento(tipo)) {
            ReglasMantenimiento.estadoPorFecha(fecha, hoy)
        } else {
            EstadoActividad.SIN_PROGRAMACION
        }

        val kmActual = vehiculos.firstOrNull {
            it.optString("placa").equals(placa, ignoreCase = true)
        }?.optInt("kilometraje", 0) ?: 0

        val estadoKm = ReglasMantenimiento.estadoPorKilometraje(
            kmActual,
            registro.optInt("proximo_kilometraje", 0)
        )

        return EstadoRegistro(
            general = ReglasMantenimiento.estadoGeneral(estadoFecha, estadoKm),
            porFecha = estadoFecha,
            porKm = estadoKm,
            diasRestantes = dias,
            kmActual = kmActual
        )
    }

    /** Texto de una alerta según el estado por fecha y por kilometraje. */
    private fun detalleAlerta(estado: EstadoRegistro, fechaTexto: String, proximoKm: Int): String {
        val lineas = mutableListOf<String>()

        if (estado.porFecha == EstadoActividad.VENCIDA) {
            lineas.add(contexto.getString(R.string.alerta_vencio_fecha, fechaTexto))
        } else if (estado.porFecha == EstadoActividad.PROXIMA) {
            lineas.add(
                when (val dias = estado.diasRestantes ?: 0) {
                    0 -> contexto.getString(R.string.alerta_vence_hoy, fechaTexto)
                    1 -> contexto.getString(R.string.alerta_vence_manana, fechaTexto)
                    else -> contexto.getString(R.string.alerta_vence_en_dias, dias, fechaTexto)
                }
            )
        }

        if (estado.porKm == EstadoActividad.VENCIDA) {
            lineas.add(
                contexto.getString(
                    R.string.alerta_vencio_km,
                    Formato.kilometros(proximoKm),
                    Formato.kilometros(estado.kmActual)
                )
            )
        } else if (estado.porKm == EstadoActividad.PROXIMA) {
            lineas.add(
                contexto.getString(
                    R.string.alerta_vence_km,
                    Formato.kilometros(proximoKm),
                    Formato.kilometros(proximoKm - estado.kmActual)
                )
            )
        }

        return lineas.joinToString("\n")
    }

    /**
     * Alertas vigentes (vencidas primero), de documentos y del plan de mantenimiento.
     * RF-09: se recalculan siempre con el kilometraje actual.
     */
    fun alertas(incluirPospuestas: Boolean = false): List<AlertaItem> {
        val vehiculos = obtenerVehiculos()
        val hoy = Date()
        val resultado = mutableListOf<AlertaItem>()

        // Documentos y servicios registrados
        for (registro in obtenerDocumentos()) {
            val estado = estadoRegistro(registro, vehiculos, hoy)
            if (estado.general != EstadoActividad.VENCIDA &&
                estado.general != EstadoActividad.PROXIMA
            ) continue

            val tipo = registro.optString("tipo")
            val placa = registro.optString("placa")
            resultado.add(
                AlertaItem(
                    estado = estado.general,
                    placa = placa,
                    titulo = contexto.getString(R.string.titulo_registro, tipo, placa),
                    detalle = detalleAlerta(
                        estado,
                        registro.optString("fecha"),
                        registro.optInt("proximo_kilometraje", 0)
                    ),
                    idOrigen = registro.optString("id")
                )
            )
        }

        // Actividades del plan (se omiten las pausadas y las que tienen las alertas desactivadas)
        for (actividad in obtenerPlan()) {
            if (actividad.optBoolean("pausada", false)) continue
            if (!actividad.optBoolean("alertas_activas", true)) continue

            val estado = estadoActividadPlan(actividad, vehiculos, hoy)
            if (estado.general != EstadoActividad.VENCIDA &&
                estado.general != EstadoActividad.PROXIMA
            ) continue

            val placa = actividad.optString("placa")
            val proximaFecha = actividad.optString("proxima_fecha")
            val proximoKm = actividad.optInt("proximo_km", 0)
            resultado.add(
                AlertaItem(
                    estado = estado.general,
                    placa = placa,
                    titulo = contexto.getString(
                        R.string.titulo_registro,
                        actividad.optString("nombre"),
                        placa
                    ),
                    detalle = detalleAlerta(estado, proximaFecha, proximoKm),
                    clave = "plan_${actividad.optString("id")}_${proximaFecha}_${proximoKm}_${estado.general.name}",
                    idOrigen = actividad.optString("id")
                )
            )
        }

        return resultado
            .filter { incluirPospuestas || !estaPospuesta(it.idOrigen, hoy) }
            .sortedBy { if (it.estado == EstadoActividad.VENCIDA) 0 else 1 }
    }

    // ---------------------------------------------------------------
    // Posponer alertas (RF-31, RN-08)
    // ---------------------------------------------------------------

    private fun leerPospuestas(): JSONObject = try {
        JSONObject(preferencias.getString(LLAVE_POSPUESTAS, "{}"))
    } catch (_: Exception) {
        JSONObject()
    }

    /**
     * Oculta una alerta durante [dias] días. Solo cambia la fecha del AVISO: el vencimiento
     * real de la actividad o del documento no se modifica (RN-08).
     */
    fun posponerAlerta(id: String, dias: Int): String {
        val hasta = formatearFecha(ReglasMantenimiento.sumarDias(Date(), dias))
        val pospuestas = leerPospuestas()
        pospuestas.put(id, hasta)
        preferencias.edit().putString(LLAVE_POSPUESTAS, pospuestas.toString()).apply()
        return hasta
    }

    fun estaPospuesta(id: String, hoy: Date = Date()): Boolean {
        if (id.isEmpty()) return false
        val hasta = parsearFecha(leerPospuestas().optString(id)) ?: return false
        return ReglasMantenimiento.diasHasta(hasta, hoy) > 0
    }

    fun cantidadPospuestas(): Int =
        alertas(incluirPospuestas = true).count { estaPospuesta(it.idOrigen) }

    fun quitarPosposiciones() {
        preferencias.edit().remove(LLAVE_POSPUESTAS).apply()
    }

    fun totalGastos(): Double = obtenerDocumentos().sumOf { it.optDouble("costo", 0.0) }

    /** Últimos registros con costo, del más reciente al más antiguo (por orden de guardado). */
    fun ultimosGastos(cantidad: Int): List<JSONObject> =
        obtenerDocumentos().filter { it.optDouble("costo", 0.0) > 0.0 }.asReversed().take(cantidad)
}
