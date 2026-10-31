package com.example.controlvial

import android.os.Build
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onData
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.closeSoftKeyboard
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.action.ViewActions.typeText
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers.isPlatformPopup
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.firebase.auth.FirebaseAuth
import org.hamcrest.CoreMatchers.allOf
import org.hamcrest.CoreMatchers.equalTo
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * RNF-14: prueba de interfaz del flujo "registrar vehículo y registrar un mantenimiento".
 *
 * IMPORTANTE:
 *  - Antes de ejecutarla, inicia sesión una vez en el emulador (la prueba usa esa sesión).
 *  - La prueba BORRA los datos locales guardados en ESE emulador (no toca Firestore).
 */
@RunWith(AndroidJUnit4::class)
class FlujoRegistroTest {

    @Before
    fun preparar() {
        val instrumentacion = InstrumentationRegistry.getInstrumentation()
        val contexto = instrumentacion.targetContext

        // Si no hay una sesión iniciada, la prueba se omite en lugar de fallar.
        assumeTrue(
            "Inicia sesión una vez en el emulador antes de ejecutar esta prueba",
            FirebaseAuth.getInstance().currentUser != null
        )

        // Evita que el aviso de permiso de notificaciones tape la pantalla.
        if (Build.VERSION.SDK_INT >= 33) {
            instrumentacion.uiAutomation.grantRuntimePermission(
                contexto.packageName,
                "android.permission.POST_NOTIFICATIONS"
            )
        }

        RepositorioMiCarro(contexto).preferenciasActuales().edit().clear().commit()
    }

    @Test
    fun registrarVehiculoYUnMantenimiento() {
        ActivityScenario.launch(MainActivity::class.java).use {
            // 1) Registrar un vehículo
            onView(withId(R.id.nav_vehiculos)).perform(click())
            onView(withId(R.id.btnNuevoVehiculo)).perform(click())
            onView(withId(R.id.etPlaca)).perform(scrollTo(), typeText("TST123"), closeSoftKeyboard())
            onView(withId(R.id.etKilometraje))
                .perform(scrollTo(), typeText("12000"), closeSoftKeyboard())
            onView(withId(R.id.btnGuardarVehiculo)).perform(scrollTo(), click())

            onView(allOf(withId(R.id.tvVehPlaca), withText("TST123"))).check(matches(isDisplayed()))

            // 2) Registrar un mantenimiento para ese vehículo
            onView(withId(R.id.nav_historial)).perform(click())
            onView(withId(R.id.btnNuevoRegistro)).perform(click())

            onView(withId(R.id.acTipoRegistro)).perform(scrollTo(), click())
            onData(equalTo("Cambio de Aceite")).inRoot(isPlatformPopup()).perform(click())

            onView(withId(R.id.etDescripcionReg))
                .perform(scrollTo(), typeText("Cambio de aceite y filtro"), closeSoftKeyboard())

            val hoy = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date())
            onView(withId(R.id.etFecha)).perform(scrollTo(), replaceText(hoy))

            onView(withId(R.id.etKm)).perform(scrollTo(), typeText("12500"), closeSoftKeyboard())
            onView(withId(R.id.btnGuardarRegistro)).perform(scrollTo(), click())

            // 3) El mantenimiento aparece en el historial
            onView(allOf(withId(R.id.tvRegTitulo), withText("Cambio de Aceite · TST123")))
                .check(matches(isDisplayed()))
        }
    }
}
