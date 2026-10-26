package com.example.ui.screens

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ScreenTab
import com.example.data.pdf.PdfEngine
import com.example.data.util.FileUtils
import com.example.ui.components.AppTopBar
import com.example.ui.viewmodel.PdfViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    viewModel: PdfViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activeDoc by viewModel.activeReaderDoc.collectAsState()
    val activePage by viewModel.activeReaderPage.collectAsState()
    val isNightMode by viewModel.readerNightMode.collectAsState()
    val isSepiaMode by viewModel.readerSepiaMode.collectAsState()
    val readerMode by viewModel.readerMode.collectAsState()

    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    var showThumbnailSheet by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()

    BackHandler {
        viewModel.navigateTo(ScreenTab.HOME)
    }

    if (activeDoc == null) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
                Text("Tidak ada dokumen yang dipilih", style = MaterialTheme.typography.titleMedium)
                Button(onClick = { viewModel.navigateTo(ScreenTab.HOME) }) {
                    Text("Pilih Dokumen")
                }
            }
        }
        return
    }

    val doc = activeDoc!!
    val totalPages = (doc.pageCount).coerceAtLeast(1)

    // Continuous mode: keep the list and the active page (slider, prev/next, thumbnails) in sync
    val listState = rememberLazyListState()
    LaunchedEffect(readerMode) {
        if (readerMode == com.example.data.model.ReaderMode.CONTINUOUS_SCROLL) {
            listState.scrollToItem(viewModel.activeReaderPage.value.coerceIn(0, totalPages - 1))
            snapshotFlow { listState.firstVisibleItemIndex }.collect { viewModel.setReaderPage(it) }
        }
    }
    LaunchedEffect(activePage) {
        if (readerMode == com.example.data.model.ReaderMode.CONTINUOUS_SCROLL &&
            listState.firstVisibleItemIndex != activePage
        ) {
            listState.scrollToItem(activePage.coerceIn(0, totalPages - 1))
        }
    }

    // Reset zoom when page changes
    LaunchedEffect(activePage) {
        scale = 1f
        offset = Offset.Zero
    }

    // Color filter for night/sepia modes
    val colorFilter = remember(isNightMode, isSepiaMode) {
        when {
            isNightMode -> {
                // Invert colors
                val matrix = ColorMatrix(
                    floatArrayOf(
                        -1f, 0f, 0f, 0f, 255f,
                        0f, -1f, 0f, 0f, 255f,
                        0f, 0f, -1f, 0f, 255f,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
                ColorFilter.colorMatrix(matrix)
            }
            isSepiaMode -> {
                // Warm Sepia
                val matrix = ColorMatrix(
                    floatArrayOf(
                        0.393f, 0.769f, 0.189f, 0f, 0f,
                        0.349f, 0.686f, 0.168f, 0f, 0f,
                        0.272f, 0.534f, 0.131f, 0f, 0f,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
                ColorFilter.colorMatrix(matrix)
            }
            else -> null
        }
    }

    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        val newScale = (scale * zoomChange).coerceIn(1f, 4f)
        scale = newScale
        if (newScale > 1f) {
            val maxOffsetX = (newScale - 1f) * containerSize.width / 2f
            val maxOffsetY = (newScale - 1f) * containerSize.height / 2f
            offset = Offset(
                x = (offset.x + panChange.x).coerceIn(-maxOffsetX, maxOffsetX),
                y = (offset.y + panChange.y).coerceIn(-maxOffsetY, maxOffsetY)
            )
        } else {
            offset = Offset.Zero
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            AppTopBar(
                title = doc.title,
                subtitle = "Halaman ${activePage + 1} dari $totalPages",
                canNavigateBack = true,
                onNavigateBack = { viewModel.navigateTo(ScreenTab.HOME) },
                actions = {
                    IconButton(
                        onClick = { viewModel.toggleReaderMode() },
                        modifier = Modifier.testTag("reader_toggle_mode_btn")
                    ) {
                        Icon(
                            imageVector = if (readerMode == com.example.data.model.ReaderMode.SINGLE_PAGE) Icons.Default.ViewAgenda else Icons.Default.MenuBook,
                            contentDescription = "Ganti Mode Baca"
                        )
                    }
                    IconButton(
                        onClick = { viewModel.toggleNightMode() },
                        modifier = Modifier.testTag("reader_night_mode_btn")
                    ) {
                        Icon(
                            imageVector = if (isNightMode) Icons.Default.LightMode else Icons.Default.DarkMode,
                            contentDescription = "Mode Gelap",
                            tint = if (isNightMode) Color(0xFFFBBF24) else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    IconButton(
                        onClick = { viewModel.toggleSepiaMode() },
                        modifier = Modifier.testTag("reader_sepia_mode_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoStories,
                            contentDescription = "Mode Sepia",
                            tint = if (isSepiaMode) Color(0xFFD97706) else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    IconButton(
                        onClick = { FileUtils.shareFile(context, doc.file) },
                        modifier = Modifier.testTag("reader_share_btn")
                    ) {
                        Icon(Icons.Default.Share, contentDescription = "Bagikan")
                    }
                }
            )
        },
        bottomBar = {
            Surface(
                tonalElevation = 8.dp,
                shadowElevation = 8.dp,
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    // Page Slider
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "${activePage + 1}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Slider(
                            value = activePage.toFloat(),
                            onValueChange = { viewModel.setReaderPage(it.toInt()) },
                            valueRange = 0f..(totalPages - 1).coerceAtLeast(1).toFloat(),
                            steps = (totalPages - 2).coerceAtLeast(0),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("reader_page_slider")
                        )
                        Text(
                            text = "$totalPages",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Navigation bar buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { viewModel.setReaderPage(activePage - 1) },
                            enabled = activePage > 0,
                            modifier = Modifier.testTag("reader_prev_page_btn")
                        ) {
                            Icon(Icons.Default.ChevronLeft, contentDescription = "Halaman Sebelumnya")
                        }

                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            modifier = Modifier.clickable { showThumbnailSheet = !showThumbnailSheet }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(Icons.Default.ViewCarousel, contentDescription = null, modifier = Modifier.size(16.dp))
                                Text(
                                    text = if (showThumbnailSheet) "Tutup Panel" else "Lihat Semua Halaman",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        IconButton(
                            onClick = { viewModel.setReaderPage(activePage + 1) },
                            enabled = activePage < totalPages - 1,
                            modifier = Modifier.testTag("reader_next_page_btn")
                        ) {
                            Icon(Icons.Default.ChevronRight, contentDescription = "Halaman Selanjutnya")
                        }
                    }

                    // Thumbnails bottom strip (shown when toggled)
                    if (showThumbnailSheet) {
                        Spacer(modifier = Modifier.height(8.dp))
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            contentPadding = PaddingValues(horizontal = 4.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(totalPages) { pageIdx ->
                                val isCurrent = pageIdx == activePage
                                PageThumbnailItem(
                                    file = doc.file,
                                    pageIndex = pageIdx,
                                    isSelected = isCurrent,
                                    onClick = {
                                        viewModel.setReaderPage(pageIdx)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        if (readerMode == com.example.data.model.ReaderMode.CONTINUOUS_SCROLL) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .background(if (isNightMode) Color(0xFF0F172A) else Color(0xFFF1F5F9))
                    .testTag("reader_continuous_scroll_list"),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(totalPages) { pageIdx ->
                    val pageBitmapState = produceState<Bitmap?>(initialValue = null, key1 = pageIdx, key2 = doc.file.absolutePath) {
                        value = PdfEngine.renderPageBitmap(doc.file, pageIdx, targetWidth = 1080)
                    }
                    val bmp = pageBitmapState.value

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White)
                    ) {
                        Column {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFFF8FAFC))
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = "Halaman ${pageIdx + 1} dari $totalPages",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFF64748B),
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            if (bmp != null) {
                                Image(
                                    bitmap = bmp.asImageBitmap(),
                                    contentDescription = "Halaman ${pageIdx + 1}",
                                    colorFilter = colorFilter,
                                    contentScale = ContentScale.FillWidth,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(300.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(32.dp), strokeWidth = 2.dp)
                                }
                            }
                        }
                    }
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .background(if (isNightMode) Color(0xFF0F172A) else Color(0xFFF1F5F9))
                    .onSizeChanged { containerSize = it }
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onDoubleTap = {
                                scale = if (scale > 1.2f) 1f else 2.2f
                                offset = Offset.Zero
                            }
                        )
                    }
                    .transformable(state = transformState),
                contentAlignment = Alignment.Center
            ) {
                // Render active page bitmap
                val pageBitmapState = produceState<Bitmap?>(initialValue = null, key1 = activePage, key2 = doc.file.absolutePath) {
                    value = PdfEngine.renderPageBitmap(doc.file, activePage, targetWidth = 1080)
                }

                // Sharper re-render when zoomed in, while the 1080px version stays visible meanwhile
                val hiRes = scale > 1.5f
                val hiBitmapState = produceState<Bitmap?>(initialValue = null, key1 = activePage, key2 = doc.file.absolutePath, key3 = hiRes) {
                    value = if (hiRes) PdfEngine.renderPageBitmap(doc.file, activePage, targetWidth = 2160) else null
                }
                val pageBitmap = hiBitmapState.value ?: pageBitmapState.value

                if (pageBitmap != null) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth(0.94f)
                            .graphicsLayer(
                                scaleX = scale,
                                scaleY = scale,
                                translationX = offset.x,
                                translationY = offset.y
                            ),
                        shape = RoundedCornerShape(8.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White)
                    ) {
                        Image(
                            bitmap = pageBitmap.asImageBitmap(),
                            contentDescription = "Halaman ${activePage + 1}",
                            colorFilter = colorFilter,
                            contentScale = ContentScale.FillWidth,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                } else {
                    CircularProgressIndicator(
                        modifier = Modifier.size(48.dp),
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                // Floating Zoom reset button when zoomed in
                if (scale > 1.05f) {
                    FloatingActionButton(
                        onClick = {
                            scale = 1f
                            offset = Offset.Zero
                        },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(16.dp)
                            .size(40.dp)
                            .testTag("reader_reset_zoom_btn"),
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ) {
                        Icon(Icons.Default.ZoomOutMap, contentDescription = "Reset Zoom", modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun PageThumbnailItem(
    file: java.io.File,
    pageIndex: Int,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val thumbnailState = produceState<Bitmap?>(initialValue = null, key1 = pageIndex, key2 = file.absolutePath) {
        value = PdfEngine.renderPageBitmap(file, pageIndex, targetWidth = 160)
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(2.dp)
    ) {
        Box(
            modifier = Modifier
                .size(width = 56.dp, height = 76.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color.White)
                .border(
                    width = if (isSelected) 2.5.dp else 1.dp,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else Color(0xFFCBD5E1),
                    shape = RoundedCornerShape(6.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            val bmp = thumbnailState.value
            if (bmp != null) {
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            }
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = "${pageIndex + 1}",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        )
    }
}
