package com.example.controlvial

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.Date

class ReglasMantenimientoTest {

    private val hoy: Date = Calendar.getInstance().apply {
        set(2026, Calendar.OCTOBER, 30, 15, 30, 0)
    }.time

    private fun enDias(dias: Int): Date {
        val calendario = Calendar.getInstance()
        calendario.time = hoy
        calendario.add(Calendar.DAY_OF_YEAR, dias)
        return calendario.time
    }

    @Test
    fun fechaPasada_esVencida() {
        assertEquals(EstadoActividad.VENCIDA, ReglasMantenimiento.estadoPorFecha(enDias(-1), hoy))
    }

    @Test
    fun fechaDeHoy_esProxima() {
        assertEquals(EstadoActividad.PROXIMA, ReglasMantenimiento.estadoPorFecha(enDias(0), hoy))
    }

    @Test
    fun fechaEn10Dias_esProxima() {
        assertEquals(EstadoActividad.PROXIMA, ReglasMantenimiento.estadoPorFecha(enDias(10), hoy))
    }

    @Test
    fun fechaEn90Dias_esAlDia() {
        assertEquals(EstadoActividad.AL_DIA, ReglasMantenimiento.estadoPorFecha(enDias(90), hoy))
    }

    @Test
    fun sinFecha_esSinProgramacion() {
        assertEquals(EstadoActividad.SIN_PROGRAMACION, ReglasMantenimiento.estadoPorFecha(null, hoy))
    }

    @Test
    fun kmActualMayorAlProximo_esVencida() {
        assertEquals(EstadoActividad.VENCIDA, ReglasMantenimiento.estadoPorKilometraje(10500, 10000))
    }

    @Test
    fun faltan500Km_esProxima() {
        assertEquals(EstadoActividad.PROXIMA, ReglasMantenimiento.estadoPorKilometraje(9500, 10000))
    }

    @Test
    fun faltan5000Km_esAlDia() {
        assertEquals(EstadoActividad.AL_DIA, ReglasMantenimiento.estadoPorKilometraje(5000, 10000))
    }

    @Test
    fun sinProximoKm_esSinProgramacion() {
        assertEquals(EstadoActividad.SIN_PROGRAMACION, ReglasMantenimiento.estadoPorKilometraje(5000, 0))
    }

    @Test
    fun estadoGeneral_tomaElMasGrave() {
        assertEquals(
            EstadoActividad.VENCIDA,
            ReglasMantenimiento.estadoGeneral(EstadoActividad.PROXIMA, EstadoActividad.VENCIDA)
        )
        assertEquals(
            EstadoActividad.AL_DIA,
            ReglasMantenimiento.estadoGeneral(EstadoActividad.SIN_PROGRAMACION, EstadoActividad.AL_DIA)
        )
        assertEquals(
            EstadoActividad.SIN_PROGRAMACION,
            ReglasMantenimiento.estadoGeneral(EstadoActividad.SIN_PROGRAMACION, EstadoActividad.SIN_PROGRAMACION)
        )
    }

    @Test
    fun kilometrajeMenor_noEsValido() {
        assertFalse(ReglasMantenimiento.validarKilometraje(900, 1000))
        assertTrue(ReglasMantenimiento.validarKilometraje(1000, 1000))
        assertTrue(ReglasMantenimiento.validarKilometraje(1200, 1000))
    }

    @Test
    fun siguienteFecha_sumaElIntervaloDesdeLaFechaReal() {
        val siguiente = ReglasMantenimiento.siguienteFecha(hoy, 180)
        assertEquals(180, ReglasMantenimiento.diasHasta(siguiente!!, hoy))
    }

    @Test
    fun siguienteFecha_sinIntervaloNoHaySiguiente() {
        assertEquals(null, ReglasMantenimiento.siguienteFecha(hoy, 0))
    }

    @Test
    fun siguienteKm_sumaElIntervaloAlKilometrajeReal() {
        assertEquals(15000, ReglasMantenimiento.siguienteKm(10000, 5000))
        assertEquals(0, ReglasMantenimiento.siguienteKm(10000, 0))
        assertEquals(0, ReglasMantenimiento.siguienteKm(0, 5000))
    }

    @Test
    fun costoTotal_sumaManoDeObraRepuestosYOtros() {
        assertEquals(175000.0, ReglasMantenimiento.costoTotal(80000.0, 90000.0, 5000.0), 0.001)
        assertEquals(0.0, ReglasMantenimiento.costoTotal(0.0, 0.0, 0.0), 0.001)
    }

    @Test
    fun garantia_sinLimitesNoTieneGarantia() {
        assertEquals(
            EstadoGarantia.SIN_GARANTIA,
            ReglasMantenimiento.estadoGarantia(hoy, 0, 1000, 0, 2000, hoy)
        )
    }

    @Test
    fun garantia_porTiempoVigenteYVencida() {
        val hace2Meses = ReglasMantenimiento.agregarMeses(hoy, -2)
        assertEquals(
            EstadoGarantia.VIGENTE,
            ReglasMantenimiento.estadoGarantia(hace2Meses, 6, 0, 0, 0, hoy)
        )
        assertEquals(
            EstadoGarantia.VENCIDA,
            ReglasMantenimiento.estadoGarantia(hace2Meses, 1, 0, 0, 0, hoy)
        )
    }

    @Test
    fun garantia_porKilometraje() {
        assertEquals(
            EstadoGarantia.VIGENTE,
            ReglasMantenimiento.estadoGarantia(null, 0, 10000, 5000, 12000, hoy)
        )
        assertEquals(
            EstadoGarantia.VENCIDA,
            ReglasMantenimiento.estadoGarantia(null, 0, 10000, 5000, 15001, hoy)
        )
    }

    @Test
    fun garantia_vencidaSiSeSuperaCualquierLimite() {
        val hace2Meses = ReglasMantenimiento.agregarMeses(hoy, -2)
        assertEquals(
            EstadoGarantia.VENCIDA,
            ReglasMantenimiento.estadoGarantia(hace2Meses, 12, 10000, 1000, 20000, hoy)
        )
    }

    @Test
    fun categoriaDe_clasificaDocumentosYServicios() {
        assertEquals("Documentos", Catalogo.categoriaDe("SOAT"))
        assertEquals("Aceite", Catalogo.categoriaDe("Cambio de Aceite"))
        assertEquals("Otros", Catalogo.categoriaDe("Mantenimiento General"))
    }

    @Test
    fun filtro_sinCriteriosTodoCumple() {
        val filtro = FiltroHistorial()
        assertEquals(0, filtro.cantidadActivos())
        assertTrue(filtro.cumple("Aceite", "Preventivo", "Taller A", 50000.0, hoy))
    }

    @Test
    fun filtro_porCategoriaClaseYTaller() {
        val filtro = FiltroHistorial(categoria = "Frenos", clase = "Correctivo", taller = "centro")
        assertEquals(3, filtro.cantidadActivos())
        assertTrue(filtro.cumple("Frenos", "Correctivo", "Taller El Centro", 1.0, hoy))
        assertFalse(filtro.cumple("Aceite", "Correctivo", "Taller El Centro", 1.0, hoy))
        assertFalse(filtro.cumple("Frenos", "Preventivo", "Taller El Centro", 1.0, hoy))
        assertFalse(filtro.cumple("Frenos", "Correctivo", "Otro taller", 1.0, hoy))
    }

    @Test
    fun filtro_porRangoDeCosto() {
        val filtro = FiltroHistorial(costoMin = 100.0, costoMax = 500.0)
        assertTrue(filtro.cumple("", "", "", 100.0, hoy))
        assertTrue(filtro.cumple("", "", "", 500.0, hoy))
        assertFalse(filtro.cumple("", "", "", 99.0, hoy))
        assertFalse(filtro.cumple("", "", "", 501.0, hoy))
    }

    @Test
    fun filtro_porRangoDeFechas() {
        val filtro = FiltroHistorial(desde = enDias(-10), hasta = enDias(-2))
        assertTrue(filtro.cumple("", "", "", 0.0, enDias(-5)))
        assertTrue(filtro.cumple("", "", "", 0.0, enDias(-10)))
        assertFalse(filtro.cumple("", "", "", 0.0, enDias(-11)))
        assertFalse(filtro.cumple("", "", "", 0.0, enDias(0)))
        assertFalse(filtro.cumple("", "", "", 0.0, null))
    }

    @Test
    fun csv_escapaSeparadoresComillasYSaltosDeLinea() {
        assertEquals("simple", Csv.escapar("simple"))
        assertEquals("\"a;b\"", Csv.escapar("a;b"))
        assertEquals("\"dijo \"\"hola\"\"\"", Csv.escapar("dijo \"hola\""))
        assertEquals("\"l1\nl2\"", Csv.escapar("l1\nl2"))
    }

    @Test
    fun csv_neutralizaFormulas() {
        assertEquals("'=SUMA(A1)", Csv.escapar("=SUMA(A1)"))
        assertEquals("'+57", Csv.escapar("+57"))
        assertEquals("'@usuario", Csv.escapar("@usuario"))
    }

    @Test
    fun csv_numerosEnteroYDecimal() {
        assertEquals("150000", Csv.numero(150000.0))
        assertEquals("1500,50", Csv.numero(1500.5))
    }

    @Test
    fun csv_generaEncabezadoYFilas() {
        val texto = Csv.generar(listOf("A", "B"), listOf(listOf("1", "x;y")))
        assertEquals("\uFEFFA;B\r\n1;\"x;y\"\r\n", texto)
    }

    @Test
    fun bitacora_ocultaPlacasVinYCorreos() {
        assertEquals("Fallo con [placa] al guardar", Bitacora.limpiar("Fallo con ABC123 al guardar"))
        assertEquals("Moto [placa]", Bitacora.limpiar("Moto abc12d"))
        assertEquals("Placa [placa]", Bitacora.limpiar("Placa ABC-123"))
        assertEquals("VIN [vin]", Bitacora.limpiar("VIN 1HGCM82633A004352"))
        assertEquals("Usuario [correo] no existe", Bitacora.limpiar("Usuario ana@correo.com no existe"))
        assertEquals("Sin datos personales", Bitacora.limpiar("Sin datos personales"))
    }

    @Test
    fun soloSoatYSimilaresTienenVencimiento() {
        assertTrue(Catalogo.tieneVencimiento("SOAT"))
        assertFalse(Catalogo.tieneVencimiento("Cambio de Aceite"))
    }
}
