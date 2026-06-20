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
            if (ocrState is GeminiOcrState.Idle || ocrState is GeminiOcrState.Error) {
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
            when (val currentOcr = ocrState) {
                is GeminiOcrState.Loading -> {
                    ScanningProgressOverlay(
                        capturedPhoto = capturedPhotoState
                    )
                }
                is GeminiOcrState.Success -> {
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
                                ocrState = currentOcr,
                                onDismissError = { viewModel.resetOcrState() }
                            )
                            1 -> HistoryScreen(
                                receipts = receipts,
                                onReceiptClick = { selectedReceiptForDetail = it }
                            )
                            2 -> AnalyticsScreen(
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
fun ReceiptRowItem(receipt: Receipt, onClick: () -> Unit) {
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
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
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
                                (rec.location ?: "").contains(searchQuery, ignoreCase = true)

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
            label = { Text("Mağaza veya konuma göre ara...") },
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
                    ReceiptRowItem(receipt = receipt, onClick = { onReceiptClick(receipt) })
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

// Receipt Detail Bottom Sheet Dialog
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReceiptDetailBottomSheet(
    receipt: Receipt,
    onDismiss: () -> Unit,
    onDelete: () -> Unit
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

                IconButton(
                    onClick = onDelete,
                    colors = IconButtonDefaults.iconButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(Icons.Default.Delete, contentDescription = "Sil")
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
        receipts.groupBy { it.storeName.trim() }
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
                            Text("Ömür Boyu Toplam:", style = MaterialTheme.typography.bodyMedium)
                            Text("₺${formatMoney(totalSpendSum)}", fontWeight = FontWeight.Bold)
                        }
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

        // Category Distribution Donut Chart
        if (categoryTotals.isNotEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Kategorilere Göre Dağılım",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // Donut Chart Canvas
                            CategoryDonutChart(
                                categoryTotals = categoryTotals,
                                totalWidth = totalSpendSum
                            )

                            // Legend list
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                categoryTotals.take(4).forEach { (cat, amount) ->
                                    val percent = if (totalSpendSum > 0) ((amount / totalSpendSum) * 100).toInt() else 0
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically
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
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
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
