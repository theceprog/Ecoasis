package com.nu.ecoasis

import com.google.firebase.firestore.FieldPath
import kotlinx.coroutines.tasks.await

class SensorRepository {
    private val sensorCollection = FirestoreManager.db.collection("sensor_data")

    suspend fun getSensorById(sensorId: String): SensorData? {
        return try {
            sensorCollection.document(sensorId).get().await().toObject(SensorData::class.java)
        } catch (e: Exception) {
            null
        }
    }

    suspend fun getSensorOne(): SensorData? {
        return try {
            sensorCollection.document("1").get().await().toObject(SensorData::class.java)
        } catch (e: Exception) {
            null
        }
    }

    suspend fun getAllSensor(): List<SensorData> {
        return try {
            // If you want only document with ID "1" from all documents
            sensorCollection.whereEqualTo(FieldPath.documentId(), "1")
                .get()
                .await()
                .documents
                .mapNotNull { it.toObject(SensorData::class.java) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun getSensorOneRealTime(onUpdate: (SensorData?) -> Unit) {
        sensorCollection.document("1").addSnapshotListener { snapshot, error ->
            if (error != null) {
                onUpdate(null)
                return@addSnapshotListener
            }

            val sensorData = snapshot?.toObject(SensorData::class.java)
            onUpdate(sensorData)
        }
    }

    fun getSensorOneRealTimeQuery(onUpdate: (List<SensorData>) -> Unit) {
        sensorCollection.whereEqualTo(FieldPath.documentId(), "1")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    onUpdate(emptyList())
                    return@addSnapshotListener
                }

                val sensorData = snapshot?.documents?.mapNotNull { it.toObject(SensorData::class.java) } ?: emptyList()
                onUpdate(sensorData)
            }
    }
}