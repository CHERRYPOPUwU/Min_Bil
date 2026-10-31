
package com.example.controlvial

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
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

    /** RF-30: al tocar el aviso se abre la app en la pestaña de alertas. */
    private fun abrirAlertas(context: Context, codigo: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_PESTANA, MainActivity.PESTANA_ALERTAS)
        }
        return PendingIntent.getActivity(
            context,
            codigo,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

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

        // Datos del usuario con sesión iniciada (sin sesión, no hay nada que avisar).
        val repositorio = RepositorioMiCarro(context)
        val preferencias = repositorio.preferenciasActuales()

        // -----------------------------------------------------------
        // 1) Documentos (SOAT, Tecnomecánica, etc.): avisos a 30, 15, 7 y 0 días
        // -----------------------------------------------------------
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

            // Los servicios (aceite, frenos...) no vencen por fecha: solo los documentos.
            if (documento.has("actividad_id") || !Catalogo.tieneVencimiento(tipo)) continue

            val vencimiento: Date = try {
                formato.parse(fecha) ?: continue
            } catch (_: Exception) {
                continue
            }

            // RN-08: una alerta pospuesta no avisa hasta que termine la posposición.
            if (repositorio.estaPospuesta(documento.optString("id"))) continue

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

            val idNotificacion = clave.hashCode() + diasRestantes

            val notificacion = NotificationCompat.Builder(context, canalId)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("$tipo · $placa")
                .setContentText(mensaje)
                .setStyle(
                    NotificationCompat.BigTextStyle()
                        .bigText("$mensaje Fecha: $fecha")
                )
                .setContentIntent(abrirAlertas(context, idNotificacion))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .build()

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

        // -----------------------------------------------------------
        // 2) Plan de mantenimiento: un aviso cuando una actividad pasa a
        //    "próxima" y otro cuando pasa a "vencida" (RF-28, RF-29)
        // -----------------------------------------------------------
        try {
            repositorio.alertas()
                .filter { it.clave.isNotEmpty() }
                .forEach { alerta ->
                    val claveAviso = "aviso_${alerta.clave}"
                    if (preferencias.getBoolean(claveAviso, false)) return@forEach

                    val idNotificacion = alerta.clave.hashCode()
                    val notificacion = NotificationCompat.Builder(context, canalId)
                        .setSmallIcon(android.R.drawable.ic_dialog_info)
                        .setContentTitle(alerta.titulo)
                        .setContentText(alerta.detalle.lineSequence().firstOrNull() ?: "")
                        .setStyle(NotificationCompat.BigTextStyle().bigText(alerta.detalle))
                        .setContentIntent(abrirAlertas(context, idNotificacion))
                        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                        .setAutoCancel(true)
                        .build()

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
        } catch (e: Exception) {
            // Un error en el plan no debe impedir los avisos de documentos.
            Bitacora.error("Avisos", "Falló la revisión del plan", e)
        }

        return Result.success()
    }
}
