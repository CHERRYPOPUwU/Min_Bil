package com.example.controlvial

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth

class LoginActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var etEmail: EditText
    private lateinit var etPassword: EditText
    private lateinit var btnLogin: Button
    private lateinit var tvIrRegistro: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        auth = FirebaseAuth.getInstance()

        // Si ya ha iniciado sesión, pasa directo al menú principal
        if (auth.currentUser != null) {
            abrirControlVial()
            return
        }

        setContentView(R.layout.activity_login)

        conectarVistas()

        btnLogin.setOnClickListener {
            iniciarSesion()
        }

        tvIrRegistro.setOnClickListener {
            startActivity(Intent(this, RegistroActivity::class.java))
        }
    }

    private fun conectarVistas() {
        etEmail = findViewById(R.id.etEmail)
        etPassword = findViewById(R.id.etPassword)
        btnLogin = findViewById(R.id.btnLogin)
        tvIrRegistro = findViewById(R.id.tvIrRegistro)
    }

    private fun iniciarSesion() {
        val email = etEmail.text.toString().trim()
        val clave = etPassword.text.toString()

        if (email.isEmpty() || clave.isEmpty()) {
            Toast.makeText(this, "Por favor, ingresa tu correo y contraseña.", Toast.LENGTH_SHORT).show()
            return
        }

        auth.signInWithEmailAndPassword(email, clave)
            .addOnCompleteListener(this) { tarea ->
                if (tarea.isSuccessful) {
                    abrirControlVial()
                } else {
                    Toast.makeText(
                        this,
                        tarea.exception?.localizedMessage ?: "No se pudo iniciar sesión. Verifica tus datos.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
    }

    private fun abrirControlVial() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}