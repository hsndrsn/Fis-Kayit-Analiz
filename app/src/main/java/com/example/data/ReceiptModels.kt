package com.example.data

data class Receipt(
    val id: String = "",
    val storeName: String = "",
    val date: Long = 0L,
    val totalAmount: Double = 0.0,
    val location: String = "",
    val items: List<ReceiptItem> = emptyList(),
    val imageUrl: String? = null
)

data class ReceiptItem(
    val name: String = "",
    val quantity: Double = 0.0,
    val unitPrice: Double = 0.0,
    val totalPrice: Double = 0.0,
    val category: String = ""
)
