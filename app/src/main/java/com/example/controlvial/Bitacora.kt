package com.example.controlvial

import android.util.Log

/**
 * RNF-18: registro de errores técnicos SIN datos sensibles. Antes de escribir cualquier
 * mensaje se ocultan las placas, los VIN y los correos electrónicos.
 */
object Bitacora {

    private const val ETIQUETA = "MinBil"

    private val correo = Regex("[\\w.+-]+@[\\w-]+(\\.[\\w-]+)+")
    private val vin = Regex("\\b[A-HJ-NPR-Za-hj-npr-z0-9]{17}\\b")
    private val placa = Regex("\\b[A-Za-z]{3}[- ]?\\d{2}[A-Za-z0-9]\\b")

    /** Devuelve el texto sin correos, VIN ni placas. */
    fun limpiar(texto: String): String =
        texto.replace(correo, "[correo]")
            .replace(vin, "[vin]")
            .replace(placa, "[placa]")

    fun error(origen: String, mensaje: String, causa: Throwable? = null) {
        val detalle = if (causa != null) {
            " (" + causa.javaClass.simpleName + ": " + limpiar(causa.message ?: "") + ")"
        } else {
            ""
        }
        Log.e(ETIQUETA, origen + ": " + limpiar(mensaje) + detalle)
    }
}
