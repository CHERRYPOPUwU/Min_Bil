
package com.example.controlvial

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object ProgramadorRecordatorios {

    fun programar(context: Context) {

        val restricciones = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
            .build()

        val solicitud =
            PeriodicWorkRequestBuilder<RecordatorioWorker>(
                24, TimeUnit.HOURS
            )
                .setConstraints(restricciones)
                .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "recordatorios_controlvial",
            ExistingPeriodicWorkPolicy.KEEP,
            solicitud
        )
    }
}
