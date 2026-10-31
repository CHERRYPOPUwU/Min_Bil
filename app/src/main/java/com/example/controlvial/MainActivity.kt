package com.example.controlvial

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth

/**
 * Pantalla contenedora: muestra un fragmento (Inicio, Vehículos, Historial o Alertas)
 * y una barra de navegación inferior para cambiar entre ellos.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var barraNavegacion: BottomNavigationView

    private val pedirPermisoNotificaciones =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        // La app usa un tema claro: iconos oscuros en las barras del sistema.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        )
        super.onCreate(savedInstanceState)

        // Sin sesión iniciada (por ejemplo, al tocar una notificación) se vuelve al inicio de sesión.
        if (FirebaseAuth.getInstance().currentUser == null) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        setContentView(R.layout.activity_main)

        // Deja espacio para la barra de estado (arriba). La barra inferior gestiona el suyo.
        val raiz = findViewById<android.view.View>(R.id.raizMain)
        ViewCompat.setOnApplyWindowInsetsListener(raiz) { vista, insets ->
            val barras = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            vista.setPadding(barras.left, barras.top, barras.right, 0)
            insets
        }

        // Si hay documentos guardados de versiones antiguas sin vehículo, se recuperan.
        val repositorio = RepositorioMiCarro(this)
        Formato.codigoMoneda = repositorio.obtenerMoneda()
        repositorio.recuperarVehiculosAntiguos()
        repositorio.reintentarPendientes()

        barraNavegacion = findViewById(R.id.barraNavegacion)
        barraNavegacion.setOnItemSelectedListener { item ->
            val fragmento: Fragment? = when (item.itemId) {
                R.id.nav_inicio -> InicioFragment()
                R.id.nav_vehiculos -> VehiculosFragment()
                R.id.nav_plan -> PlanFragment()
                R.id.nav_historial -> HistorialContenedorFragment()
                R.id.nav_alertas -> AlertasFragment()
                else -> null
            }
            if (fragmento == null) {
                false
            } else {
                supportFragmentManager.beginTransaction()
                    .replace(R.id.contenedorFragmentos, fragmento)
                    .commit()
                true
            }
        }

        if (savedInstanceState == null) {
            // RF-30: al tocar una notificación se abre directamente la pestaña de alertas.
            barraNavegacion.selectedItemId =
                if (intent.getStringExtra(EXTRA_PESTANA) == PESTANA_ALERTAS) R.id.nav_alertas
                else R.id.nav_inicio
        }

        solicitarPermisoNotificaciones()
        ProgramadorRecordatorios.programar(this)
    }

    companion object {
        const val EXTRA_PESTANA = "pestana"
        const val PESTANA_ALERTAS = "alertas"
    }

    /** Permite a un fragmento cambiar de pestaña (por ejemplo, "Ver todas las alertas"). */
    fun irA(idPestana: Int) {
        barraNavegacion.selectedItemId = idPestana
    }

    /**
     * RNF-08: el permiso de notificaciones se pide solo si hace falta, y antes se explica
     * para qué se usa. Si el usuario dice "Ahora no", no se vuelve a insistir.
     */
    private fun solicitarPermisoNotificaciones() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        ) return

        val ajustes = getSharedPreferences("ajustes_app", MODE_PRIVATE)
        if (ajustes.getBoolean("permiso_notificaciones_explicado", false)) return

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.permiso_notif_titulo)
            .setMessage(R.string.permiso_notif_mensaje)
            .setCancelable(false)
            .setNegativeButton(R.string.ahora_no) { _, _ ->
                ajustes.edit().putBoolean("permiso_notificaciones_explicado", true).apply()
            }
            .setPositiveButton(R.string.continuar) { _, _ ->
                ajustes.edit().putBoolean("permiso_notificaciones_explicado", true).apply()
                pedirPermisoNotificaciones.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            .show()
    }
}
