package com.example.controlvial

import android.app.DatePickerDialog
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.google.android.material.card.MaterialCardView
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import java.text.NumberFormat
import java.util.Calendar
import java.util.Currency
import java.util.Locale

object Formato {

    /** Código de la moneda elegida por el usuario (se carga al abrir la app). */
    var codigoMoneda: String = "COP"

    /** Muestra un valor guardado (en pesos colombianos) en la moneda elegida, ya convertido. */
    fun moneda(valorCop: Double): String {
        val valor = Monedas.desdeBase(valorCop, codigoMoneda)
        val formato = NumberFormat.getCurrencyInstance(Locale("es", "CO"))
        try {
            formato.currency = Currency.getInstance(codigoMoneda)
        } catch (_: Exception) {
            // Si el código no es válido se conserva la moneda por defecto.
        }
        // El peso colombiano se muestra sin decimales; las demás monedas con dos.
        formato.maximumFractionDigits = Monedas.decimales(codigoMoneda)
        formato.minimumFractionDigits = formato.maximumFractionDigits
        return formato.format(valor)
    }

    /** Texto de un valor guardado (COP) para escribirlo en un campo, en la moneda elegida. */
    fun montoParaCampo(valorCop: Double): String {
        if (valorCop <= 0.0) return ""
        return java.math.BigDecimal(Monedas.desdeBase(valorCop, codigoMoneda))
            .setScale(Monedas.decimales(codigoMoneda), java.math.RoundingMode.HALF_UP)
            .stripTrailingZeros().toPlainString()
    }

    /** Lo escrito en un campo (en la moneda elegida) convertido a pesos para guardarlo. */
    fun montoDesdeCampo(texto: String): Double? =
        Monedas.leerNumero(texto)?.let { Monedas.haciaBase(it, codigoMoneda) }

    fun kilometros(km: Int): String =
        NumberFormat.getIntegerInstance(Locale("es", "CO")).format(km)
}

object Estilos {

    fun colorTexto(contexto: Context, estado: EstadoActividad): Int {
        val recurso = when (estado) {
            EstadoActividad.VENCIDA -> R.color.danger
            EstadoActividad.PROXIMA -> R.color.warning
            EstadoActividad.AL_DIA -> R.color.success
            EstadoActividad.SIN_PROGRAMACION -> R.color.text_secondary
        }
        return ContextCompat.getColor(contexto, recurso)
    }

    fun colorFondo(contexto: Context, estado: EstadoActividad): Int {
        val recurso = when (estado) {
            EstadoActividad.VENCIDA -> R.color.danger_bg
            EstadoActividad.PROXIMA -> R.color.warning_bg
            EstadoActividad.AL_DIA -> R.color.success_bg
            EstadoActividad.SIN_PROGRAMACION -> R.color.neutral_bg
        }
        return ContextCompat.getColor(contexto, recurso)
    }

    /** Pinta una etiqueta redondeada (usa el estilo @style/Etiqueta) según el estado. */
    fun aplicarEtiqueta(vista: TextView, estado: EstadoActividad, texto: String) {
        vista.text = texto
        vista.setTextColor(colorTexto(vista.context, estado))
        vista.backgroundTintList = ColorStateList.valueOf(colorFondo(vista.context, estado))
    }

    fun dp(contexto: Context, valor: Int): Int =
        (valor * contexto.resources.displayMetrics.density).toInt()
}

object Vistas {

    /** Tarjeta de alerta reutilizada en Inicio y en la pantalla Alertas. */
    fun tarjetaAlerta(
        inflater: LayoutInflater,
        padre: ViewGroup,
        alerta: AlertaItem,
        alPosponer: ((AlertaItem) -> Unit)? = null
    ): View {
        val tarjeta = inflater.inflate(R.layout.item_alerta, padre, false) as MaterialCardView
        val contexto = tarjeta.context
        tarjeta.setCardBackgroundColor(Estilos.colorFondo(contexto, alerta.estado))

        val tvEstado = tarjeta.findViewById<TextView>(R.id.tvAlertaEstado)
        tvEstado.setText(
            if (alerta.estado == EstadoActividad.VENCIDA) R.string.alerta_titulo_vencido
            else R.string.alerta_titulo_proximo
        )
        tvEstado.setTextColor(Estilos.colorTexto(contexto, alerta.estado))

        tarjeta.findViewById<TextView>(R.id.tvAlertaTitulo).text = alerta.titulo
        tarjeta.findViewById<TextView>(R.id.tvAlertaDetalle).text = alerta.detalle

        val botonPosponer = tarjeta.findViewById<View>(R.id.btnPosponer)
        if (alPosponer != null && alerta.idOrigen.isNotEmpty()) {
            botonPosponer.visibility = View.VISIBLE
            botonPosponer.setOnClickListener { alPosponer(alerta) }
        } else {
            botonPosponer.visibility = View.GONE
        }
        return tarjeta
    }
}

object Ayudas {

    /**
     * Muestra una foto elegida por el usuario (dirección guardada como texto) en miniatura.
     * Si no hay foto, o ya no se puede leer, deja un ícono de vehículo.
     */
    fun mostrarFoto(contexto: Context, vista: ImageView, direccion: String, ladoDp: Int = 72) {
        val mapa = leerFoto(contexto, direccion, ladoDp)
        if (mapa != null) {
            vista.scaleType = ImageView.ScaleType.CENTER_CROP
            vista.setImageBitmap(mapa)
        } else {
            vista.scaleType = ImageView.ScaleType.CENTER
            vista.setImageResource(R.drawable.ic_nav_vehiculos)
        }
    }

    /**
     * Lee la foto reducida (para no gastar memoria) y la gira si el teléfono la guardó de lado.
     * Devuelve nulo si no hay foto o ya no se puede leer.
     */
    private fun leerFoto(contexto: Context, direccion: String, ladoDp: Int): Bitmap? {
        if (direccion.isEmpty()) return null
        try {
            val lado = Estilos.dp(contexto, ladoDp)
            val uri = Uri.parse(direccion)

            val medidas = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            contexto.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, medidas)
            }

            var muestra = 1
            while (medidas.outWidth / (muestra * 2) >= lado &&
                medidas.outHeight / (muestra * 2) >= lado
            ) {
                muestra *= 2
            }
            val opciones = BitmapFactory.Options().apply { inSampleSize = muestra }
            val mapa = contexto.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, opciones)
            } ?: return null

            return girarSegunOrientacion(contexto, uri, mapa)
        } catch (e: Exception) {
            Bitacora.error("Foto", "No se pudo cargar la imagen", e)
            return null
        }
    }

    private fun girarSegunOrientacion(contexto: Context, uri: Uri, mapa: Bitmap): Bitmap {
        val grados = try {
            contexto.contentResolver.openInputStream(uri)?.use {
                when (android.media.ExifInterface(it).getAttributeInt(
                    android.media.ExifInterface.TAG_ORIENTATION,
                    android.media.ExifInterface.ORIENTATION_NORMAL
                )) {
                    android.media.ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    android.media.ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    android.media.ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            } ?: 0f
        } catch (e: Exception) {
            0f
        }
        if (grados == 0f) return mapa
        val matriz = android.graphics.Matrix().apply { postRotate(grados) }
        return Bitmap.createBitmap(mapa, 0, 0, mapa.width, mapa.height, matriz, true)
    }

    /** Muestra el código de la moneda elegida como prefijo del campo de un monto (ej. "USD "). */
    fun marcarMoneda(campo: TextInputEditText) {
        var padre = campo.parent
        while (padre != null && padre !is TextInputLayout) padre = padre.parent
        (padre as? TextInputLayout)?.prefixText = Formato.codigoMoneda + " "
    }

    /**
     * Muestra la foto como banner. Si no hay foto, o no se puede leer, oculta la vista.
     * Devuelve true si la foto se mostró.
     */
    fun mostrarBanner(contexto: Context, vista: ImageView, direccion: String, anchoDp: Int = 360): Boolean {
        val mapa = leerFoto(contexto, direccion, anchoDp)
        if (mapa == null) {
            vista.visibility = View.GONE
            return false
        }
        vista.scaleType = ImageView.ScaleType.CENTER_CROP
        vista.setImageBitmap(mapa)
        vista.visibility = View.VISIBLE
        return true
    }

    /** Llena una lista desplegable (Material) y deja seleccionado un valor inicial. */
    fun asignar(
        contexto: Context,
        lista: AutoCompleteTextView,
        opciones: List<String>,
        seleccionInicial: String,
        alElegir: (String) -> Unit = {}
    ) {
        lista.setAdapter(ArrayAdapter(contexto, android.R.layout.simple_list_item_1, opciones))
        lista.setText(seleccionInicial, false)
        lista.setOnItemClickListener { padre, _, posicion, _ ->
            alElegir(padre.getItemAtPosition(posicion).toString())
        }
    }

    /** Abre el selector de fecha y escribe el resultado como dd/MM/yyyy. */
    fun elegirFecha(contexto: Context, campo: TextInputEditText, capa: TextInputLayout?) {
        val calendario = Calendar.getInstance()
        DatePickerDialog(
            contexto,
            { _, anio, mes, dia ->
                campo.setText(String.format(Locale.getDefault(), "%02d/%02d/%04d", dia, mes + 1, anio))
                capa?.error = null
            },
            calendario.get(Calendar.YEAR),
            calendario.get(Calendar.MONTH),
            calendario.get(Calendar.DAY_OF_MONTH)
        ).show()
    }
}
