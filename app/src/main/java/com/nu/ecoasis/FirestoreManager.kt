package com.nu.ecoasis

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.firestoreSettings
import kotlinx.coroutines.tasks.await
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Date

private fun Double.roundToDecimalPlace(decimalPlaces: Int): Double {
    if (this.isNaN() || this.isInfinite()) {
        return this
    }
    return BigDecimal(this).setScale(decimalPlaces, RoundingMode.HALF_UP).toDouble()
}

data class PlantPreset(
    val name: String = "",
    val minPH: Double = 0.0,
    val maxPH: Double = 0.0,
    val minPPM: Int = 0,
    val maxPPM: Int = 0
) {
    constructor() : this("", 0.0, 0.0, 0, 0)
}

class FirestoreManager {

    val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    // Define actual possible ranges (absolute values)
    private val sensorCollection by lazy { db.collection("ecoasis") }
    val PH_ABS_MIN = 1.0
    val PH_ABS_MAX = 14.0
    val TDS_ABS_MIN = 0
    val TDS_ABS_MAX = 1500

    private val statusCollection by lazy { db.collection("ecoasis") }

    data class StatusReading(
        val status: StatusFields = StatusFields()
    ) {
        constructor() : this(StatusFields())

        data class StatusFields(
            val ph: Int = 0,
            val tds: Int = 0
        ) {
            constructor() : this(0, 0)
        }
    }

    suspend fun getStatusReading(): StatusReading? {
        return try {
            db.collection("ecoasis").document("readings").get().await()
                .toObject(StatusReading::class.java)
        } catch (e: Exception) {
            null
        }
    }

    fun getStatusReadingRealTime(onUpdate: (StatusReading?) -> Unit) {
        db.collection("ecoasis").document("readings").addSnapshotListener { snapshot, error ->
            if (error != null) {
                onUpdate(null)
                return@addSnapshotListener
            }
            val statusReading = snapshot?.toObject(StatusReading::class.java)
            onUpdate(statusReading)
        }
    }

    fun getPumpStatusRealTime(onUpdate: (StatusData?) -> Unit) {
        statusCollection.document("status").addSnapshotListener { snapshot, error ->
            if (error != null) {
                onUpdate(null)
                return@addSnapshotListener
            }

            val statusData = snapshot?.toObject(StatusData::class.java)
            onUpdate(statusData)
        }
    }

    // Update getPumpStatus method as well
    suspend fun getPumpStatus(): StatusData? {
        return try {
            statusCollection.document("status").get().await().toObject(StatusData::class.java)
        } catch (e: Exception) {
            null
        }
    }

    fun getRangeSettings(
        onSuccess: (phMin: Double, phMax: Double, tdsMin: Int, tdsMax: Int) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        val docRef = db.collection("ecoasis").document("settings")

        docRef.get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    val phMin = clampPhValue(document.getDouble("ph_min") ?: PH_ABS_MIN)
                    val phMax = clampPhValue(document.getDouble("ph_max") ?: PH_ABS_MAX)
                    val tdsMin = clampTdsValue(document.getLong("tds_min")?.toInt() ?: TDS_ABS_MIN)
                    val tdsMax = clampTdsValue(document.getLong("tds_max")?.toInt() ?: TDS_ABS_MAX)

                    onSuccess(phMin, phMax, tdsMin, tdsMax)
                } else {
                    onSuccess(PH_ABS_MIN, PH_ABS_MAX, TDS_ABS_MIN, TDS_ABS_MAX) // Default Doubles
                }
            }
            .addOnFailureListener { exception ->
                onFailure(exception)
            }
    }

    fun listenForRangeSettings(
        onUpdate: (phMin: Double, phMax: Double, tdsMin: Int, tdsMax: Int) -> Unit,
        onError: (FirebaseFirestoreException) -> Unit
    ): ListenerRegistration {
        val docRef = db.collection("ecoasis").document("settings")

        return docRef.addSnapshotListener { snapshot, error ->
            if (error != null) {
                onError(error)
                return@addSnapshotListener
            }

            if (snapshot != null && snapshot.exists()) {
                val phMin = clampPhValue(snapshot.getDouble("ph_min") ?: PH_ABS_MIN)
                val phMax = clampPhValue(snapshot.getDouble("ph_max") ?: PH_ABS_MAX)
                val tdsMin = clampTdsValue(snapshot.getLong("tds_min")?.toInt() ?: TDS_ABS_MIN)
                val tdsMax = clampTdsValue(snapshot.getLong("tds_max")?.toInt() ?: TDS_ABS_MAX)
                onUpdate(phMin, phMax, tdsMin, tdsMax)
            } else {
                onUpdate(PH_ABS_MIN, PH_ABS_MAX, TDS_ABS_MIN, TDS_ABS_MAX) // Default Doubles
            }
        }
    }

    fun saveRangeSettings(
        phMin: Double,
        phMax: Double,
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
    private fun clampPhValue(value: Double): Double {
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

        // Sign in with Firebase Auth
        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    // Get user data from Firestore after successful authentication
                    val user = auth.currentUser
                    if (user != null) {
                        getUserDataFromFirestore(user.uid, onSuccess, onFailure)
                    } else {
                        onFailure(Exception("User not found"))
                    }
                } else {
                    onFailure(task.exception ?: Exception("Authentication failed"))
                }
            }
    }

    // Register with Firebase Authentication
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

        // Check if username already exists
        checkUsernameAvailability(username) { usernameAvailable ->
            if (usernameAvailable) {
                // Create user with Firebase Auth
                auth.createUserWithEmailAndPassword(email, password)
                    .addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            val firebaseUser = auth.currentUser
                            if (firebaseUser != null) {
                                // Update user profile with display name
                                val profileUpdates = UserProfileChangeRequest.Builder()
                                    .setDisplayName("$firstName $lastName")
                                    .build()

                                firebaseUser.updateProfile(profileUpdates)
                                    .addOnCompleteListener { profileTask ->
                                        if (profileTask.isSuccessful) {
                                            // Save additional user data to Firestore
                                            saveUserDataToFirestore(
                                                firebaseUser.uid,
                                                email,
                                                firstName,
                                                lastName,
                                                username,
                                                onSuccess,
                                                onFailure
                                            )
                                        } else {
                                            onFailure(
                                                profileTask.exception
                                                    ?: Exception("Profile update failed")
                                            )
                                        }
                                    }
                            } else {
                                onFailure(Exception("User creation failed"))
                            }
                        } else {
                            onFailure(task.exception ?: Exception("Registration failed"))
                        }
                    }
            } else {
                onFailure(Exception("Username already taken"))
            }
        }
    }

    private fun checkUsernameAvailability(username: String, callback: (Boolean) -> Unit) {
        db.collection("users")
            .whereEqualTo("username", username)
            .get()
            .addOnSuccessListener { documents ->
                callback(documents.isEmpty)
            }
            .addOnFailureListener {
                callback(true) // Assume available if check fails
            }
    }

    private fun saveUserDataToFirestore(
        uid: String,
        email: String,
        firstName: String,
        lastName: String,
        username: String,
        onSuccess: () -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        val user = hashMapOf(
            "uid" to uid,
            "firstName" to firstName,
            "lastName" to lastName,
            "email" to email,
            "username" to username,
            "createdAt" to Date(),
            "updatedAt" to Date()
        )

        db.collection("users").document(uid)
            .set(user)
            .addOnSuccessListener {
                onSuccess()
            }
            .addOnFailureListener { exception ->
                onFailure(exception)
            }
    }

    private fun getUserDataFromFirestore(
        uid: String,
        onSuccess: (User) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        db.collection("users").document(uid)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val user = document.toObject(User::class.java)
                    if (user != null) {
                        onSuccess(user)
                    } else {
                        onFailure(Exception("User data not found"))
                    }
                } else {
                    onFailure(Exception("User document not found"))
                }
            }
            .addOnFailureListener { exception ->
                onFailure(exception)
            }
    }

    // Check if user is logged in
    fun isUserLoggedIn(): Boolean {
        return auth.currentUser != null
    }

    // Get current user
    fun getCurrentUser(): com.google.firebase.auth.FirebaseUser? {
        return auth.currentUser
    }

    // Sign out
    fun signOut() {
        auth.signOut()
    }

    // Password reset
    fun sendPasswordResetEmail(
        email: String,
        onSuccess: () -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        if (email.isBlank() || !isValidEmail(email)) {
            onFailure(Exception("Please enter a valid email"))
            return
        }

        auth.sendPasswordResetEmail(email)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    onSuccess()
                } else {
                    onFailure(task.exception ?: Exception("Password reset failed"))
                }
            }
    }


    // Sensor data methods (migrated from SensorRepository)
    suspend fun getSensorOne(): SensorData? {
        return try {
            sensorCollection.document("readings").get().await().toObject(SensorData::class.java)
        } catch (e: Exception) {
            null
        }
    }

    fun getSensorOneRealTime(onUpdate: (SensorData?) -> Unit) {
        sensorCollection.document("readings").addSnapshotListener { snapshot, error ->
            if (error != null) {
                onUpdate(null)
                return@addSnapshotListener
            }

            val sensorData = snapshot?.toObject(SensorData::class.java)
            onUpdate(sensorData)
        }
    }

    data class SensorData(
        val air: Double = 0.0,
        val h2o: Double = 0.0,
        val humid: Double = 0.0,
        val lux: Double = 0.0,
        val ph: Double = 0.0,
        val ppm: Int = 0,
        val up: Double = 0.0,
        val down: Double = 0.0,
        val a: Double = 0.0,
        val b: Double = 0.0,
    ) {
        constructor() : this(0.0, 0.0, 0.0, 0.0, 0.0, 0, 0.0, 0.0, 0.0, 0.0)
    }

    // Status data class
    data class StatusData(
        val pump_a: Boolean = false,
        val pump_b: Boolean = false,
        val pump_ph_up: Boolean = false,
        val pump_ph_down: Boolean = false
    ) {
        constructor() : this(false, false, false, false)
    }


    data class User(
        val uid: String = "",
        val email: String = "",
        val firstName: String = "",
        val lastName: String = "",
        val username: String = "",
        val createdAt: Date = Date(),
        val updatedAt: Date = Date()
    ) {
        constructor() : this("", "", "", "", "", Date(), Date())
    }

    // Email validation helper
    private fun isValidEmail(email: String): Boolean {
        val pattern = "[a-zA-Z0-9._-]+@[a-z]+\\.+[a-z]+".toRegex()
        return pattern.matches(email)
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