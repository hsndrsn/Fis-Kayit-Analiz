package com.example.data

import androidx.room.*
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

@Entity(tableName = "receipts")
data class Receipt(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val storeName: String,
    val date: Long, // timestamp in ms
    val totalAmount: Double,
    val location: String,
    val items: List<ReceiptItem>
)

data class ReceiptItem(
    val name: String,
    val quantity: Double,
    val unitPrice: Double,
    val totalPrice: Double,
    val category: String // "Gıda", "İçecek", "Temizlik", "Kişisel Bakım", "Giyim", "Elektronik", "Ulaşım", "Diğer"
)

class Converters {
    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val listType = Types.newParameterizedType(List::class.java, ReceiptItem::class.java)
    private val adapter = moshi.adapter<List<ReceiptItem>>(listType)

    @TypeConverter
    fun fromItemsList(value: List<ReceiptItem>?): String {
        return adapter.toJson(value ?: emptyList())
    }

    @TypeConverter
    fun toItemsList(value: String?): List<ReceiptItem> {
        if (value.isNullOrEmpty()) return emptyList()
        return try {
            adapter.fromJson(value) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }
}
