package com.example.data

import kotlinx.coroutines.flow.Flow

class ReceiptRepository(private val receiptDao: ReceiptDao) {
    val allReceipts: Flow<List<Receipt>> = receiptDao.getAllReceipts()

    suspend fun insert(receipt: Receipt): Long = receiptDao.insertReceipt(receipt)

    suspend fun update(receipt: Receipt) = receiptDao.updateReceipt(receipt)

    suspend fun delete(receipt: Receipt) = receiptDao.deleteReceipt(receipt)

    suspend fun getReceiptById(id: Long): Receipt? = receiptDao.getReceiptById(id)

    suspend fun deleteById(id: Long) = receiptDao.deleteReceiptById(id)
}
