package com.nu.ecoasis

import com.google.firebase.firestore.FieldPath
import kotlinx.coroutines.tasks.await

class SensorRepository {
    private val sensorCollection = FirestoreManager.db.collection("ecoasis")
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

}