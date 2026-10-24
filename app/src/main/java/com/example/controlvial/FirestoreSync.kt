
package com.example.controlvial

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class FirestoreSync {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private fun idSeguro(texto: String): String {
        return texto.trim()
            .uppercase()
            .replace(Regex("[^A-Z0-9_-]"), "_")
    }

    private fun usuarioActual(
        alError: (String) -> Unit
    ): String? {
        val uid = auth.currentUser?.uid

        if (uid == null) {
            alError("No hay una sesión iniciada.")
        }

        return uid
    }

    fun guardarVehiculo(
        placa: String,
        datos: Map<String, Any>,
        alTerminar: (Boolean, String?) -> Unit
    ) {
        val uid = usuarioActual {
            alTerminar(false, it)
        } ?: return

        val id = idSeguro(placa)

        if (id.isBlank()) {
            alTerminar(false, "La placa está vacía.")
            return
        }

        db.collection("usuarios")
            .document(uid)
            .collection("vehiculos")
            .document(id)
            .set(datos)
            .addOnSuccessListener {
                alTerminar(true, null)
            }
            .addOnFailureListener { error ->
                alTerminar(
                    false,
                    error.localizedMessage
                        ?: "No se pudo guardar el vehículo en Firestore."
                )
            }
    }

    fun guardarDocumento(
        placa: String,
        tipo: String,
        fecha: String,
        datos: Map<String, Any>,
        alTerminar: (Boolean, String?) -> Unit
    ) {
        val uid = usuarioActual {
            alTerminar(false, it)
        } ?: return

        val id = idSeguro("${placa}_${tipo}_${fecha}")

        if (id.isBlank()) {
            alTerminar(false, "El identificador está vacío.")
            return
        }

        db.collection("usuarios")
            .document(uid)
            .collection("documentos")
            .document(id)
            .set(datos)
            .addOnSuccessListener {
                alTerminar(true, null)
            }
            .addOnFailureListener { error ->
                alTerminar(
                    false,
                    error.localizedMessage
                        ?: "No se pudo guardar el documento en Firestore."
                )
            }
    }

    fun eliminarVehiculo(
        placa: String,
        alTerminar: (Boolean, String?) -> Unit
    ) {
        val uid = usuarioActual {
            alTerminar(false, it)
        } ?: return

        val usuario = db.collection("usuarios").document(uid)

        usuario.collection("documentos")
            .whereEqualTo("placa", placa)
            .get()
            .addOnSuccessListener { resultado ->

                val lote = db.batch()

                resultado.documents.forEach { documento ->
                    lote.delete(documento.reference)
                }

                lote.delete(
                    usuario.collection("vehiculos")
                        .document(idSeguro(placa))
                )

                lote.commit()
                    .addOnSuccessListener {
                        alTerminar(true, null)
                    }
                    .addOnFailureListener { error ->
                        alTerminar(
                            false,
                            error.localizedMessage
                                ?: "No se pudo eliminar el vehículo en Firestore."
                        )
                    }
            }
            .addOnFailureListener { error ->
                alTerminar(
                    false,
                    error.localizedMessage
                        ?: "No se pudieron consultar los documentos."
                )
            }
    }

    fun eliminarDocumento(
        placa: String,
        tipo: String,
        fecha: String,
        alTerminar: (Boolean, String?) -> Unit
    ) {
        val uid = usuarioActual {
            alTerminar(false, it)
        } ?: return

        val id = idSeguro("${placa}_${tipo}_${fecha}")

        db.collection("usuarios")
            .document(uid)
            .collection("documentos")
            .document(id)
            .delete()
            .addOnSuccessListener {
                alTerminar(true, null)
            }
            .addOnFailureListener { error ->
                alTerminar(
                    false,
                    error.localizedMessage
                        ?: "No se pudo eliminar el documento en Firestore."
                )
            }
    }
}
