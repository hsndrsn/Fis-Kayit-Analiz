package com.example.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.api.ParsedReceiptItem
import com.example.api.ParsedReceiptResponse
import androidx.compose.ui.graphics.asImageBitmap
import com.example.data.Receipt
import com.example.data.ReceiptItem
import java.io.File
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.*

// Categories to choose from consistently
val CUSTOM_CATEGORIES = listOf(
    "Gıda", "İçecek", "Temizlik", "Kişisel Bakım", "Giyim", "Elektronik", "Ulaşım", "Diğer"
)

// Material Colors list for visual charts
val CATEGORY_COLORS = mapOf(
    "Gıda" to Color(0xFF4CAF50),         // Green
    "İçecek" to Color(0xFF2196F3),       // Blue
    "Temizlik" to Color(0xFF9C27B0),     // Purple
    "Kişisel Bakım" to Color(0xFFFF9800), // Orange
    "Giyim" to Color(0xFFE91E63),         // Pink
    "Elektronik" to Color(0xFF3F51B5),    // Indigo
    "Ulaşım" to Color(0xFF00BCD4),       // Cyan
    "Diğer" to Color(0xFF795548)          // Brown
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FisDefteriApp(
    viewModel: ReceiptViewModel = viewModel()
) {
    val context = LocalContext.current
    val receipts by viewModel.allReceipts.collectAsState()
    val ocrState by viewModel.ocrState.collectAsState()
    val capturedPhotoState by viewModel.capturedPhoto.collectAsState()

    var activeTab by remember { mutableStateOf(0) }
    var selectedReceiptForDetail by remember { mutableStateOf<Receipt?>(null) }
    var editingReceipt by remember { mutableStateOf<Receipt?>(null) }

    // Uri helper for Camera taking
    var tempPhotoUri by remember { mutableStateOf<Uri?>(null) }

    // Launcher for camera
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture(),
        onResult = { success ->
            if (success) {
                tempPhotoUri?.let { uri ->
                    val bitmap = loadBitmapFromUri(context, uri)
                    if (bitmap != null) {
                        viewModel.analyzeReceipt(bitmap)
                    } else {
                        Toast.makeText(context, "Fotoğraf yüklenemedi.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    )

    // Launcher for Gallery
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri ->
            uri?.let {
                val bitmap = loadBitmapFromUri(context, it)
                if (bitmap != null) {
                    viewModel.analyzeReceipt(bitmap)
                } else {
                    Toast.makeText(context, "Galeri görseli okunamadı.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    )

    // Launcher for camera permission request
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            if (granted) {
                val uri = createTempImageUri(context)
                tempPhotoUri = uri
                cameraLauncher.launch(uri)
            } else {
                Toast.makeText(
                    context, 
                    "Kamera izni verilmedi. Galeriden seçim yapabilirsiniz.", 
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    )

    fun startCameraIntent() {
        val permissionCheck = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
        if (permissionCheck == PackageManager.PERMISSION_GRANTED) {
            val uri = createTempImageUri(context)
            tempPhotoUri = uri
            cameraLauncher.launch(uri)
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    Scaffold(
        bottomBar = {
            if (editingReceipt == null && (ocrState is GeminiOcrState.Idle || ocrState is GeminiOcrState.Error)) {
                NavigationBar(
                    modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars)
                ) {
                    NavigationBarItem(
                        selected = activeTab == 0,
                        onClick = { activeTab = 0 },
                        icon = { Icon(Icons.Default.Home, contentDescription = "Ana Sayfa") },
                        label = { Text("Ana Sayfa") },
                        modifier = Modifier.testTag("nav_home")
                    )
                    NavigationBarItem(
                        selected = activeTab == 1,
                        onClick = { activeTab = 1 },
                        icon = { Icon(Icons.Default.List, contentDescription = "Geçmiş") },
                        label = { Text("Geçmiş") },
                        modifier = Modifier.testTag("nav_history")
                    )
                    NavigationBarItem(
                        selected = activeTab == 2,
                        onClick = { activeTab = 2 },
                        icon = { Icon(Icons.Default.Info, contentDescription = "Analiz") },
                        label = { Text("Analiz") },
                        modifier = Modifier.testTag("nav_analysis")
                    )
                    NavigationBarItem(
                        selected = activeTab == 3,
                        onClick = { activeTab = 3 },
                        icon = { Icon(Icons.Default.Star, contentDescription = "Fiyat Değişim") },
                        label = { Text("Fiyat Değişim") },
                        modifier = Modifier.testTag("nav_price_change")
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // State pattern for app content
            val editRec = editingReceipt
            val currentOcr = ocrState
            when {
                editRec != null -> {
                    EditReceiptScreen(
                        receipt = editRec,
                        onCancel = { editingReceipt = null },
                        onSave = { id, store, date, total, loc, items ->
                            viewModel.updateReceipt(id, store, date, total, loc, items)
                            editingReceipt = null
                            Toast.makeText(context, "Fiş başarıyla güncellendi!", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
                currentOcr is GeminiOcrState.Loading -> {
                    ScanningProgressOverlay(
                        capturedPhoto = capturedPhotoState
                    )
                }
                currentOcr is GeminiOcrState.Success -> {
                    PreviewOcrScreen(
                        parsedData = currentOcr.parsedReceipt,
                        capturedPhoto = capturedPhotoState,
                        onCancel = {
                            viewModel.resetOcrState()
                        },
                        onSave = { store, date, total, loc, items ->
                            viewModel.saveReceipt(store, date, total, loc, items)
                            viewModel.resetOcrState()
                            activeTab = 0 // Go to Home tab
                            Toast.makeText(context, "Alışveriş başarıyla kaydedildi!", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
                else -> {
                    // Normal tabs
                    AnimatedContent(
                        targetState = activeTab,
                        label = "tab_transitions"
                    ) { tabIndex ->
                        when (tabIndex) {
                            0 -> HomeScreen(
                                receipts = receipts,
                                onCameraClick = { startCameraIntent() },
                                onGalleryClick = { galleryLauncher.launch("image/*") },
                                onReceiptClick = { selectedReceiptForDetail = it },
                                ocrState = ocrState,
                                onDismissError = { viewModel.resetOcrState() }
                            )
                            1 -> HistoryScreen(
                                receipts = receipts,
                                onReceiptClick = { selectedReceiptForDetail = it }
                            )
                            2 -> AnalyticsScreen(
                                receipts = receipts
                            )
                            3 -> PriceTrackingScreen(
                                receipts = receipts
                            )
                        }
                    }
                }
            }

            // Detail Bottom Sheet
            selectedReceiptForDetail?.let { receipt ->
                ReceiptDetailBottomSheet(
                    receipt = receipt,
                    onDismiss = { selectedReceiptForDetail = null },
                    onDelete = {
                        viewModel.deleteReceipt(receipt.id)
                        selectedReceiptForDetail = null
                        Toast.makeText(context, "Fiş silindi.", Toast.LENGTH_SHORT).show()
                    },
                    onEdit = {
                        editingReceipt = receipt
                        selectedReceiptForDetail = null
                    }
                )
            }
        }
    }
}

@Composable
fun ScanningProgressOverlay(capturedPhoto: Bitmap?) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        val img = capturedPhoto
        if (img != null) {
            Box(
                modifier = Modifier
                    .size(200.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp))
            ) {
                Image(
                    bitmap = img.asImageBitmap(),
                    contentDescription = "Çekilen fiş",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
        }

        LinearProgressIndicator(
            modifier = Modifier
                .width(180.dp)
                .clip(CircleShape),
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "Yapay Zeka Fişinizi Çözümlüyor...",
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Ürünler, fiyatlar ve kategoriler otomatik olarak ayıklanıyor. Lütfen bekleyin.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(
    receipts: List<Receipt>,
    onCameraClick: () -> Unit,
    onGalleryClick: () -> Unit,
    onReceiptClick: (Receipt) -> Unit,
    ocrState: GeminiOcrState,
    onDismissError: () -> Unit
) {
    val thisMonthSpending = remember(receipts) {
        val cal = Calendar.getInstance()
        val currentMonth = cal.get(Calendar.MONTH)
        val currentYear = cal.get(Calendar.YEAR)

        receipts.filter { rec ->
            val recCal = Calendar.getInstance().apply { timeInMillis = rec.date }
            recCal.get(Calendar.MONTH) == currentMonth && recCal.get(Calendar.YEAR) == currentYear
        }.sumOf { it.totalAmount }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // App header
        item {
            Column(modifier = Modifier.padding(vertical = 8.dp)) {
                Text(
                    text = "Fiş Defteri",
                    fontWeight = FontWeight.Bold,
                    fontSize = 28.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Hızlı tarama, akıllı harcama takibi",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Display error if any
        if (ocrState is GeminiOcrState.Error) {
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Warning, contentDescription = "Hata", tint = MaterialTheme.colorScheme.error)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Çözümleme Başarısız",
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.errorColor()
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = ocrState.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = onDismissError,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.onErrorContainer
                            ),
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Text("Tamam", color = MaterialTheme.colorScheme.errorContainer)
                        }
                    }
                }
            }
        }

        // Summary Card
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                ),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(24.dp)
                ) {
                    Text(
                        text = "Bu Ayki Toplam Harcama",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "₺${formatMoney(thisMonthSpending)}",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 36.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Kayıtlı toplam fiş sayısı: ${receipts.size}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                }
            }
        }

        // Capture receipts big box
        item {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Yeni Fiş Kaydet",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = onCameraClick,
                        modifier = Modifier
                            .weight(1f)
                            .height(110.dp)
                            .testTag("button_fiş_çek"),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.ShoppingCart,
                                contentDescription = "Camera",
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Fiş Fotoğrafı Çek",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = onGalleryClick,
                        modifier = Modifier
                            .weight(1f)
                            .height(110.dp)
                            .testTag("button_galeri"),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Add from Gallery",
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "Galeriden Seç",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }

        // Recent Receipts
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Son Harcamalar",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }

        if (receipts.isEmpty()) {
            item {
                EmptyReceiptState()
            }
        } else {
            val limit = if (receipts.size > 5) 5 else receipts.size
            itemsIndexed(receipts.take(limit)) { _, receipt ->
                ReceiptRowItem(receipt = receipt, onClick = { onReceiptClick(receipt) })
            }
        }
    }
}

@Composable
fun EmptyReceiptState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Warning,
            contentDescription = "Yok",
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.size(56.dp)
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Kayıtlı Fiş Bulunmuyor",
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Hemen üstteki butonları kullanarak ilk faturanı tarat!",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun ReceiptRowItem(receipt: Receipt, searchQuery: String = "", onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1.0f)) {
                    Text(
                        text = receipt.storeName,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = formatDate(receipt.date),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (!receipt.location.isNullOrBlank()) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "• ${receipt.location}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Text(
                    text = "₺${formatMoney(receipt.totalAmount)}",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.titleMedium
                )
            }

            // Inline matching search items
            if (searchQuery.isNotBlank()) {
                val matchingItems = receipt.items.filter { it.name.contains(searchQuery, ignoreCase = true) }
                if (matchingItems.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Eşleşen Ürünler:",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    matchingItems.forEach { item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "• ${item.name}",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (item.quantity > 1.0) {
                                    Text(
                                        text = "(${formatDouble(item.quantity)} adet)",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Text(
                                text = "₺${formatMoney(item.totalPrice)}",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
    }
}

// History Screen with rich filters
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HistoryScreen(
    receipts: List<Receipt>,
    onReceiptClick: (Receipt) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategoryFilter by remember { mutableStateOf("Tümü") }
    var selectedStoreFilter by remember { mutableStateOf("Tümü") }

    val storesList = remember(receipts) {
        val stores = receipts.map { it.storeName.trim() }.distinct().toMutableList()
        stores.add(0, "Tümü")
        stores
    }

    val filteredReceipts = remember(receipts, searchQuery, selectedCategoryFilter, selectedStoreFilter) {
        receipts.filter { rec ->
            val matchesSearch = rec.storeName.contains(searchQuery, ignoreCase = true) || 
                                (rec.location ?: "").contains(searchQuery, ignoreCase = true) ||
                                rec.items.any { it.name.contains(searchQuery, ignoreCase = true) }

            val matchesStore = selectedStoreFilter == "Tümü" || rec.storeName.trim() == selectedStoreFilter

            val matchesCategory = selectedCategoryFilter == "Tümü" || rec.items.any { it.category == selectedCategoryFilter }

            matchesSearch && matchesStore && matchesCategory
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Alışveriş Geçmişi",
            fontWeight = FontWeight.Bold,
            fontSize = 24.sp,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        // Search Field
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            label = { Text("Mağaza, konum veya ürün adına göre ara...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Ara") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            shape = RoundedCornerShape(12.dp),
            singleLine = true
        )

        // Category Filter row
        Text(
            text = "Kategori Filtresi",
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(bottom = 6.dp)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val categoriesWithAll = listOf("Tümü") + CUSTOM_CATEGORIES
            categoriesWithAll.forEach { cat ->
                val isSelected = selectedCategoryFilter == cat
                FilterChip(
                    selected = isSelected,
                    onClick = { selectedCategoryFilter = cat },
                    label = { Text(cat) }
                )
            }
        }

        // Store Filter row
        if (storesList.size > 2) { // "Tümü" and at least 1 store
            Text(
                text = "Mağaza Filtresi",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(bottom = 6.dp)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                storesList.forEach { store ->
                    val isSelected = selectedStoreFilter == store
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedStoreFilter = store },
                        label = { Text(store) }
                    )
                }
            }
        }

        Divider(modifier = Modifier.padding(vertical = 4.dp))

        if (filteredReceipts.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Arama kriterlerine uygun alışveriş bulunamadı.",
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                itemsIndexed(filteredReceipts) { _, receipt ->
                    ReceiptRowItem(receipt = receipt, searchQuery = searchQuery, onClick = { onReceiptClick(receipt) })
                }
            }
        }
    }
}

// Preview Screen for OCR values
@Composable
fun PreviewOcrScreen(
    parsedData: ParsedReceiptResponse,
    capturedPhoto: Bitmap?,
    onCancel: () -> Unit,
    onSave: (String, String, Double, String, List<ReceiptItem>) -> Unit
) {
    var storeName by remember { mutableStateOf(parsedData.storeName) }
    var rawDate by remember { mutableStateOf(parsedData.date) }
    var totalAmountInput by remember { mutableStateOf(parsedData.totalAmount.toString()) }
    var locationInput by remember { mutableStateOf(parsedData.location ?: "") }

    // Make an editable list of items
    val itemsList = remember {
        val initialList = parsedData.items.map {
            mutableStateOf(
                ReceiptItem(
                    name = it.name,
                    quantity = it.quantity,
                    unitPrice = it.unitPrice,
                    totalPrice = it.totalPrice,
                    category = if (CUSTOM_CATEGORIES.contains(it.category)) it.category else "Diğer"
                )
            )
        }.toMutableStateList()
        initialList
    }

    // Auto calculate and update total amount based on items sum
    val computedTotal = remember(itemsList) {
        derivedStateOf {
            itemsList.sumOf { it.value.totalPrice }
        }
    }

    LaunchedEffect(computedTotal.value) {
        if (computedTotal.value > 0.0) {
            totalAmountInput = String.format(Locale.US, "%.2f", computedTotal.value)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Upper TopAppBar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Algılanan Fiş Bilgileri",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary
            )
            IconButton(onClick = onCancel) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Geri")
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Receipt Metadata card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            "Ana Fiş Bilgileri",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )

                        OutlinedTextField(
                            value = storeName,
                            onValueChange = { storeName = it },
                            label = { Text("Mağaza / İşyeri Adı") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedTextField(
                                value = rawDate,
                                onValueChange = { rawDate = it },
                                label = { Text("Tarih (gg.aa.yyyy)") },
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )

                            OutlinedTextField(
                                value = totalAmountInput,
                                onValueChange = { totalAmountInput = it },
                                label = { Text("Toplam Tutar (₺)") },
                                modifier = Modifier.weight(1f),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true
                            )
                        }

                        OutlinedTextField(
                            value = locationInput,
                            onValueChange = { locationInput = it },
                            label = { Text("Konum/Şube (İsteğe Bağlı)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }
                }
            }

            // Products section
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Satın Alınan Ürünler (${itemsList.size})",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )

                    Button(
                        onClick = {
                            itemsList.add(
                                mutableStateOf(
                                    ReceiptItem(
                                        name = "Yeni Ürün",
                                        quantity = 1.0,
                                        unitPrice = 0.0,
                                        totalPrice = 0.0,
                                        category = "Diğer"
                                    )
                                )
                            )
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Ekle")
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Ekle", fontSize = 12.sp)
                    }
                }
            }

            itemsIndexed(itemsList) { index, itemState ->
                val item = itemState.value
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f))
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Product Name / Delete Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = item.name,
                                onValueChange = { newName ->
                                    itemState.value = item.copy(name = newName)
                                },
                                label = { Text("Ürün Adı") },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent
                                )
                            )

                            Spacer(modifier = Modifier.width(8.dp))

                            IconButton(
                                onClick = { itemsList.removeAt(index) },
                                colors = IconButtonDefaults.iconButtonColors(
                                    contentColor = MaterialTheme.colorScheme.error
                                )
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = "Sil")
                            }
                        }

                        // Category Dropdown list
                        var expandedDropDown by remember { mutableStateOf(false) }
                        Box(modifier = Modifier.fillMaxWidth()) {
                            OutlinedTextField(
                                value = item.category,
                                onValueChange = {},
                                label = { Text("Kategori") },
                                readOnly = true,
                                trailingIcon = {
                                    IconButton(onClick = { expandedDropDown = true }) {
                                        Icon(
                                            painter = painterResource(id = android.R.drawable.arrow_down_float),
                                            contentDescription = "Seç"
                                        )
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { expandedDropDown = true },
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent
                                )
                            )

                            DropdownMenu(
                                expanded = expandedDropDown,
                                onDismissRequest = { expandedDropDown = false }
                            ) {
                                CUSTOM_CATEGORIES.forEach { category ->
                                    DropdownMenuItem(
                                        text = { Text(category) },
                                        onClick = {
                                            itemState.value = item.copy(category = category)
                                            expandedDropDown = false
                                        }
                                    )
                                }
                            }
                        }

                        // Quantity, Unit Price and Total Price calculation fields
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedTextField(
                                value = if (item.quantity == 0.0) "" else item.quantity.toString(),
                                onValueChange = { qtyStr ->
                                    val qty = qtyStr.toDoubleOrNull() ?: 0.0
                                    itemState.value = item.copy(
                                        quantity = qty,
                                        totalPrice = qty * item.unitPrice
                                    )
                                },
                                label = { Text("Adet") },
                                modifier = Modifier.weight(1.2f),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true
                            )

                            OutlinedTextField(
                                value = if (item.unitPrice == 0.0) "" else item.unitPrice.toString(),
                                onValueChange = { priceStr ->
                                    val price = priceStr.toDoubleOrNull() ?: 0.0
                                    itemState.value = item.copy(
                                        unitPrice = price,
                                        totalPrice = item.quantity * price
                                    )
                                },
                                label = { Text("Birim (₺)") },
                                modifier = Modifier.weight(1.5f),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true
                            )

                            OutlinedTextField(
                                value = String.format(Locale.US, "%.2f", item.totalPrice),
                                onValueChange = { totStr ->
                                    val tot = totStr.toDoubleOrNull() ?: 0.0
                                    itemState.value = item.copy(totalPrice = tot)
                                },
                                label = { Text("Toplam (₺)") },
                                modifier = Modifier.weight(1.8f),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true
                            )
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }

        // Save panel at the bottom
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("İptal Et")
                }

                Button(
                    onClick = {
                        val parsedTotal = totalAmountInput.toDoubleOrNull() ?: computedTotal.value
                        val itemsToSave = itemsList.map { it.value }
                        onSave(storeName, rawDate, parsedTotal, locationInput, itemsToSave)
                    },
                    modifier = Modifier
                        .weight(1.5f)
                        .height(50.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Check, contentDescription = "Kaydet")
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Fişi Deftere Kaydet")
                }
            }
        }
    }
}

// Edit Screen for existing Receipt
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EditReceiptScreen(
    receipt: Receipt,
    onCancel: () -> Unit,
    onSave: (Long, String, String, Double, String, List<ReceiptItem>) -> Unit
) {
    var storeName by remember { mutableStateOf(receipt.storeName) }
    var rawDate by remember { mutableStateOf(formatDate(receipt.date)) }
    var totalAmountInput by remember { mutableStateOf(receipt.totalAmount.toString()) }
    var locationInput by remember { mutableStateOf(receipt.location ?: "") }

    // Make an editable list of items
    val itemsList = remember {
        val initialList = receipt.items.map {
            mutableStateOf(
                ReceiptItem(
                    name = it.name,
                    quantity = it.quantity,
                    unitPrice = it.unitPrice,
                    totalPrice = it.totalPrice,
                    category = if (CUSTOM_CATEGORIES.contains(it.category)) it.category else "Diğer"
                )
            )
        }.toMutableStateList()
        initialList
    }

    // Auto calculate and update total amount based on items sum
    val computedTotal = remember(itemsList) {
        derivedStateOf {
            itemsList.sumOf { it.value.totalPrice }
        }
    }

    LaunchedEffect(computedTotal.value) {
        if (computedTotal.value > 0.0) {
            totalAmountInput = String.format(Locale.US, "%.2f", computedTotal.value)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Text(
            text = "Fiş Düzenle",
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(16.dp)
        )

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Store & Date row
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Genel Bilgiler",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium
                        )

                        OutlinedTextField(
                            value = storeName,
                            onValueChange = { storeName = it },
                            label = { Text("Mağaza Adı") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedTextField(
                                value = rawDate,
                                onValueChange = { rawDate = it },
                                label = { Text("Tarih (gg.aa.yyyy)") },
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )
                            OutlinedTextField(
                                value = totalAmountInput,
                                onValueChange = { totalAmountInput = it },
                                label = { Text("Toplam Tutar (₺)") },
                                modifier = Modifier.weight(1f),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true
                            )
                        }

                        OutlinedTextField(
                            value = locationInput,
                            onValueChange = { locationInput = it },
                            label = { Text("Şube / Konum Bilgisi") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }
                }
            }

            // Products section
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Satın Alınan Ürünler (${itemsList.size})",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )

                    TextButton(
                        onClick = {
                            itemsList.add(
                                mutableStateOf(
                                    ReceiptItem(
                                        name = "Yeni Ürün",
                                        quantity = 1.0,
                                        unitPrice = 0.0,
                                        totalPrice = 0.0,
                                        category = "Diğer"
                                    )
                                )
                            )
                        }
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Ekle")
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Yeni Ürün Ekle")
                    }
                }
            }

            itemsIndexed(itemsList) { index, itemState ->
                val item = itemState.value
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${index + 1}. Ürün Detayı",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )

                            IconButton(
                                onClick = { itemsList.removeAt(index) },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Ürünü Sil",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        // Product Name field
                        OutlinedTextField(
                            value = item.name,
                            onValueChange = { nameStr ->
                                itemState.value = item.copy(name = nameStr)
                            },
                            label = { Text("Ürün Adı") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        // Category flow layout selection
                        Text(text = "Kategori", style = MaterialTheme.typography.labelSmall)
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            CUSTOM_CATEGORIES.forEach { cat ->
                                val isSelected = item.category == cat
                                FilterChip(
                                    selected = isSelected,
                                    onClick = {
                                        itemState.value = item.copy(category = cat)
                                    },
                                    label = { Text(cat, fontSize = 11.sp) }
                                )
                            }
                        }

                        // Pricing calculation inputs
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = formatDouble(item.quantity),
                                onValueChange = { qtyStr ->
                                    val qty = qtyStr.toDoubleOrNull() ?: 1.0
                                    val newTotal = qty * item.unitPrice
                                    itemState.value = item.copy(
                                        quantity = qty,
                                        totalPrice = newTotal
                                    )
                                },
                                label = { Text("Adet") },
                                modifier = Modifier.weight(1f),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true
                            )

                            OutlinedTextField(
                                value = formatDouble(item.unitPrice),
                                onValueChange = { prcStr ->
                                    val prc = prcStr.toDoubleOrNull() ?: 0.0
                                    val newTotal = item.quantity * prc
                                    itemState.value = item.copy(
                                        unitPrice = prc,
                                        totalPrice = newTotal
                                    )
                                },
                                label = { Text("Birim F. (₺)") },
                                modifier = Modifier.weight(1.2f),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true
                            )

                            OutlinedTextField(
                                value = String.format(Locale.US, "%.2f", item.totalPrice),
                                onValueChange = { totStr ->
                                    val tot = totStr.toDoubleOrNull() ?: 0.0
                                    itemState.value = item.copy(totalPrice = tot)
                                },
                                label = { Text("Toplam (₺)") },
                                modifier = Modifier.weight(1.8f),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true
                            )
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }

        // Save panel at the bottom
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("İptal Et")
                }

                Button(
                    onClick = {
                        val parsedTotal = totalAmountInput.toDoubleOrNull() ?: computedTotal.value
                        val itemsToSave = itemsList.map { it.value }
                        onSave(receipt.id, storeName, rawDate, parsedTotal, locationInput, itemsToSave)
                    },
                    modifier = Modifier
                        .weight(1.5f)
                        .height(50.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Check, contentDescription = "Kaydet")
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Değişiklikleri Kaydet")
                }
            }
        }
    }
}

// Receipt Detail Bottom Sheet Dialog
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReceiptDetailBottomSheet(
    receipt: Receipt,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
    onEdit: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = receipt.storeName,
                        fontWeight = FontWeight.ExtraBold,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = formatDate(receipt.date),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onEdit,
                        colors = IconButtonDefaults.iconButtonColors(
                            contentColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = "Düzenle")
                    }

                    IconButton(
                        onClick = onDelete,
                        colors = IconButtonDefaults.iconButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Sil")
                    }
                }
            }

            if (!receipt.location.isNullOrBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Home,
                        contentDescription = "Konum",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = receipt.location, style = MaterialTheme.typography.bodyMedium)
                }
            }

            Divider()

            Text(
                "Satın Alınan Ürünler",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium
            )

            // Items list Scrollable block inside column
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .weight(1f, fill = false),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                receipt.items.forEach { item ->
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1.0f)) {
                                Text(item.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Spacer(modifier = Modifier.height(4.dp))
                                Card(
                                    colors = CardDefaults.cardColors(
                                        containerColor = CATEGORY_COLORS[item.category]?.copy(alpha = 0.15f) ?: Color.Gray.copy(alpha = 0.1f)
                                    ),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = item.category,
                                        color = CATEGORY_COLORS[item.category] ?: Color.Gray,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    "₺${formatMoney(item.totalPrice)}",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    "${formatDouble(item.quantity)} Adet x ₺${formatMoney(item.unitPrice)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            Divider()

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Toplam Tutar",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
                Text(
                    "₺${formatMoney(receipt.totalAmount)}",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 24.sp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

// Stats & Interactive Charts Screen
@Composable
fun AnalyticsScreen(
    receipts: List<Receipt>
) {
    if (receipts.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    Icons.Default.Info,
                    contentDescription = "Info",
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Veri Analizi İçin Fiş Gerekli",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Henüz kaydedilmiş bir harcamanız yok. Grafik ve içgörülerinizi görmek için ana ekrandan bir fatura taratın.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
        return
    }

    // Prepare statistics metrics
    val totalSpendSum = remember(receipts) { receipts.sumOf { it.totalAmount } }
    
    val categoryTotals = remember(receipts) {
        val totals = mutableMapOf<String, Double>()
        receipts.forEach { rec ->
            rec.items.forEach { item ->
                val prev = totals[item.category] ?: 0.0
                totals[item.category] = prev + item.totalPrice
            }
        }
        totals.toList().sortedByDescending { it.second }
    }

    val storeTotals = remember(receipts) {
        receipts.groupBy { consolidateStoreName(it.storeName) }
            .mapValues { entry -> entry.value.sumOf { it.totalAmount } }
            .toList()
            .sortedByDescending { it.second }
    }

    val highestReceipt = remember(receipts) {
        receipts.maxByOrNull { it.totalAmount }
    }

    val topCategory = remember(categoryTotals) {
        categoryTotals.firstOrNull()?.first ?: "Yok"
    }

    var selectedDrillDownCategory by remember { mutableStateOf<String?>(if (topCategory != "Yok") topCategory else null) }

    val drilldownItems = remember(receipts, selectedDrillDownCategory) {
        if (selectedDrillDownCategory == null) emptyList()
        else {
            val list = mutableListOf<Triple<Receipt, ReceiptItem, String>>()
            receipts.forEach { rec ->
                rec.items.forEach { item ->
                    if (item.category == selectedDrillDownCategory) {
                        list.add(Triple(rec, item, consolidateStoreName(rec.storeName)))
                    }
                }
            }
            list.sortedByDescending { it.first.date }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        item {
            Text(
                text = "Harcama Analiz Panosu",
                fontWeight = FontWeight.Bold,
                fontSize = 24.sp,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                "Mevcut verilerinizin grafiksel dağılımı",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // 2x2 Grid of Dashboard Summary Metrics
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Top spent card
                    Card(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ShoppingCart,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Column {
                                Text(
                                    "Toplam Harcama",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                )
                                Text(
                                    "₺${formatMoney(totalSpendSum)}",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }

                    // Total receipts card
                    Card(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.1f))
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.secondary),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.List,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSecondary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Column {
                                Text(
                                    "Fiş Adedi",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                                )
                                Text(
                                    "${receipts.size} Fiş",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Average Spent card
                    Card(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.1f))
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.tertiary),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Star,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onTertiary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Column {
                                Text(
                                    "Ortalama Fiş",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f)
                                )
                                Text(
                                    "₺${formatMoney(totalSpendSum / receipts.size)}",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                            }
                        }
                    }

                    // Top store/most expensive
                    Card(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.outline),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Column {
                                Text(
                                    "En Pahalı Harcama",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    "₺${formatMoney(highestReceipt?.totalAmount ?: 0.0)}",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }

        // Automatic Insight Cards
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Akıllı Hesap Özeti & İçgörüler",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Text("En Fazla Harcanan Kategori:", style = MaterialTheme.typography.bodyMedium)
                            Text(topCategory, fontWeight = FontWeight.Bold, color = CATEGORY_COLORS[topCategory] ?: MaterialTheme.colorScheme.primary)
                        }
                        if (highestReceipt != null) {
                            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                Text("En Pahalı Alışveriş:", style = MaterialTheme.typography.bodyMedium)
                                Text("${highestReceipt.storeName} (₺${formatMoney(highestReceipt.totalAmount)})", fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Divider()
                    Spacer(modifier = Modifier.height(12.dp))

                    // Text suggestions
                    Text(
                        text = "💡 Öneriler & Yönlendirmeler",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = getSmartTurkishRecommendation(topCategory, totalSpendSum / receipts.size),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Interactive Category Distribution Donut / Pie Chart
        if (categoryTotals.isNotEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Kategorilere Göre Dağılım (Pasta Grafik)",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                        Text(
                            text = "Detayları ve ürünleri görmek için bir kategoriye dokunun",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // Donut Chart Canvas
                            Box(
                                contentAlignment = Alignment.Center
                            ) {
                                CategoryDonutChart(
                                    categoryTotals = categoryTotals,
                                    totalWidth = totalSpendSum
                                )
                                // Centered category info
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    val countOfCats = categoryTotals.size
                                    Text(
                                        text = "$countOfCats Kat.",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            // Interactive Legend List
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                categoryTotals.forEach { (cat, amount) ->
                                    val percent = if (totalSpendSum > 0) ((amount / totalSpendSum) * 100).toInt() else 0
                                    val isSelected = selectedDrillDownCategory == cat
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(
                                                if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                                                else Color.Transparent
                                            )
                                            .clickable {
                                                selectedDrillDownCategory = if (isSelected) null else cat
                                            }
                                            .padding(horizontal = 6.dp, vertical = 4.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(10.dp)
                                                .clip(CircleShape)
                                                .background(CATEGORY_COLORS[cat] ?: Color.Gray)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "$cat (%$percent)",
                                            fontSize = 12.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                        if (isSelected) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = "Seçili",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(12.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Drill-Down detailed summary section
        val activeDrill = selectedDrillDownCategory
        if (activeDrill != null && drilldownItems.isNotEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(12.dp)
                                        .clip(CircleShape)
                                        .background(CATEGORY_COLORS[activeDrill] ?: Color.Gray)
                                )
                                Text(
                                    text = "$activeDrill Veri Özeti",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            IconButton(
                                onClick = { selectedDrillDownCategory = null },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Kapat",
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        val catTotalStrStr = categoryTotals.find { it.first == activeDrill }?.second ?: 0.0
                        Text(
                            text = "Bu kategoride toplam harcanan miktar: ₺${formatMoney(catTotalStrStr)}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary
                        )

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            drilldownItems.forEach { (receipt, item, store) ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = item.name,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Text(
                                                text = store,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Text(
                                                text = "• ${formatDate(receipt.date)}",
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            text = "₺${formatMoney(item.totalPrice)}",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            text = "${formatDouble(item.quantity)} Adet x ₺${formatMoney(item.unitPrice)}",
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Time-based Spend card
        item {
            TimeBasedAnalysisCard(receipts = receipts)
        }

        // Store spending Distribution Horizontal Bar Chart
        if (storeTotals.isNotEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Mağazalara Göre Dağılım",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium
                        )

                        val maxAmount = storeTotals.maxOf { it.second }.toFloat()

                        storeTotals.take(5).forEach { (store, amount) ->
                            val flowPercentage = if (maxAmount > 0f) (amount / maxAmount).toFloat() else 0f
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = store,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                    Text(
                                        text = "₺${formatMoney(amount)}",
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }

                                // Native custom progress drawn bar
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(10.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth(flowPercentage)
                                            .height(10.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primary)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TimeBasedAnalysisCard(receipts: List<Receipt>) {
    var selectedGroup by remember { mutableStateOf(TimeGrouping.AYLIK) }

    val groupedSpendings = remember(receipts, selectedGroup) {
        val cal = Calendar.getInstance()
        val groups = mutableMapOf<String, Double>()
        val groupTimestamps = mutableMapOf<String, Long>()

        receipts.forEach { rec ->
            cal.timeInMillis = rec.date
            val key = when (selectedGroup) {
                TimeGrouping.GÜNLÜK -> formatDate(rec.date)
                TimeGrouping.HAFTALIK -> {
                    val week = cal.get(Calendar.WEEK_OF_YEAR)
                    val year = cal.get(Calendar.YEAR)
                    "Hafta $week ($year)"
                }
                TimeGrouping.AYLIK -> {
                    val monthName = cal.getDisplayName(Calendar.MONTH, Calendar.LONG, Locale("tr", "TR")) ?: ""
                    val year = cal.get(Calendar.YEAR)
                    "$monthName $year"
                }
            }
            groups[key] = (groups[key] ?: 0.0) + rec.totalAmount
            val currentMax = groupTimestamps[key] ?: 0L
            if (rec.date > currentMax) {
                groupTimestamps[key] = rec.date
            }
        }

        groups.toList()
            .sortedByDescending { (key, _) -> groupTimestamps[key] ?: 0L }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Zamana Göre Harcamalar",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TimeGrouping.values().forEach { grouping ->
                    val isSelected = selectedGroup == grouping
                    val label = when (grouping) {
                        TimeGrouping.GÜNLÜK -> "Günlük"
                        TimeGrouping.HAFTALIK -> "Haftalık"
                        TimeGrouping.AYLIK -> "Aylık"
                    }
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedGroup = grouping },
                        label = { Text(label, fontSize = 12.sp) }
                    )
                }
            }

            if (groupedSpendings.isEmpty()) {
                Text(
                    "Bu periyotta harcama verisi bulunamadı.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                val maxAmount = groupedSpendings.maxOf { it.second }.toFloat()

                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    groupedSpendings.take(5).forEach { (periodName, totalAmount) ->
                        val barPercentage = if (maxAmount > 0f) (totalAmount / maxAmount).toFloat() else 0f
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = periodName,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                                Text(
                                    text = "₺${formatMoney(totalAmount)}",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(10.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(barPercentage)
                                        .height(10.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primary)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

enum class TimeGrouping { GÜNLÜK, HAFTALIK, AYLIK }

// Custom concentric Ring/Donut Graph on Canvas
@Composable
fun CategoryDonutChart(
    categoryTotals: List<Pair<String, Double>>,
    totalWidth: Double
) {
    Canvas(
        modifier = Modifier.size(110.dp)
    ) {
        var startAngle = -90f
        categoryTotals.forEach { (cat, amount) ->
            val sweepAngle = if (totalWidth > 0.0) ((amount / totalWidth) * 360f).toFloat() else 0f
            val color = CATEGORY_COLORS[cat] ?: Color.Gray

            drawArc(
                color = color,
                startAngle = startAngle,
                sweepAngle = sweepAngle,
                useCenter = false,
                style = Stroke(width = 14.dp.toPx(), cap = StrokeCap.Round),
                size = Size(96.dp.toPx(), 96.dp.toPx()),
                topLeft = Offset(7.dp.toPx(), 7.dp.toPx())
            )
            startAngle += sweepAngle
        }
    }
}

// Helpers
fun loadBitmapFromUri(context: Context, uri: Uri): Bitmap? {
    return try {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            val source = android.graphics.ImageDecoder.createSource(context.contentResolver, uri)
            android.graphics.ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                decoder.isMutableRequired = true
            }
        } else {
            @Suppress("DEPRECATION")
            android.provider.MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
        }
    } catch (e: Exception) {
        null
    }
}

fun createTempImageUri(context: Context): Uri {
    val tempFile = File.createTempFile("fiş_foto", ".jpg", context.cacheDir).apply {
        deleteOnExit()
    }
    return FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        tempFile
    )
}

fun formatDate(timestamp: Long): String {
    return try {
        val df = SimpleDateFormat("dd.MM.yyyy", Locale("tr", "TR"))
        df.format(Date(timestamp))
    } catch (e: Exception) {
        val df = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
        df.format(Date(timestamp))
    }
}

fun formatMoney(amount: Double): String {
    val dec = DecimalFormat("###,###,##0.00")
    return dec.format(amount)
}

fun formatDouble(value: Double): String {
    return if (value % 1.0 == 0.0) {
        value.toInt().toString()
    } else {
        String.format(Locale.US, "%.2f", value)
    }
}

fun consolidateStoreName(name: String): String {
    val trimmed = name.trim()
    val lower = trimmed.lowercase(Locale("tr", "TR"))
    return when {
        lower.contains("file") -> "File Market"
        lower.contains("migros") -> "Migros"
        lower.contains("bim") || lower == "bi̇m" -> "BİM"
        lower.contains("a101") || lower.contains("a-101") -> "A101"
        lower.contains("şok") || lower.contains("sok") || lower == "sok market" || lower == "şok market" -> "ŞOK Market"
        lower.contains("carrefour") -> "CarrefourSA"
        lower.contains("shell") -> "Shell"
        lower.contains("opet") -> "Opet"
        lower.contains("gratis") -> "Gratis"
        lower.contains("watsons") -> "Watsons"
        lower.contains("tarım kredi") || lower.contains("kooperatif") -> "Tarım Kredi Koop."
        lower.contains("macrocenter") || lower.contains("macro") -> "Macrocenter"
        else -> {
            // Capitalize first letters of each word
            trimmed.split(" ").filter { it.isNotBlank() }
                .joinToString(" ") { word ->
                    word.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale("tr", "TR")) else it.toString() }
                }
        }
    }
}

fun getSmartTurkishRecommendation(topCategory: String, averageTicket: Double): String {
    return when (topCategory) {
        "Gıda" -> "Alışveriş bütçenizi en çok Gıda harcamaları oluşturuyor. Haftalık toplu market listesi çıkarmak ve indirimleri takip etmek aylık %15 tasarruf etmenizi sağlayabilir."
        "İçecek" -> "İçecek kategorisi harcamalarınız dikkat çekici. Kahve ve asitli içecek tüketimini haftalık planda azaltmak hem bütçenize hem de sağlığınıza olumlu bir katkı yapacaktır."
        "Temizlik" -> "Temizlik malzemeleri harcamalarınız ön planda. Büyük hacimli (endüstriyel) temizlik ürünleri satın alarak birim maliyeti düşürmeyi düşünebilirsiniz."
        "Kişisel Bakım" -> "Kişisel bakım ve makyaj/kozmetik harcamalarınız tepe noktada. Kampanyaları ve marka indirim dönemlerini birleştirerek tasarruf adımları atabilirsiniz."
        "Giyim" -> "Giyim harcamalarınız bu dönemde yükselmiş durumda. Kapsül dolap taktiklerini araştırmanızı ve acele giyim alışverişlerinden kaçınmanızı öneririz!"
        "Elektronik" -> "Elektronik harcamalar yüksek tutarlar barındırıyor. Alışveriş yapmadan önce en az 3 farklı sitede fiyat karşılaştırması yapıp, nakit iadeli / taksitli ödemeleri değerlendirin."
        "Ulaşım" -> "Ulaşım giderleriniz listenizde yer kaplıyor. Yakın mesafeler için bisiklet, yürüyüş veya toplu taşımayı önceliklendirerek akaryakıt masraflarınızı minimize edebilirsiniz."
        else -> "Ortalama fiş tutarınız olan ₺${formatMoney(averageTicket)} düzeyini korumak için sıklıkla küçük harcamalar yerine, planlı ve toplu alışverişleri tercih edebilirsiniz."
    }
}

@Composable
fun MaterialTheme.errorColor(): Color = MaterialTheme.colorScheme.error

data class ProductOccurrence(
    val date: Long,
    val storeName: String,
    val productName: String,
    val unitPrice: Double,
    val quantity: Double,
    val totalPrice: Double
)

data class PriceHistoryPoint(
    val occurrence: ProductOccurrence,
    val diffAmount: Double,
    val diffPercentage: Double,
    val isFirst: Boolean
)

data class ProductHistory(
    val storeName: String,
    val productName: String,
    val history: List<PriceHistoryPoint>
)

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun PriceTrackingScreen(receipts: List<Receipt>) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedStoreFilter by remember { mutableStateOf("Tümü") }
    var onlyChangesFilter by remember { mutableStateOf(true) }

    // Aggregate occurrences
    val allProductOccurrences = remember(receipts) {
        val list = mutableListOf<ProductOccurrence>()
        receipts.forEach { rec ->
            val consolidatedStore = consolidateStoreName(rec.storeName)
            val seenInThisReceipt = mutableSetOf<String>()
            rec.items.forEach { item ->
                val normalizedName = item.name.trim().lowercase(Locale("tr", "TR"))
                if (normalizedName !in seenInThisReceipt) {
                    seenInThisReceipt.add(normalizedName)
                    list.add(
                        ProductOccurrence(
                            date = rec.date,
                            storeName = consolidatedStore,
                            productName = item.name.trim(),
                            unitPrice = item.unitPrice,
                            quantity = item.quantity,
                            totalPrice = item.totalPrice
                        )
                    )
                }
            }
        }
        list
    }

    // Consolidated stores to filter with
    val availableStores = remember(allProductOccurrences) {
        listOf("Tümü") + allProductOccurrences.map { it.storeName }.distinct().sorted()
    }

    // Grouping by store and normalized product name
    val groupedProducts = remember(allProductOccurrences, searchQuery, selectedStoreFilter, onlyChangesFilter) {
        // Group by Store -> Product Name (normalized)
        val filtered = allProductOccurrences.filter { occ ->
            val matchesStore = selectedStoreFilter == "Tümü" || occ.storeName == selectedStoreFilter
            val matchesSearch = occ.productName.contains(searchQuery, ignoreCase = true)
            matchesStore && matchesSearch
        }

        val groups = filtered.groupBy { "${it.storeName}|||${it.productName.lowercase(Locale("tr", "TR"))}" }
        
        val productHistoryList = groups.map { (key, occurrences) ->
            val parts = key.split("|||")
            val store = parts[0]
            // Use the most frequent or first occurrence name as display name
            val displayName = occurrences.first().productName

            // Sort chronologically by date
            val sortedOccurrences = occurrences.sortedBy { it.date }
            
            // Calculate sequential changes
            val historyWithChanges = mutableListOf<PriceHistoryPoint>()
            for (i in sortedOccurrences.indices) {
                val current = sortedOccurrences[i]
                if (i == 0) {
                    historyWithChanges.add(
                        PriceHistoryPoint(
                            occurrence = current,
                            diffAmount = 0.0,
                            diffPercentage = 0.0,
                            isFirst = true
                        )
                    )
                } else {
                    val prev = sortedOccurrences[i - 1]
                    val diff = current.unitPrice - prev.unitPrice
                    val percent = if (prev.unitPrice > 0.0) {
                        (diff / prev.unitPrice) * 100.0
                    } else {
                        0.0
                    }
                    historyWithChanges.add(
                        PriceHistoryPoint(
                            occurrence = current,
                            diffAmount = diff,
                            diffPercentage = percent,
                            isFirst = false
                        )
                    )
                }
            }

            ProductHistory(
                storeName = store,
                productName = displayName,
                history = historyWithChanges
            )
        }

        // Apply "only changes" filter
        val result = if (onlyChangesFilter) {
            productHistoryList.filter { it.history.size >= 2 }
        } else {
            productHistoryList
        }

        // Sort by the ones with the largest history count or highest percent change
        result.sortedWith(compareByDescending<ProductHistory> { it.history.size }.thenBy { it.productName })
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Top view with static banner background look
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Fiyat Değişim Takibi",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "Aynı mağazadan farklı tarihlerde aldığınız ürünlerin birim fiyatlarındaki değişimlerini tutar ve oransal olarak görüntüleyin.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text("Ürün adı ile geçmişi sorgula...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Ara") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            // Filtering options row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilterChip(
                    selected = onlyChangesFilter,
                    onClick = { onlyChangesFilter = !onlyChangesFilter },
                    label = { Text("Sadece Fiyatı Değişenler") },
                    leadingIcon = {
                        if (onlyChangesFilter) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                )
            }

            // Store list horizontal chip filter
            if (availableStores.size > 1) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    availableStores.forEach { store ->
                        val isSelected = selectedStoreFilter == store
                        FilterChip(
                            selected = isSelected,
                            onClick = { selectedStoreFilter = store },
                            label = { Text(store, fontSize = 12.sp) }
                        )
                    }
                }
            }
        }

        // List section
        if (groupedProducts.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                    )
                    Text(
                        text = "Karşılaştıracak yeterli ürün veri geçmişi bulunamadı.",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = if (onlyChangesFilter) "Lütfen 'Sadece Fiyatı Değişenler' filtresini kapatarak tekil fişlerdeki ürünleri de görmeyi deneyin." else "Taranmış veya kaydedilmiş uygun fişiniz bulunmamaktadır.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                itemsIndexed(groupedProducts) { _, prodHist ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Product Title Header
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Top
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = prodHist.productName,
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    ContactStoreBadge(storeName = prodHist.storeName)
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                // Visual Spark Indicator
                                PriceSparkIndicator(history = prodHist.history)
                            }

                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                            // Step Timeline sequence of occurrences
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                prodHist.history.forEachIndexed { index, histPoint ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // Chronological visual dot tracker
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            modifier = Modifier.padding(end = 12.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(8.dp)
                                                    .clip(CircleShape)
                                                    .background(
                                                        when {
                                                            histPoint.isFirst -> MaterialTheme.colorScheme.outline
                                                            histPoint.diffAmount > 0.0 -> MaterialTheme.colorScheme.error
                                                            histPoint.diffAmount < 0.0 -> MaterialTheme.colorScheme.tertiary
                                                            else -> MaterialTheme.colorScheme.outline
                                                        }
                                                    )
                                            )
                                            if (index < prodHist.history.size - 1) {
                                                Box(
                                                    modifier = Modifier
                                                        .width(2.dp)
                                                        .height(28.dp)
                                                        .background(MaterialTheme.colorScheme.outlineVariant)
                                                )
                                            }
                                        }

                                        // Purchase info row content
                                        Row(
                                            modifier = Modifier.weight(1f),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column {
                                                Text(
                                                    text = formatDate(histPoint.occurrence.date),
                                                    fontWeight = FontWeight.SemiBold,
                                                    fontSize = 13.sp,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                                Text(
                                                    text = "${formatDouble(histPoint.occurrence.quantity)} adet alındı",
                                                    fontSize = 11.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }

                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Text(
                                                    text = "₺${formatMoney(histPoint.occurrence.unitPrice)}",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 14.sp,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )

                                                // Change Badge pill
                                                PriceChangeBadge(histPoint = histPoint)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ContactStoreBadge(storeName: String) {
    Card(
        shape = RoundedCornerShape(6.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = Icons.Default.ShoppingCart,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(10.dp)
            )
            Text(
                text = storeName,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
fun PriceSparkIndicator(history: List<PriceHistoryPoint>) {
    if (history.size < 2) return
    val lastPoint = history.last()
    val isUp = lastPoint.diffAmount > 0.0
    val isDown = lastPoint.diffAmount < 0.0

    val symbol = when {
        isUp -> "▲"
        isDown -> "▼"
        else -> "▬"
    }
    val tint = when {
        isUp -> MaterialTheme.colorScheme.error
        isDown -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.outline
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(
                when {
                    isUp -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f)
                    isDown -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
                    else -> MaterialTheme.colorScheme.surfaceVariant
                }
            )
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = symbol,
            color = tint,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp
        )
        Text(
            text = when {
                isUp -> "Yükselme Eğilimi"
                isDown -> "Düşüş Eğilimi"
                else -> "Dengeli"
            },
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = when {
                isUp -> MaterialTheme.colorScheme.error
                isDown -> MaterialTheme.colorScheme.tertiary
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
    }
}

@Composable
fun PriceChangeBadge(histPoint: PriceHistoryPoint) {
    if (histPoint.isFirst) {
        Card(
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Text(
                text = "İLK FİYAT",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
            )
        }
    } else {
        val isUp = histPoint.diffAmount > 0.0
        val isDown = histPoint.diffAmount < 0.0
        
        val containerColor = when {
            isUp -> MaterialTheme.colorScheme.errorContainer
            isDown -> MaterialTheme.colorScheme.tertiaryContainer // Note: let's use safety default if tertiaryContainer not resolved
            else -> MaterialTheme.colorScheme.surfaceVariant
        }
        val contentColor = when {
            isUp -> MaterialTheme.colorScheme.onErrorContainer
            isDown -> MaterialTheme.colorScheme.onTertiaryContainer
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        }

        Card(
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.cardColors(containerColor = containerColor),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                val symbol = when {
                    isUp -> "▲"
                    isDown -> "▼"
                    else -> "▬"
                }
                Text(
                    text = symbol,
                    color = contentColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp
                )
                Text(
                    text = if (isUp) {
                        "+₺${formatMoney(histPoint.diffAmount)} (+${histPoint.diffPercentage.toInt()}%)"
                    } else if (isDown) {
                        "₺${formatMoney(histPoint.diffAmount)} (${histPoint.diffPercentage.toInt()}%)"
                    } else {
                        "DEĞİŞMEDİ"
                    },
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = contentColor
                )
            }
        }
    }
}
