package com.example.data

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class ReceiptRepository {
    private val db = FirebaseFirestore.getInstance()
    private val receiptsCollection = db.collection("receipts")

    val allReceipts: Flow<List<Receipt>> = callbackFlow {
        val subscription = receiptsCollection
            .orderBy("date", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val receipts = snapshot.documents.mapNotNull { doc ->
                        val receipt = doc.toObject(Receipt::class.java)
                        receipt?.copy(id = doc.id)
                    }
                    trySend(receipts)
                }
            }
        
        awaitClose { subscription.remove() }
    }

    suspend fun insert(receipt: Receipt): String {
        return try {
            val documentRef = receiptsCollection.add(receipt).await()
            documentRef.id
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        }
    }

    suspend fun update(receipt: Receipt) {
        if (receipt.id.isNotBlank()) {
            try {
                receiptsCollection.document(receipt.id).set(receipt).await()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    suspend fun delete(receipt: Receipt) {
        deleteById(receipt.id)
    }

    suspend fun getReceiptById(id: String): Receipt? {
        return try {
            val doc = receiptsCollection.document(id).get().await()
            val receipt = doc.toObject(Receipt::class.java)
            receipt?.copy(id = doc.id)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun deleteById(id: String) {
        if (id.isNotBlank()) {
            try {
                receiptsCollection.document(id).delete().await()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
