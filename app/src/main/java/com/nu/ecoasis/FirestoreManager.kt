package com.nu.ecoasis

import android.util.Patterns
import com.google.firebase.Firebase
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.firestore
import com.google.firebase.firestore.firestoreSettings
import java.util.Date

data class PlantPreset(
    val name: String = "",
    val minPH: Double = 0.0,
    val maxPH: Double = 0.0,
    val minPPM: Int = 0,
    val maxPPM: Int = 0
) {
    constructor() : this("", 0.0, 0.0, 0, 0)
}

object FirestoreManager {

    val db: FirebaseFirestore by lazy {
        val firestore = Firebase.firestore
        // Configure settings only once when Firestore is first accessed
        val settings = firestoreSettings {
            isPersistenceEnabled = true
            cacheSizeBytes = FirebaseFirestoreSettings.CACHE_SIZE_UNLIMITED
        }
        firestore.firestoreSettings = settings
        firestore
    }

    // Define actual possible ranges (absolute values)
    const val PH_ABS_MIN = 1
    const val PH_ABS_MAX = 14
    const val TDS_ABS_MIN = 0
    const val TDS_ABS_MAX = 1500

    init {
        val settings = firestoreSettings {
            isPersistenceEnabled = true
            cacheSizeBytes = FirebaseFirestoreSettings.CACHE_SIZE_UNLIMITED
        }
        db.firestoreSettings = settings
    }

    fun getRangeSettings(
        onSuccess: (phMin: Int, phMax: Int, tdsMin: Int, tdsMax: Int) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        val docRef = db.collection("ecoasis").document("settings")

        docRef.get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    // Get values from Firestore and clamp to absolute ranges
                    val phMin = clampPhValue(document.getLong("ph_min")?.toInt() ?: 1)
                    val phMax = clampPhValue(document.getLong("ph_max")?.toInt() ?: 14)
                    val tdsMin = clampTdsValue(document.getLong("tds_min")?.toInt() ?: 0)
                    val tdsMax = clampTdsValue(document.getLong("tds_max")?.toInt() ?: 1500)

                    onSuccess(phMin, phMax, tdsMin, tdsMax)
                } else {
                    // Return default values if document doesn't exist
                    onSuccess(1, 14, 0, 1500)
                }
            }
            .addOnFailureListener { exception ->
                onFailure(exception)
            }
    }

    fun listenForRangeSettings(
        onUpdate: (phMin: Int, phMax: Int, tdsMin: Int, tdsMax: Int) -> Unit,
        onError: (FirebaseFirestoreException) -> Unit
    ): ListenerRegistration {
        val docRef = db.collection("ecoasis").document("settings")

        return docRef.addSnapshotListener { snapshot, error ->
            if (error != null) {
                onError(error)
                return@addSnapshotListener
            }

            if (snapshot != null && snapshot.exists()) {
                // Clamp values to absolute ranges
                val phMin = clampPhValue(snapshot.getLong("ph_min")?.toInt() ?: 1)
                val phMax = clampPhValue(snapshot.getLong("ph_max")?.toInt() ?: 14)
                val tdsMin = clampTdsValue(snapshot.getLong("tds_min")?.toInt() ?: 0)
                val tdsMax = clampTdsValue(snapshot.getLong("tds_max")?.toInt() ?: 1500)

                onUpdate(phMin, phMax, tdsMin, tdsMax)
            } else {
                // Use default values if document doesn't exist
                onUpdate(1, 14, 0, 1500)
            }
        }
    }

    fun saveRangeSettings(
        phMin: Int,
        phMax: Int,
        tdsMin: Int,
        tdsMax: Int,
        onSuccess: () -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        // Clamp values to absolute ranges before saving
        val clampedPhMin = clampPhValue(phMin)
        val clampedPhMax = clampPhValue(phMax)
        val clampedTdsMin = clampTdsValue(tdsMin)
        val clampedTdsMax = clampTdsValue(tdsMax)

        // Validate ranges
        if (clampedPhMin >= clampedPhMax) {
            onFailure(IllegalArgumentException("pH Min must be less than pH Max"))
            return
        }

        if (clampedTdsMin >= clampedTdsMax) {
            onFailure(IllegalArgumentException("TDS Min must be less than TDS Max"))
            return
        }

        val settingsData = hashMapOf(
            "ph_min" to clampedPhMin,
            "ph_max" to clampedPhMax,
            "tds_min" to clampedTdsMin,
            "tds_max" to clampedTdsMax
        )

        db.collection("ecoasis").document("settings")
            .set(settingsData)
            .addOnSuccessListener {
                onSuccess()
            }
            .addOnFailureListener { exception ->
                onFailure(exception)
            }
    }

    // Helper functions to clamp values to absolute ranges
    private fun clampPhValue(value: Int): Int {
        return value.coerceIn(PH_ABS_MIN, PH_ABS_MAX)
    }

    private fun clampTdsValue(value: Int): Int {
        return value.coerceIn(TDS_ABS_MIN, TDS_ABS_MAX)
    }// Add this to your FirestoreManager.kt

    fun loginUser(
        email: String,
        password: String,
        onSuccess: (User) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        // Input validation
        if (email.isBlank() || !isValidEmail(email)) {
            onFailure(Exception("Please enter a valid email"))
            return
        }

        if (password.isBlank() || password.length < 6) {
            onFailure(Exception("Password must be at least 6 characters"))
            return
        }

        val docRef = db.collection("users").document(email)

        docRef.get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    val user = document.toObject(User::class.java)

                    if (user != null) {
                        // Verify password (in production, use proper password hashing)
                        if (user.password == password) {
                            onSuccess(user)
                        } else {
                            onFailure(Exception("Invalid password"))
                        }
                    } else {
                        onFailure(Exception("User data corrupted"))
                    }
                } else {
                    onFailure(Exception("User not found"))
                }
            }
            .addOnFailureListener { exception ->
                onFailure(exception)
            }
    }

    // Add User data class if you don't have it already
    data class User(
        val email: String = "",
        val password: String = "",
        val name: String = "",
        val phone: String = "",
        val gender: String = ""
    ) {
        // Empty constructor for Firestore
        constructor() : this("", "", "", "", "")
    }

    // Add email validation helper
    private fun isValidEmail(email: String): Boolean {
        val pattern = "[a-zA-Z0-9._-]+@[a-z]+\\.+[a-z]+".toRegex()
        return pattern.matches(email)
    }// Add this to your FirestoreManager.kt

    fun registerUser(
        email: String,
        firstName: String,
        lastName: String,
        username: String,
        password: String,
        onSuccess: () -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        // Input validation
        if (email.isBlank() || !isValidEmail(email)) {
            onFailure(Exception("Please enter a valid email"))
            return
        }

        if (firstName.isBlank()) {
            onFailure(Exception("First name is required"))
            return
        }

        if (lastName.isBlank()) {
            onFailure(Exception("Last name is required"))
            return
        }

        if (username.isBlank() || username.length < 3) {
            onFailure(Exception("Username must be at least 3 characters"))
            return
        }

        if (password.isBlank() || password.length < 6) {
            onFailure(Exception("Password must be at least 6 characters"))
            return
        }

        // Check if email already exists
        db.collection("users").document(email)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    onFailure(Exception("Email already registered"))
                } else {
                    // Check if username already exists
                    checkUsernameAvailability(
                        email,
                        firstName,
                        lastName,
                        username,
                        password,
                        onSuccess,
                        onFailure
                    )
                }
            }
            .addOnFailureListener { exception ->
                onFailure(exception)
            }
    }

    private fun checkUsernameAvailability(
        email: String,
        firstName: String,
        lastName: String,
        username: String,
        password: String,
        onSuccess: () -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        db.collection("users")
            .whereEqualTo("username", username)
            .get()
            .addOnSuccessListener { documents ->
                if (documents.isEmpty) {
                    // Username is available, create new user
                    createUser(email, firstName, lastName, username, password, onSuccess, onFailure)
                } else {
                    onFailure(Exception("Username already taken"))
                }
            }
            .addOnFailureListener { exception ->
                onFailure(exception)
            }
    }

    private fun createUser(
        email: String,
        firstName: String,
        lastName: String,
        username: String,
        password: String,
        onSuccess: () -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        val user = hashMapOf(
            "firstName" to firstName,
            "lastName" to lastName,
            "email" to email,
            "username" to username,
            "password" to password, // ⚠️ In production, hash this password!
            "createdAt" to Date(),
            "updatedAt" to Date()
        )

        db.collection("users").document(email)
            .set(user)
            .addOnSuccessListener {
                onSuccess()
            }
            .addOnFailureListener { exception ->
                onFailure(exception)
            }
    }
    // Update in FirestoreManager.kt
    fun getAllPlants(
        onSuccess: (List<Pair<String, PlantPreset>>) -> Unit, // Returns documentId + plant data
        onFailure: (Exception) -> Unit
    ) {
        db.collection("plants")
            .get()
            .addOnSuccessListener { documents ->
                val plants = mutableListOf<Pair<String, PlantPreset>>()
                for (document in documents) {
                    val plant = document.toObject(PlantPreset::class.java)
                    plants.add(Pair(document.id, plant))
                }
                onSuccess(plants)
            }
            .addOnFailureListener { exception ->
                onFailure(exception)
            }
    }

    // Update getPlantById method
    fun getPlantById(
        plantDocumentId: String,
        onSuccess: (PlantPreset) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        db.collection("plants").document(plantDocumentId)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val plant = document.toObject(PlantPreset::class.java)
                    onSuccess(plant ?: PlantPreset())
                } else {
                    onFailure(Exception("Plant not found"))
                }
            }
            .addOnFailureListener { exception ->
                onFailure(exception)
            }
    }

}