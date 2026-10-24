package com.example.controlvial

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth

class RegistroActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var etEmail: EditText
    private lateinit var etPassword: EditText
    private lateinit var etConfirmPassword: EditText
    private lateinit var btnRegistrar: Button
    private lateinit var tvIrLogin: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_registro)

        auth = FirebaseAuth.getInstance()
        conectarVistas()

        btnRegistrar.setOnClickListener {
            registrarUsuario()
        }

        tvIrLogin.setOnClickListener {
            finish()
        }
    }

    private fun conectarVistas() {
        etEmail = findViewById(R.id.etEmail)
        etPassword = findViewById(R.id.etPassword)
        etConfirmPassword = findViewById(R.id.etConfirmPassword)
        btnRegistrar = findViewById(R.id.btnRegistrar)
        tvIrLogin = findViewById(R.id.tvIrLogin)
    }

    private fun registrarUsuario() {
        val email = etEmail.text.toString().trim()
        val clave = etPassword.text.toString()
        val repetida = etConfirmPassword.text.toString()

        if (email.isEmpty() || clave.isEmpty() || repetida.isEmpty()) {
            Toast.makeText(this, "Completa todos los campos.", Toast.LENGTH_SHORT).show()
            return
        }

        if (clave.length < 6) {
            Toast.makeText(this, "La contraseña debe tener al menos 6 caracteres.", Toast.LENGTH_LONG).show()
            return
        }

        if (clave != repetida) {
            Toast.makeText(this, "Las contraseñas no coinciden.", Toast.LENGTH_SHORT).show()
            return
        }

        auth.createUserWithEmailAndPassword(email, clave)
            .addOnCompleteListener(this) { tarea ->
                if (tarea.isSuccessful) {
                    Toast.makeText(this, "Cuenta creada correctamente.", Toast.LENGTH_SHORT).show()
                    startActivity(Intent(this, MainActivity::class.java))
                    finish()
                } else {
                    Toast.makeText(
                        this,
                        tarea.exception?.localizedMessage ?: "No se pudo crear la cuenta.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
    }
}