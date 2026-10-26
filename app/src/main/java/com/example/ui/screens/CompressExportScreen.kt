package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.CompressionLevel
import com.example.data.model.ExportFormat
import com.example.data.model.PdfDocumentItem
import com.example.data.model.ScreenTab
import com.example.ui.components.AppTopBar
import com.example.ui.viewmodel.PdfViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompressExportScreen(
    viewModel: PdfViewModel,
    modifier: Modifier = Modifier
) {
    val subTab by viewModel.compressExportSubTab.collectAsState()
    val availableDocs by viewModel.recentDocuments.collectAsState()
    val compressTarget by viewModel.compressTargetDoc.collectAsState()
    val selectedCompression by viewModel.selectedCompression.collectAsState()
    val exportTarget by viewModel.exportTargetDoc.collectAsState()
    val selectedFormat by viewModel.selectedExportFormat.collectAsState()

    var showDocSelectorDialog by remember { mutableStateOf(false) }
    var selectingForTab by remember { mutableIntStateOf(0) }

    val safPdfPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            viewModel.importPdf(it)
            showDocSelectorDialog = false
        }
    }

    BackHandler {
        viewModel.navigateTo(ScreenTab.HOME)
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            AppTopBar(
                title = "Kompresi & Ekspor Balik",
                subtitle = if (subTab == 0) "Mengecilkan ukuran berkas PDF" else "Ekspor PDF ke gambar / teks",
                canNavigateBack = true,
                onNavigateBack = { viewModel.navigateTo(ScreenTab.HOME) }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            PrimaryTabRow(
                selectedTabIndex = subTab,
                modifier = Modifier.fillMaxWidth()
            ) {
                Tab(
                    selected = subTab == 0,
                    onClick = { viewModel.setCompressExportSubTab(0) },
                    text = { Text("Kompresi PDF", fontWeight = FontWeight.SemiBold) },
                    icon = { Icon(Icons.Default.Compress, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    modifier = Modifier.testTag("tab_compress")
                )
                Tab(
                    selected = subTab == 1,
                    onClick = { viewModel.setCompressExportSubTab(1) },
                    text = { Text("Ekspor Balik", fontWeight = FontWeight.SemiBold) },
                    icon = { Icon(Icons.Default.Output, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    modifier = Modifier.testTag("tab_export")
                )
            }

            if (subTab == 0) {
                // Compress Tab
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Document picker card
                    item {
                        SelectedDocumentBanner(
                            title = "Dokumen yang Akan Dikompresi",
                            selectedDoc = compressTarget,
                            onPickDoc = {
                                selectingForTab = 0
                                showDocSelectorDialog = true
                            }
                        )
                    }

                    // Compression level selector
                    item {
                        Text(
                            text = "Tingkat Kompresi Dokumen",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    items(CompressionLevel.values().size) { idx ->
                        val level = CompressionLevel.values()[idx]
                        val isSelected = selectedCompression == level

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.setCompressionLevel(level) }
                                .testTag("compress_level_${level.name}"),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            ),
                            border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = isSelected,
                                    onClick = { viewModel.setCompressionLevel(level) }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = level.title,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = level.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    item {
                        Button(
                            onClick = { viewModel.executeCompression() },
                            enabled = compressTarget != null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("compress_execute_btn"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Compress, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Mulai Kompresi Dokumen", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } else {
                // Reverse Export Tab
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item {
                        SelectedDocumentBanner(
                            title = "Dokumen yang Akan Diekspor",
                            selectedDoc = exportTarget,
                            onPickDoc = {
                                selectingForTab = 1
                                showDocSelectorDialog = true
                            }
                        )
                    }

                    item {
                        Text(
                            text = "Pilih Format Ekspor",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            FormatOptionCard(
                                title = "Gambar PNG",
                                subtitle = "Kualitas maksimal tanpa kompresi",
                                isSelected = selectedFormat == ExportFormat.PNG,
                                onClick = { viewModel.setExportFormat(ExportFormat.PNG) },
                                modifier = Modifier.weight(1f)
                            )
                            FormatOptionCard(
                                title = "Gambar JPG",
                                subtitle = "Ukuran ringkas untuk dibagikan",
                                isSelected = selectedFormat == ExportFormat.JPG,
                                onClick = { viewModel.setExportFormat(ExportFormat.JPG) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    item {
                        FormatOptionCard(
                            title = "Ekstrak Teks (.TXT)",
                            subtitle = "Salin seluruh teks isi dokumen PDF ke dalam berkas teks biasa",
                            isSelected = selectedFormat == ExportFormat.TXT,
                            onClick = { viewModel.setExportFormat(ExportFormat.TXT) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    item {
                        Button(
                            onClick = { viewModel.executeExport() },
                            enabled = exportTarget != null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("export_execute_btn"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Output, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Ekspor ke Format ${selectedFormat.extension.uppercase()}",
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }

    // Dialog for picking target document
    if (showDocSelectorDialog) {
        AlertDialog(
            onDismissRequest = { showDocSelectorDialog = false },
            title = { Text("Pilih Dokumen PDF", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = { safPdfPicker.launch(arrayOf("application/pdf")) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("dialog_pick_storage_pdf_btn"),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.FileOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Buka File PDF dari HP")
                    }

                    if (availableDocs.isEmpty()) {
                        Text("Belum ada dokumen lain yang tersedia di aplikasi.")
                    } else {
                        Text(
                            text = "Atau pilih dokumen yang sudah ada:",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 240.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                        items(availableDocs.size) { idx ->
                            val doc = availableDocs[idx]
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (selectingForTab == 0) {
                                            viewModel.setCompressTarget(doc)
                                        } else {
                                            viewModel.setExportTarget(doc)
                                        }
                                        showDocSelectorDialog = false
                                    }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(doc.title, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text("${doc.pageCount} Hal • ${doc.fileSizeFormatted}", style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
            confirmButton = {
                TextButton(onClick = { showDocSelectorDialog = false }) {
                    Text("Tutup")
                }
            }
        )
    }
}

@Composable
fun SelectedDocumentBanner(
    title: String,
    selectedDoc: PdfDocumentItem?,
    onPickDoc: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                TextButton(onClick = onPickDoc) {
                    Text("Ganti Dokumen")
                }
            }

            if (selectedDoc != null) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(Color(0xFFFEE2E2), RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = Color(0xFFDC2626))
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = selectedDoc.title,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${selectedDoc.pageCount} Halaman • Ukuran Asli: ${selectedDoc.fileSizeFormatted}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                Button(
                    onClick = onPickDoc,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Pilih Dokumen PDF")
                }
            }
        }
    }
}

@Composable
fun FormatOptionCard(
    title: String,
    subtitle: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .clickable(onClick = onClick)
            .testTag("format_card_${title.replace(" ", "_")}"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
