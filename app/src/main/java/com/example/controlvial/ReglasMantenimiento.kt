package com.example.controlvial

import java.util.Calendar
import java.util.Date
import kotlin.math.roundToInt

/** Estado de una actividad de mantenimiento o de un documento. */
enum class EstadoActividad { AL_DIA, PROXIMA, VENCIDA, SIN_PROGRAMACION }

/** Listas de tipos de registro (son los valores que se guardan en los datos). */
object Catalogo {
    val DOCUMENTOS = listOf(
        "SOAT",
        "Tecnomecánica",
        "Seguro Todo Riesgo",
        "Extintor",
        "Licencia"
    )

    val SERVICIOS = listOf(
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

    val TIPOS_REGISTRO = DOCUMENTOS + SERVICIOS

    const val CATEGORIA_DOCUMENTOS = "Documentos"
    const val CATEGORIA_OTROS = "Otros"

    val CLASES = listOf("Preventivo", "Correctivo")

    /** Categoría de gasto de un tipo de registro (para el resumen de gastos). */
    fun categoriaDe(tipo: String): String {
        if (tieneVencimiento(tipo)) return CATEGORIA_DOCUMENTOS
        return when (tipo) {
            "Cambio de Aceite" -> "Aceite"
            "Filtros" -> "Filtros"
            "Frenos" -> "Frenos"
            "Llantas" -> "Llantas"
            "Batería" -> "Batería"
            "Refrigeración" -> "Refrigeración"
            "Suspensión" -> "Suspensión"
            "Transmisión" -> "Transmisión"
            else -> CATEGORIA_OTROS
        }
    }

    /** Los documentos usan la fecha como vencimiento; los servicios, como fecha de realización. */
    fun tieneVencimiento(tipo: String): Boolean =
        DOCUMENTOS.any { it.equals(tipo, ignoreCase = true) }
}

/** Estado de la garantía de un repuesto. */
enum class EstadoGarantia { SIN_GARANTIA, VIGENTE, VENCIDA }

/**
 * Reglas de negocio puras (sin clases de Android) para poder probarlas con pruebas unitarias.
 * RN-01: una actividad está vencida si se supera CUALQUIERA de sus límites (fecha o kilometraje).
 * RN-02: una actividad está próxima si entra en el margen de anticipación.
 */
object ReglasMantenimiento {

    const val DIAS_ANTICIPACION = 30
    const val KM_ANTICIPACION = 1000

    private const val MILIS_POR_DIA = 24.0 * 60 * 60 * 1000

    private fun inicioDeDia(fecha: Date): Long {
        val calendario = Calendar.getInstance()
        calendario.time = fecha
        calendario.set(Calendar.HOUR_OF_DAY, 0)
        calendario.set(Calendar.MINUTE, 0)
        calendario.set(Calendar.SECOND, 0)
        calendario.set(Calendar.MILLISECOND, 0)
        return calendario.timeInMillis
    }

    /** Días que faltan desde hoy hasta el vencimiento (negativo si ya pasó). */
    fun diasHasta(vencimiento: Date, hoy: Date): Int {
        val diferencia = inicioDeDia(vencimiento) - inicioDeDia(hoy)
        return (diferencia / MILIS_POR_DIA).roundToInt()
    }

    fun estadoPorFecha(
        vencimiento: Date?,
        hoy: Date,
        diasAnticipacion: Int = DIAS_ANTICIPACION
    ): EstadoActividad {
        if (vencimiento == null) return EstadoActividad.SIN_PROGRAMACION
        val dias = diasHasta(vencimiento, hoy)
        return when {
            dias < 0 -> EstadoActividad.VENCIDA
            dias <= diasAnticipacion -> EstadoActividad.PROXIMA
            else -> EstadoActividad.AL_DIA
        }
    }

    fun estadoPorKilometraje(
        kmActual: Int,
        proximoKm: Int,
        kmAnticipacion: Int = KM_ANTICIPACION
    ): EstadoActividad {
        if (proximoKm <= 0) return EstadoActividad.SIN_PROGRAMACION
        return when {
            kmActual >= proximoKm -> EstadoActividad.VENCIDA
            proximoKm - kmActual <= kmAnticipacion -> EstadoActividad.PROXIMA
            else -> EstadoActividad.AL_DIA
        }
    }

    private fun gravedad(estado: EstadoActividad): Int = when (estado) {
        EstadoActividad.VENCIDA -> 2
        EstadoActividad.PROXIMA -> 1
        else -> 0
    }

    /** Devuelve el más grave de los dos estados; SIN_PROGRAMACION solo si ambos lo son. */
    fun estadoGeneral(
        estadoFecha: EstadoActividad,
        estadoKm: EstadoActividad
    ): EstadoActividad {
        val candidatos = listOf(estadoFecha, estadoKm)
            .filter { it != EstadoActividad.SIN_PROGRAMACION }
        if (candidatos.isEmpty()) return EstadoActividad.SIN_PROGRAMACION
        return candidatos.maxByOrNull { gravedad(it) } ?: EstadoActividad.SIN_PROGRAMACION
    }

    fun sumarDias(fecha: Date, dias: Int): Date {
        val calendario = Calendar.getInstance()
        calendario.time = fecha
        calendario.add(Calendar.DAY_OF_YEAR, dias)
        return calendario.time
    }

    /**
     * RN-03 / RF-21: al completar una actividad periódica, el siguiente vencimiento se calcula
     * desde la fecha y el kilometraje REALES del servicio. Sin intervalo, no hay siguiente.
     */
    fun siguienteFecha(realizada: Date, intervaloDias: Int): Date? =
        if (intervaloDias > 0) sumarDias(realizada, intervaloDias) else null

    fun siguienteKm(kmReal: Int, intervaloKm: Int): Int =
        if (intervaloKm > 0 && kmReal > 0) kmReal + intervaloKm else 0

    /** RN-04: el costo total es mano de obra + repuestos + otros costos. */
    fun costoTotal(manoObra: Double, repuestos: Double, otros: Double): Double =
        manoObra + repuestos + otros

    fun agregarMeses(fecha: Date, meses: Int): Date {
        val calendario = Calendar.getInstance()
        calendario.time = fecha
        calendario.add(Calendar.MONTH, meses)
        return calendario.time
    }

    /**
     * RF-26: la garantía puede ser por tiempo, por kilometraje o por ambos.
     * Está vencida si se supera CUALQUIERA de sus límites.
     */
    fun estadoGarantia(
        fechaInstalacion: Date?,
        garantiaMeses: Int,
        kmInstalacion: Int,
        garantiaKm: Int,
        kmActual: Int,
        hoy: Date
    ): EstadoGarantia {
        val porTiempo = garantiaMeses > 0 && fechaInstalacion != null
        val porKm = garantiaKm > 0
        if (!porTiempo && !porKm) return EstadoGarantia.SIN_GARANTIA

        if (porTiempo && diasHasta(agregarMeses(fechaInstalacion!!, garantiaMeses), hoy) < 0) {
            return EstadoGarantia.VENCIDA
        }
        if (porKm && kmActual > kmInstalacion + garantiaKm) return EstadoGarantia.VENCIDA
        return EstadoGarantia.VIGENTE
    }

    /** RF-08 / RN-06: una lectura menor que la anterior requiere confirmación. */
    fun validarKilometraje(nueva: Int, anterior: Int): Boolean = nueva >= anterior
}
