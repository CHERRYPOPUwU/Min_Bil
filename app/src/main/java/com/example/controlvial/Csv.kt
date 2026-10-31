package com.example.controlvial

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Generación de archivos CSV para exportar el historial (RF-38).
 * Usa ";" como separador y coma decimal, que es lo que espera Excel en español.
 */
object Csv {

    const val SEPARADOR = ";"

    /**
     * Escapa un campo. Además neutraliza los textos que empiezan por = + - @, que Excel podría
     * interpretar como una fórmula (inyección de fórmulas en hojas de cálculo).
     */
    fun escapar(campo: String): String {
        val seguro = if (campo.isNotEmpty() && campo[0] in "=+-@") "'$campo" else campo
        val necesitaComillas = seguro.any { it == ';' || it == '"' || it == '\n' || it == '\r' }
        return if (necesitaComillas) "\"" + seguro.replace("\"", "\"\"") + "\"" else seguro
    }

    /** Un monto sin decimales si es entero; si no, con dos decimales y coma. */
    fun numero(valor: Double): String =
        if (valor == Math.floor(valor)) {
            valor.toLong().toString()
        } else {
            BigDecimal(valor).setScale(2, RoundingMode.HALF_UP).toPlainString().replace('.', ',')
        }

    fun generar(encabezados: List<String>, filas: List<List<String>>): String {
        val texto = StringBuilder()
        texto.append('\uFEFF') // marca que permite a Excel leer bien las tildes
        texto.append(encabezados.joinToString(SEPARADOR) { escapar(it) }).append("\r\n")
        filas.forEach { fila ->
            texto.append(fila.joinToString(SEPARADOR) { escapar(it) }).append("\r\n")
        }
        return texto.toString()
    }
}
