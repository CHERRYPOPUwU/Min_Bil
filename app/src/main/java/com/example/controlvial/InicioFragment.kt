package com.example.controlvial

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Pantalla de inicio: vehículo principal, resumen, gastos recientes y alertas (RF-37). */
class InicioFragment : Fragment(R.layout.fragment_inicio) {

    /** Guardar la copia de respaldo en un archivo elegido por el usuario (RF-39). */
    private val selectorCrearRespaldo =
        registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
            if (uri != null) escribirRespaldo(uri)
        }

    /** Elegir el archivo de una copia de respaldo para restaurarla (RF-39). */
    private val selectorRestaurar =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) confirmarRestaurar(uri)
        }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<View>(R.id.btnSalir).setOnClickListener { confirmarCerrarSesion() }

        view.findViewById<View>(R.id.btnVerAlertas).setOnClickListener {
            (activity as? MainActivity)?.irA(R.id.nav_alertas)
        }

        view.findViewById<View>(R.id.btnAgregarPrimero).setOnClickListener {
            (activity as? MainActivity)?.irA(R.id.nav_vehiculos)
        }

        view.findViewById<View>(R.id.btnActualizarKm).setOnClickListener {
            val placa = RepositorioMiCarro(requireContext()).vehiculoPrincipal()?.optString("placa")
            if (!placa.isNullOrEmpty()) {
                DialogoKilometraje.mostrar(this, placa) { actualizar() }
            }
        }

        view.findViewById<View>(R.id.btnBorrarDatos).setOnClickListener { confirmarBorrarDatos() }
        view.findViewById<View>(R.id.btnMoneda).setOnClickListener { elegirMoneda() }

        view.findViewById<View>(R.id.btnCrearRespaldo).setOnClickListener {
            val fecha = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
            selectorCrearRespaldo.launch(getString(R.string.respaldo_nombre_archivo, fecha))
        }
        view.findViewById<View>(R.id.btnRestaurarRespaldo).setOnClickListener {
            selectorRestaurar.launch(arrayOf("*/*"))
        }
    }

    override fun onResume() {
        super.onResume()
        actualizar()
    }

    private fun actualizar() {
        val vista = view ?: return
        val repo = RepositorioMiCarro(requireContext())
        val vehiculos = repo.obtenerVehiculos().filter { !repo.esArchivado(it) }
        // Las alertas pospuestas siguen contando para el estado real, pero no se listan.
        val alertas = repo.alertas(incluirPospuestas = true)
        val alertasVisibles = repo.alertas()

        val pendientes = repo.cantidadPendientes()
        val avisoPendientes = vista.findViewById<TextView>(R.id.tvPendientes)
        avisoPendientes.visibility = if (pendientes > 0) View.VISIBLE else View.GONE
        avisoPendientes.text =
            resources.getQuantityString(R.plurals.n_pendientes, pendientes, pendientes)

        pintarPrincipal(vista, repo, alertas)

        vista.findViewById<TextView>(R.id.tvStatVehiculos).text = vehiculos.size.toString()
        vista.findViewById<TextView>(R.id.tvStatProximos).text =
            alertas.count { it.estado == EstadoActividad.PROXIMA }.toString()
        vista.findViewById<TextView>(R.id.tvStatVencidos).text =
            alertas.count { it.estado == EstadoActividad.VENCIDA }.toString()

        vista.findViewById<TextView>(R.id.btnMoneda).text =
            getString(R.string.moneda_actual, repo.obtenerMoneda())
        pintarTasa(vista, repo.obtenerMoneda())

        pintarGastos(vista, repo)
        pintarAlertas(vista, alertasVisibles)
    }

    private fun pintarPrincipal(vista: View, repo: RepositorioMiCarro, alertas: List<AlertaItem>) {
        val tarjeta = vista.findViewById<View>(R.id.cardPrincipal)
        val vacio = vista.findViewById<View>(R.id.cardSinVehiculos)
        val principal = repo.vehiculoPrincipal()

        if (principal == null) {
            tarjeta.visibility = View.GONE
            vacio.visibility = View.VISIBLE
            return
        }

        tarjeta.visibility = View.VISIBLE
        vacio.visibility = View.GONE

        val placa = principal.optString("placa")
        vista.findViewById<TextView>(R.id.tvPlacaPrincipal).text = placa

        // La foto opcional del registro del vehículo aparece arriba de la tarjeta.
        Ayudas.mostrarBanner(
            requireContext(),
            vista.findViewById<ImageView>(R.id.ivFotoPrincipal),
            principal.optString("foto")
        )

        val detalle = listOf(
            principal.optString("marca"),
            principal.optString("modelo"),
            principal.optString("anio")
        ).filter { it.isNotBlank() }.joinToString(" · ")
        vista.findViewById<TextView>(R.id.tvDetallePrincipal).text =
            if (detalle.isEmpty()) getString(R.string.sin_detalle) else detalle

        vista.findViewById<TextView>(R.id.tvKmPrincipal).text = getString(
            R.string.km_formato,
            Formato.kilometros(principal.optInt("kilometraje", 0))
        )

        val propias = alertas.filter { it.placa.equals(placa, ignoreCase = true) }
        val vencidas = propias.count { it.estado == EstadoActividad.VENCIDA }
        val proximas = propias.count { it.estado == EstadoActividad.PROXIMA }
        val tieneRegistros = repo.obtenerDocumentos().any {
            it.optString("placa").equals(placa, ignoreCase = true)
        }

        val (estado, texto) = when {
            vencidas > 0 -> EstadoActividad.VENCIDA to
                resources.getQuantityString(R.plurals.n_vencidas, vencidas, vencidas)
            proximas > 0 -> EstadoActividad.PROXIMA to
                resources.getQuantityString(R.plurals.n_proximas, proximas, proximas)
            tieneRegistros -> EstadoActividad.AL_DIA to getString(R.string.estado_al_dia)
            else -> EstadoActividad.SIN_PROGRAMACION to getString(R.string.estado_sin_registros)
        }
        Estilos.aplicarEtiqueta(vista.findViewById(R.id.tvEstadoPrincipal), estado, texto)
    }

    private fun pintarGastos(vista: View, repo: RepositorioMiCarro) {
        vista.findViewById<TextView>(R.id.tvGastoTotal).text = Formato.moneda(repo.totalGastos())

        val contenedor = vista.findViewById<LinearLayout>(R.id.contenedorGastos)
        contenedor.removeAllViews()

        val ultimos = repo.ultimosGastos(3)
        vista.findViewById<View>(R.id.tvSinGastos).visibility =
            if (ultimos.isEmpty()) View.VISIBLE else View.GONE

        for (registro in ultimos) {
            val fila = layoutInflater.inflate(R.layout.item_gasto, contenedor, false)
            fila.findViewById<TextView>(R.id.tvGastoTitulo).text = getString(
                R.string.titulo_registro,
                registro.optString("tipo"),
                registro.optString("placa")
            )
            fila.findViewById<TextView>(R.id.tvGastoFecha).text = registro.optString("fecha")
            fila.findViewById<TextView>(R.id.tvGastoMonto).text =
                Formato.moneda(registro.optDouble("costo", 0.0))
            contenedor.addView(fila)
        }
    }

    private fun pintarAlertas(vista: View, alertas: List<AlertaItem>) {
        val contenedor = vista.findViewById<LinearLayout>(R.id.contenedorAlertasInicio)
        contenedor.removeAllViews()

        vista.findViewById<View>(R.id.tvSinAlertas).visibility =
            if (alertas.isEmpty()) View.VISIBLE else View.GONE
        vista.findViewById<View>(R.id.btnVerAlertas).visibility =
            if (alertas.isEmpty()) View.GONE else View.VISIBLE

        alertas.take(3).forEach {
            contenedor.addView(Vistas.tarjetaAlerta(layoutInflater, contenedor, it))
        }
    }

    /** Explica qué tasa se está usando para convertir los montos. */
    private fun pintarTasa(vista: View, codigo: String) {
        val texto = vista.findViewById<TextView>(R.id.tvTasaMoneda)
        if (codigo == Monedas.BASE) {
            texto.text = getString(R.string.moneda_tasa_base)
            return
        }
        val formato = java.text.NumberFormat.getNumberInstance(Locale("es", "CO"))
        formato.maximumFractionDigits = 2
        val tasa = formato.format(Monedas.tasa(codigo))
        texto.text = if (Monedas.fechaTasas.isNotEmpty()) {
            getString(R.string.moneda_tasa_actualizada, codigo, tasa, Monedas.fechaTasas)
        } else {
            getString(R.string.moneda_tasa_aproximada, codigo, tasa)
        }
    }

    private fun elegirMoneda() {
        val repo = RepositorioMiCarro(requireContext())
        val opciones = resources.getStringArray(R.array.monedas)
        val codigos = opciones.map { it.substringBefore("|") }
        val etiquetas = opciones.map {
            getString(R.string.moneda_item, it.substringBefore("|"), it.substringAfter("|"))
        }.toTypedArray()
        val actual = codigos.indexOf(repo.obtenerMoneda()).coerceAtLeast(0)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.moneda_titulo)
            .setSingleChoiceItems(etiquetas, actual) { dialogo, posicion ->
                repo.guardarMoneda(codigos[posicion])
                dialogo.dismiss()
                actualizar()
                // Se intenta traer la tasa más reciente y se vuelve a pintar con ella.
                Monedas.actualizar(requireContext(), forzar = true) { exito ->
                    if (isAdded) {
                        actualizar()
                        Toast.makeText(
                            requireContext(),
                            if (exito) R.string.moneda_tasas_actualizadas else R.string.moneda_sin_conexion,
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
            .setNegativeButton(R.string.cancelar, null)
            .show()
    }

    private fun escribirRespaldo(uri: Uri) {
        val contexto = requireContext()
        try {
            val texto = RepositorioMiCarro(contexto).exportarRespaldo()
            contexto.contentResolver.openOutputStream(uri)?.use {
                it.write(texto.toByteArray(Charsets.UTF_8))
            }
            MaterialAlertDialogBuilder(contexto)
                .setTitle(R.string.respaldo_guardado_titulo)
                .setMessage(R.string.respaldo_guardado_mensaje)
                .setPositiveButton(R.string.entendido, null)
                .show()
        } catch (e: Exception) {
            Bitacora.error("Respaldo", "No se pudo guardar la copia", e)
            Toast.makeText(contexto, R.string.respaldo_error, Toast.LENGTH_LONG).show()
        }
    }

    private fun confirmarRestaurar(uri: Uri) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.restaurar_titulo)
            .setMessage(R.string.restaurar_mensaje)
            .setNegativeButton(R.string.cancelar, null)
            .setPositiveButton(R.string.restaurar_confirmar) { _, _ -> restaurar(uri) }
            .show()
    }

    private fun restaurar(uri: Uri) {
        val contexto = requireContext()
        val exito = try {
            val texto = contexto.contentResolver.openInputStream(uri)
                ?.bufferedReader()?.use { it.readText() }
            texto != null && RepositorioMiCarro(contexto).restaurarRespaldo(texto)
        } catch (e: Exception) {
            Bitacora.error("Respaldo", "No se pudo restaurar la copia", e)
            false
        }

        Toast.makeText(
            contexto,
            if (exito) R.string.restauracion_ok else R.string.restauracion_error,
            Toast.LENGTH_LONG
        ).show()
        if (exito) actualizar()
    }

    /** RF-40: borrado definitivo de todos los datos, con confirmación. */
    private fun confirmarBorrarDatos() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.borrar_datos_titulo)
            .setMessage(R.string.borrar_datos_mensaje)
            .setNegativeButton(R.string.cancelar, null)
            .setPositiveButton(R.string.borrar_datos_confirmar) { _, _ ->
                RepositorioMiCarro(requireContext()).borrarTodo()
                actualizar()
                Toast.makeText(requireContext(), R.string.datos_borrados, Toast.LENGTH_LONG).show()
            }
            .show()
    }

    private fun confirmarCerrarSesion() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.cerrar_sesion_titulo)
            .setMessage(R.string.cerrar_sesion_mensaje)
            .setNegativeButton(R.string.cancelar, null)
            .setPositiveButton(R.string.cerrar_sesion) { _, _ ->
                FirebaseAuth.getInstance().signOut()

                val intent = Intent(requireContext(), LoginActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
                startActivity(intent)
                activity?.finish()
            }
            .show()
    }
}
