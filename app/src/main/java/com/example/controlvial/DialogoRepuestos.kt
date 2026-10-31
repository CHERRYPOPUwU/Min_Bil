package com.example.controlvial

import android.content.DialogInterface
import android.view.LayoutInflater
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import org.json.JSONObject

/** Diálogos para ver y agregar los repuestos de un servicio (RF-24 a RF-26). */
object DialogoRepuestos {

    /** Lista los repuestos del registro [idRegistro] y permite agregar más. */
    fun mostrar(fragmento: Fragment, idRegistro: String, alCambiar: () -> Unit) {
        val contexto = fragmento.requireContext()
        val repo = RepositorioMiCarro(contexto)
        val repuestos = repo.repuestosDeRegistro(idRegistro)

        val dialogo = MaterialAlertDialogBuilder(contexto)
            .setTitle(R.string.repuestos_servicio_titulo)
            .setPositiveButton(R.string.agregar_repuesto) { _, _ ->
                formulario(fragmento, idRegistro, alCambiar)
            }
            .setNegativeButton(R.string.cerrar, null)

        if (repuestos.isEmpty()) {
            dialogo.setMessage(R.string.sin_repuestos_servicio)
        } else {
            val items = repuestos.map {
                val base = contexto.getString(
                    R.string.repuesto_item,
                    it.optString("nombre"),
                    it.optInt("cantidad", 1),
                    Formato.moneda(repo.valorRepuesto(it))
                )
                if (it.optBoolean("instalado", true)) base
                else base + contexto.getString(R.string.repuesto_retirado_sufijo)
            }.toTypedArray()

            dialogo.setItems(items) { _, posicion ->
                opciones(fragmento, repuestos[posicion], idRegistro, alCambiar)
            }
        }
        dialogo.show()
    }

    private fun opciones(
        fragmento: Fragment,
        repuesto: JSONObject,
        idRegistro: String,
        alCambiar: () -> Unit
    ) {
        val contexto = fragmento.requireContext()
        val repo = RepositorioMiCarro(contexto)
        val instalado = repuesto.optBoolean("instalado", true)
        val id = repuesto.optString("id")

        val acciones = arrayOf(
            contexto.getString(if (instalado) R.string.marcar_retirado else R.string.reinstalar),
            contexto.getString(R.string.eliminar)
        )

        MaterialAlertDialogBuilder(contexto)
            .setTitle(repuesto.optString("nombre"))
            .setItems(acciones) { _, posicion ->
                if (posicion == 0) {
                    repo.cambiarInstalado(id, !instalado)
                } else {
                    repo.eliminarRepuesto(id)
                    Toast.makeText(contexto, R.string.repuesto_eliminado, Toast.LENGTH_SHORT).show()
                }
                alCambiar()
                mostrar(fragmento, idRegistro, alCambiar)
            }
            .setNegativeButton(R.string.cancelar) { _, _ -> mostrar(fragmento, idRegistro, alCambiar) }
            .show()
    }

    private fun formulario(fragmento: Fragment, idRegistro: String, alCambiar: () -> Unit) {
        val contexto = fragmento.requireContext()
        val repo = RepositorioMiCarro(contexto)
        val registro = repo.buscarDocumento(idRegistro) ?: return

        val vista = LayoutInflater.from(contexto).inflate(R.layout.dialog_repuesto, null)
        val tilNombre = vista.findViewById<TextInputLayout>(R.id.tilRepNombre)
        val etNombre = vista.findViewById<TextInputEditText>(R.id.etRepNombre)
        val etMarca = vista.findViewById<TextInputEditText>(R.id.etRepMarca)
        val etReferencia = vista.findViewById<TextInputEditText>(R.id.etRepReferencia)
        val tilCantidad = vista.findViewById<TextInputLayout>(R.id.tilRepCantidad)
        val etCantidad = vista.findViewById<TextInputEditText>(R.id.etRepCantidad)
        val tilValor = vista.findViewById<TextInputLayout>(R.id.tilRepValor)
        val etValor = vista.findViewById<TextInputEditText>(R.id.etRepValor)
        val etProveedor = vista.findViewById<TextInputEditText>(R.id.etRepProveedor)
        val tilFecha = vista.findViewById<TextInputLayout>(R.id.tilRepFecha)
        val etFecha = vista.findViewById<TextInputEditText>(R.id.etRepFecha)
        val etGarantiaMeses = vista.findViewById<TextInputEditText>(R.id.etRepGarantiaMeses)
        val etGarantiaKm = vista.findViewById<TextInputEditText>(R.id.etRepGarantiaKm)
        val etObservaciones = vista.findViewById<TextInputEditText>(R.id.etRepObservaciones)

        etCantidad.setText("1")
        etFecha.setText(registro.optString("fecha")) // por defecto, la fecha del servicio
        etFecha.setOnClickListener { Ayudas.elegirFecha(contexto, etFecha, tilFecha) }

        val dialogo = MaterialAlertDialogBuilder(contexto)
            .setTitle(R.string.agregar_repuesto)
            .setView(vista)
            .setNegativeButton(R.string.cancelar) { _, _ -> mostrar(fragmento, idRegistro, alCambiar) }
            .setPositiveButton(R.string.guardar, null)
            .create()

        dialogo.setOnShowListener {
            dialogo.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener {
                tilNombre.error = null
                tilCantidad.error = null
                tilValor.error = null
                tilFecha.error = null

                val nombre = etNombre.text.toString().trim()
                if (nombre.isEmpty()) {
                    tilNombre.error = contexto.getString(R.string.error_rep_nombre)
                    return@setOnClickListener
                }

                val cantidad = etCantidad.text.toString().trim().toIntOrNull() ?: 0
                if (cantidad < 1) {
                    tilCantidad.error = contexto.getString(R.string.error_rep_cantidad)
                    return@setOnClickListener
                }

                val valor = etValor.text.toString().trim().toDoubleOrNull()
                if (valor == null) {
                    tilValor.error = contexto.getString(R.string.error_rep_valor)
                    return@setOnClickListener
                }

                val fechaTexto = etFecha.text.toString().trim()
                if (repo.parsearFecha(fechaTexto) == null) {
                    tilFecha.error = contexto.getString(R.string.error_fecha_invalida)
                    return@setOnClickListener
                }

                repo.agregarRepuesto(JSONObject().apply {
                    put("registro_id", idRegistro)
                    put("placa", registro.optString("placa"))
                    put("nombre", nombre)
                    put("marca", etMarca.text.toString().trim())
                    put("referencia", etReferencia.text.toString().trim())
                    put("cantidad", cantidad)
                    put("valor_unitario", valor)
                    put("proveedor", etProveedor.text.toString().trim())
                    put("fecha_instalacion", fechaTexto)
                    put("km_instalacion", registro.optInt("kilometraje", 0))
                    put("garantia_meses", etGarantiaMeses.text.toString().trim().toIntOrNull() ?: 0)
                    put("garantia_km", etGarantiaKm.text.toString().trim().toIntOrNull() ?: 0)
                    put("observaciones", etObservaciones.text.toString().trim())
                })

                dialogo.dismiss()
                Toast.makeText(contexto, R.string.repuesto_guardado, Toast.LENGTH_LONG).show()
                alCambiar()
                mostrar(fragmento, idRegistro, alCambiar)
            }
        }
        dialogo.show()
    }
}
