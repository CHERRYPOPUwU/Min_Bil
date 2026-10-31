package com.example.controlvial

import java.util.Date

/**
 * Filtros avanzados del historial (RF-35): categoría, tipo de mantenimiento, taller,
 * rango de costo y rango de fechas. Sin clases de Android, para poder probarlo.
 */
data class FiltroHistorial(
    val categoria: String = "",
    val clase: String = "",
    val taller: String = "",
    val costoMin: Double? = null,
    val costoMax: Double? = null,
    val desde: Date? = null,
    val hasta: Date? = null
) {
    fun cantidadActivos(): Int = listOf(
        categoria.isNotEmpty(),
        clase.isNotEmpty(),
        taller.isNotBlank(),
        costoMin != null,
        costoMax != null,
        desde != null,
        hasta != null
    ).count { it }

    fun cumple(
        categoriaRegistro: String,
        claseRegistro: String,
        tallerRegistro: String,
        costo: Double,
        fecha: Date?
    ): Boolean {
        if (categoria.isNotEmpty() && categoriaRegistro != categoria) return false
        if (clase.isNotEmpty() && claseRegistro != clase) return false
        if (taller.isNotBlank() && !tallerRegistro.contains(taller.trim(), ignoreCase = true)) return false
        if (costoMin != null && costo < costoMin) return false
        if (costoMax != null && costo > costoMax) return false

        if (desde != null || hasta != null) {
            if (fecha == null) return false
            if (desde != null && ReglasMantenimiento.diasHasta(fecha, desde) < 0) return false
            if (hasta != null && ReglasMantenimiento.diasHasta(fecha, hasta) > 0) return false
        }
        return true
    }
}
