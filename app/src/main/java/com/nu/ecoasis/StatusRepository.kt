package com.nu.ecoasis

import com.google.firebase.firestore.FieldPath
import kotlinx.coroutines.tasks.await

class StatusRepository {
    private val statusCollection = FirestoreManager.db.collection("ecoasis")
    suspend fun getStatusOne(): StatusData? {
        return try {
            statusCollection.document("status").get().await().toObject(StatusData::class.java)
        } catch (e: Exception) {
            null
        }
    }

    fun getStatusOneRealTime(onUpdate: (StatusData?) -> Unit) {
        statusCollection.document("status").addSnapshotListener { snapshot, error ->
            if (error != null) {
                onUpdate(null)
                return@addSnapshotListener
            }

            val statusData = snapshot?.toObject(StatusData::class.java)
            onUpdate(statusData)
        }
    }

}