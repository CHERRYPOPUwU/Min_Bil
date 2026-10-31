package com.example.controlvial

import android.app.DatePickerDialog
import android.content.DialogInterface
import android.view.LayoutInflater
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Diálogo para actualizar el kilometraje con fecha y valor del odómetro (RF-06 a RF-09). */
object DialogoKilometraje {

    fun mostrar(fragmento: Fragment, placa: String, alGuardar: () -> Unit) {
        val contexto = fragmento.requireContext()
        val repo = RepositorioMiCarro(contexto)
        val kmActual = repo.buscarVehiculo(placa)?.optInt("kilometraje", 0) ?: 0

        val vista = LayoutInflater.from(contexto).inflate(R.layout.dialog_kilometraje, null)
        val tvActual = vista.findViewById<TextView>(R.id.tvKmActualDialogo)
        val tilKm = vista.findViewById<TextInputLayout>(R.id.tilKmLectura)
        val etKm = vista.findViewById<TextInputEditText>(R.id.etKmLectura)
        val tilFecha = vista.findViewById<TextInputLayout>(R.id.tilFechaLectura)
        val etFecha = vista.findViewById<TextInputEditText>(R.id.etFechaLectura)

        tvActual.text = contexto.getString(R.string.km_registrado_actual, Formato.kilometros(kmActual))
        etFecha.setText(repo.formatearFecha(Date()))

        etFecha.setOnClickListener {
            val calendario = Calendar.getInstance()
            DatePickerDialog(
                contexto,
                { _, anio, mes, dia ->
                    etFecha.setText(
                        String.format(Locale.getDefault(), "%02d/%02d/%04d", dia, mes + 1, anio)
                    )
                    tilFecha.error = null
                },
                calendario.get(Calendar.YEAR),
                calendario.get(Calendar.MONTH),
                calendario.get(Calendar.DAY_OF_MONTH)
            ).show()
        }

        val dialogo: AlertDialog = MaterialAlertDialogBuilder(contexto)
            .setTitle(contexto.getString(R.string.actualizar_km_titulo, placa))
            .setView(vista)
            .setNegativeButton(R.string.cancelar, null)
            .setPositiveButton(R.string.guardar, null)
            .create()

        // Se maneja el clic aquí para que el diálogo no se cierre si hay un error de validación.
        dialogo.setOnShowListener {
            dialogo.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener {
                tilKm.error = null
                tilFecha.error = null

                val km = etKm.text.toString().trim().toIntOrNull()
                if (km == null || km <= 0) {
                    tilKm.error = contexto.getString(R.string.error_lectura_km)
                    return@setOnClickListener
                }

                val fechaTexto = etFecha.text.toString().trim()
                val fecha = repo.parsearFecha(fechaTexto)
                if (fecha == null) {
                    tilFecha.error = contexto.getString(R.string.error_fecha_invalida)
                    return@setOnClickListener
                }
                if (ReglasMantenimiento.diasHasta(fecha, Date()) > 0) {
                    tilFecha.error = contexto.getString(R.string.error_lectura_futura)
                    return@setOnClickListener
                }

                fun guardar() {
                    repo.registrarLectura(placa, km, fechaTexto)
                    dialogo.dismiss()
                    Toast.makeText(contexto, R.string.km_actualizado, Toast.LENGTH_SHORT).show()
                    alGuardar()
                }

                // RF-08: una lectura menor a la anterior exige confirmación o corrección.
                if (!ReglasMantenimiento.validarKilometraje(km, kmActual)) {
                    MaterialAlertDialogBuilder(contexto)
                        .setTitle(R.string.km_menor_titulo)
                        .setMessage(
                            contexto.getString(
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
}
