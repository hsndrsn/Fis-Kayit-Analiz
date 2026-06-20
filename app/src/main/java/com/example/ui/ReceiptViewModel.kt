package com.example.ui

import android.app.Application
import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.api.*
import com.example.data.*
import com.squareup.moshi.JsonAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.*

sealed interface GeminiOcrState {
    object Idle : GeminiOcrState
    object Loading : GeminiOcrState
    data class Success(val parsedReceipt: ParsedReceiptResponse) : GeminiOcrState
    data class Error(val message: String) : GeminiOcrState
}

class ReceiptViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val repository = ReceiptRepository(db.receiptDao())

    // UI visible receipts
    val allReceipts: StateFlow<List<Receipt>> = repository.allReceipts
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _ocrState = MutableStateFlow<GeminiOcrState>(GeminiOcrState.Idle)
    val ocrState: StateFlow<GeminiOcrState> = _ocrState.asStateFlow()

    // Temporary storage of the bitmap being processed (for UI displaying reference)
    private val _capturedPhoto = MutableStateFlow<Bitmap?>(null)
    val capturedPhoto: StateFlow<Bitmap?> = _capturedPhoto.asStateFlow()

    fun setCapturedPhoto(bitmap: Bitmap?) {
        _capturedPhoto.value = bitmap
    }

    fun resetOcrState() {
        _ocrState.value = GeminiOcrState.Idle
    }

    fun deleteReceipt(id: Long) {
        viewModelScope.launch {
            repository.deleteById(id)
        }
    }

    fun saveReceipt(
        storeName: String,
        dateStr: String,
        totalAmount: Double,
        location: String,
        items: List<ReceiptItem>
    ) {
        viewModelScope.launch {
            val timestamp = parseTurkishDate(dateStr)
            val receipt = Receipt(
                storeName = storeName.ifBlank { "Bilinmeyen Mağaza" },
                date = timestamp,
                totalAmount = totalAmount,
                location = location,
                items = items
            )
            repository.insert(receipt)
        }
    }

    fun analyzeReceipt(bitmap: Bitmap) {
        setCapturedPhoto(bitmap)
        _ocrState.value = GeminiOcrState.Loading

        viewModelScope.launch(Dispatchers.IO) {
            val apiKey = BuildConfig.GEMINI_API_KEY
            if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
                _ocrState.value = GeminiOcrState.Error(
                    "Lütfen Google AI Studio Secrets sekmesinden 'GEMINI_API_KEY' anahtarınızı tanımlayın."
                )
                return@launch
            }

            try {
                // Compress Bitmap
                val base64Image = compressBitmapToBase64(bitmap)

                val prompt = """
                    Alışveriş fişi/faturası görselindeki bilgileri son derece hassas bir şekilde çözümle ve sadece belirtilen JSON formatında çıktı ver. JSON dışı hiçbir açıklama veya metin yazma.
                    
                    Önemli Kurallar:
                    1. Türkçe dilinde çıktı üret.
                    2. Ürün kategorilerini şu kelimelerden biri olarak ata: "Gıda", "İçecek", "Temizlik", "Kişisel Bakım", "Giyim", "Elektronik", "Ulaşım", "Diğer". Başka hiçbir kategori uydurma.
                    3. Alışveriş tarihi dd.MM.yyyy (ör. 19.06.2026) şeklinde olsun. Eğer yıl taranamadıysa 2026 olarak varsay.
                    4. Görüntüdeki silik, küçük veya kaymış yazılarda karakterleri doğru tahmin etmek için bağlamdan yararlan. Sıklıkla hata yapılan şu karakter çiftlerine özellikle dikkat et: '9' ile '8', '5' ile '6', 'F' ile 'R', 'O' ile '0' (Sıfır), '1' ile '7'.
                    5. Sayısal Tutarlılık ve Sağlama Kuralı: Taranan fiyatları matematiksel olarak doğrula. Her kalem için "Miktar (quantity) * Birim Fiyat (unitPrice) = Toplam Fiyat (totalPrice)" denklemlerini kontrol et. Eğer görseldeki rakamlar siliklik nedeniyle uyuşmuyorsa, en mantıklı rakam tashihi ile matematiksel uyuşumu sağla. Ayrıca tüm kalemlerin "totalPrice" toplamının, ana "totalAmount" (Genel Toplam) değerini teyit ettiğinden emin ol.
                    6. Çıktı biçimi tam olarak şu JSON şemasında olmalıdır:
                    {
                      "storeName": "Mağaza veya İşyeri Adı",
                      "date": "gg.aa.yyyy",
                      "time": "ss:dk",
                      "items": [
                        {
                          "name": "Ürün Adı",
                          "quantity": 1.0,
                          "unitPrice": 10.0,
                          "totalPrice": 10.0,
                          "category": "Kategori"
                        }
                      ],
                      "totalAmount": 10.0,
                      "location": "Şube/Konum Bilgisi (Yoksa boş bırak)"
                    }
                """.trimIndent()

                val request = GenerateContentRequest(
                    contents = listOf(
                        Content(
                            parts = listOf(
                                Part(text = prompt),
                                Part(inlineData = InlineData(mimeType = "image/jpeg", data = base64Image))
                            )
                        )
                    ),
                    generationConfig = GenerationConfig(
                        responseMimeType = "application/json",
                        temperature = 0.2f
                    )
                )

                val response = RetrofitClient.service.generateContent(apiKey, request)
                val rawText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text

                if (rawText != null) {
                    Log.d("ReceiptOCR", "Raw Response: $rawText")
                    val cleanJson = cleanJsonResponse(rawText)
                    val parsedResponse = parseJsonToReceipt(cleanJson)
                    if (parsedResponse != null) {
                        _ocrState.value = GeminiOcrState.Success(parsedResponse)
                    } else {
                        _ocrState.value = GeminiOcrState.Error("Fiş verileri işlenemedi. JSON okuma hatası.")
                    }
                } else {
                    _ocrState.value = GeminiOcrState.Error("Gemini API'den boş yanıt alındı.")
                }

            } catch (e: Exception) {
                Log.e("ReceiptOCR", "Ocr Error", e)
                _ocrState.value = GeminiOcrState.Error("Fiş analizi başarısız oldu: ${e.localizedMessage}")
            }
        }
    }

    private fun compressBitmapToBase64(bitmap: Bitmap): String {
        val outputStream = ByteArrayOutputStream()
        // Resize to maximum 1600x1600px to avoid huge payload while preserving fine text detail
        val resized = resizeBitmap(bitmap, 1600)
        resized.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)
        val bytes = outputStream.toByteArray()
        return android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
    }

    private fun resizeBitmap(image: Bitmap, maxSize: Int): Bitmap {
        var width = image.width
        var height = image.height

        val bitmapRatio = width.toFloat() / height.toFloat()
        if (bitmapRatio > 1) {
            width = maxSize
            height = (width / bitmapRatio).toInt()
        } else {
            height = maxSize
            width = (height * bitmapRatio).toInt()
        }
        return Bitmap.createScaledBitmap(image, width, height, true)
    }

    private fun cleanJsonResponse(rawText: String): String {
        var text = rawText.trim()
        if (text.startsWith("```json")) {
            text = text.substringAfter("```json").substringBeforeLast("```").trim()
        } else if (text.startsWith("```")) {
            text = text.substringAfter("```").substringBeforeLast("```").trim()
        }
        return text
    }

    private fun parseJsonToReceipt(jsonStr: String): ParsedReceiptResponse? {
        return try {
            val adapter: JsonAdapter<ParsedReceiptResponse> =
                RetrofitClient.moshiInstance.adapter(ParsedReceiptResponse::class.java)
            adapter.fromJson(jsonStr)
        } catch (e: Exception) {
            Log.e("ReceiptOCR", "JSON parsing failed", e)
            null
        }
    }

    // Date formatting helpers
    fun formatTurkishDate(timestamp: Long): String {
        return try {
            val sdf = SimpleDateFormat("dd.MM.yyyy", Locale("tr", "TR"))
            sdf.format(Date(timestamp))
        } catch (e: Exception) {
            val sdf = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
            sdf.format(Date(timestamp))
        }
    }

    fun parseTurkishDate(dateStr: String): Long {
        return try {
            val clean = dateStr.trim()
            val sdf = SimpleDateFormat("dd.MM.yyyy", Locale("tr", "TR"))
            sdf.parse(clean)?.time ?: System.currentTimeMillis()
        } catch (e: Exception) {
            try {
                val sdf = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
                sdf.parse(dateStr.trim())?.time ?: System.currentTimeMillis()
            } catch (e2: Exception) {
                System.currentTimeMillis()
            }
        }
    }
}
