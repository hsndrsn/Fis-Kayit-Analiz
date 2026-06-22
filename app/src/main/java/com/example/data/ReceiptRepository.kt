package com.example.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.tasks.await

class ReceiptRepository {
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    
    private val userId: String
        get() = auth.currentUser?.uid ?: ""

    private val receiptsCollection: CollectionReference?
        get() {
            val uid = userId
            return if (uid.isNotBlank()) {
                db.collection("users").document(uid).collection("receipts")
            } else {
                null
            }
        }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val allReceipts: Flow<List<Receipt>> = callbackFlow {
        // First we register auth listener to restart the snapshot when user logs in/out
        val authListener = FirebaseAuth.AuthStateListener {
            trySend(Unit) // trigger update
        }
        auth.addAuthStateListener(authListener)
        trySend(Unit) // initial fire
        awaitClose { auth.removeAuthStateListener(authListener) }
    }.flatMapLatest {
        callbackFlow {
            val collection = receiptsCollection
            if (collection == null) {
                trySend(emptyList())
                close()
                return@callbackFlow
            }
            val subscription = collection
                .orderBy("date", Query.Direction.DESCENDING)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
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
    }

    suspend fun insert(receipt: Receipt): String {
        return try {
            val documentRef = receiptsCollection?.add(receipt)?.await()
            documentRef?.id ?: ""
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        }
    }

    suspend fun update(receipt: Receipt) {
        if (receipt.id.isNotBlank()) {
            try {
                receiptsCollection?.document(receipt.id)?.set(receipt)?.await()
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
            val doc = receiptsCollection?.document(id)?.get()?.await()
            val receipt = doc?.toObject(Receipt::class.java)
            receipt?.copy(id = doc?.id ?: "")
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun deleteById(id: String) {
        if (id.isNotBlank()) {
            try {
                receiptsCollection?.document(id)?.delete()?.await()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
