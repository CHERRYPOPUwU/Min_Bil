
package com.example.controlvial

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Worker
import androidx.work.WorkerParameters
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class RecordatorioWorker(
    context: Context,
    params: WorkerParameters
) : Worker(context, params) {

    override fun doWork(): Result {
        val context = applicationContext
        val canalId = "vencimientos_controlvial"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val canal = NotificationChannel(
                canalId,
                "Vencimientos de documentos",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Avisos sobre documentos de tus vehículos"
            }

            val administrador =
                context.getSystemService(Context.NOTIFICATION_SERVICE)
                        as NotificationManager

            administrador.createNotificationChannel(canal)
        }

        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return Result.success()
        }

        val preferencias = context.getSharedPreferences(
            "ControlVial",
            Context.MODE_PRIVATE
        )

        val contenido = preferencias.getString("documentos", "[]")
        val documentos = try {
            JSONArray(contenido)
        } catch (_: Exception) {
            return Result.success()
        }

        val formato = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        formato.isLenient = false

        val hoy = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.time

        val diasAviso = listOf(30, 15, 7, 0)

        for (i in 0 until documentos.length()) {
            val documento = documentos.optJSONObject(i) ?: continue

            val placa = documento.optString("placa")
            val tipo = documento.optString("tipo")
            val fecha = documento.optString("fecha")

            val vencimiento: Date = try {
                formato.parse(fecha) ?: continue
            } catch (_: Exception) {
                continue
            }

            val diasRestantes = TimeUnit.MILLISECONDS.toDays(
                vencimiento.time - hoy.time
            ).toInt()

            if (diasRestantes !in diasAviso) continue

            val clave = "${placa}_${tipo}_${fecha}"
                .hashCode().toString()

            val claveAviso = "aviso_${clave}_$diasRestantes"

            if (preferencias.getBoolean(claveAviso, false)) continue

            val mensaje = when (diasRestantes) {
                30 -> "Faltan 30 días para el vencimiento."
                15 -> "Faltan 15 días para el vencimiento."
                7 -> "Faltan 7 días para el vencimiento."
                0 -> "El documento vence hoy."
                else -> continue
            }

            val notificacion = NotificationCompat.Builder(context, canalId)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("$tipo · $placa")
                .setContentText(mensaje)
                .setStyle(
                    NotificationCompat.BigTextStyle()
                        .bigText("$mensaje Fecha: $fecha")
                )
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .build()

            val idNotificacion = clave.hashCode() + diasRestantes

            try {
                NotificationManagerCompat.from(context)
                    .notify(idNotificacion, notificacion)

                preferencias.edit()
                    .putBoolean(claveAviso, true)
                    .apply()
            } catch (_: SecurityException) {
                // El usuario puede haber desactivado los permisos.
            }
        }

        return Result.success()
    }
}
