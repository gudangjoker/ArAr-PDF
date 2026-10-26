package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.data.model.ScreenTab
import com.example.data.pdf.PdfEngine
import com.example.data.util.FileUtils
import com.example.ui.components.AppTopBar
import com.example.ui.viewmodel.PdfViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConvertScreen(
    viewModel: PdfViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val subTab by viewModel.convertSubTab.collectAsState()
    val selectedImages by viewModel.selectedImages.collectAsState()
    val selectedDocx by viewModel.selectedDocx.collectAsState()
    val docxPreview by viewModel.docxPreview.collectAsState()

    var imagePdfTitle by remember { mutableStateOf("Foto_Ke_PDF") }
    var docxPdfTitle by remember { mutableStateOf("Dokumen_Word_Ke_PDF") }

    val coroutineScope = rememberCoroutineScope()

    // Photo picker
    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            viewModel.importImages(uris)
        }
    }

    // Docx picker
    val docxPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { viewModel.importDocx(it) }
    }

    BackHandler {
        viewModel.navigateTo(ScreenTab.HOME)
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            AppTopBar(
                title = "Konversi ke PDF",
                subtitle = if (subTab == 0) "Gambar JPG/PNG ke PDF" else "Dokumen Word (.docx) ke PDF",
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
            // Tabs
            PrimaryTabRow(
                selectedTabIndex = subTab,
                modifier = Modifier.fillMaxWidth()
            ) {
                Tab(
                    selected = subTab == 0,
                    onClick = { viewModel.setConvertSubTab(0) },
                    text = { Text("Gambar (JPG/PNG)", fontWeight = FontWeight.SemiBold) },
                    icon = { Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    modifier = Modifier.testTag("convert_tab_images")
                )
                Tab(
                    selected = subTab == 1,
                    onClick = { viewModel.setConvertSubTab(1) },
                    text = { Text("Word (.docx)", fontWeight = FontWeight.SemiBold) },
                    icon = { Icon(Icons.Default.Description, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    modifier = Modifier.testTag("convert_tab_docx")
                )
            }

            if (subTab == 0) {
                // Image to PDF tab
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Foto Terpilih (${selectedImages.size})",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    if (selectedImages.isNotEmpty()) {
                                        TextButton(onClick = { viewModel.clearSelectedImages() }) {
                                            Text("Hapus Semua")
                                        }
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Button(
                                        onClick = { imagePicker.launch("image/*") },
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("convert_pick_images_btn"),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Icon(Icons.Default.AddPhotoAlternate, contentDescription = null)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(if (selectedImages.isEmpty()) "Pilih Foto" else "Tambah Foto")
                                    }
                                }
                            }
                        }
                    }

                    if (selectedImages.isNotEmpty()) {
                        item {
                            Text(
                                text = "Pratinjau Halaman Foto",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        item {
                            Box(modifier = Modifier.height(300.dp)) {
                                LazyVerticalGrid(
                                    columns = GridCells.Fixed(3),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    itemsIndexed(selectedImages) { idx, uri ->
                                        Box(
                                            modifier = Modifier
                                                .aspectRatio(0.75f)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(Color.White)
                                        ) {
                                            AsyncImage(
                                                model = uri,
                                                contentDescription = "Foto ${idx + 1}",
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                            // Page number badge
                                            Surface(
                                                color = Color.Black.copy(alpha = 0.6f),
                                                shape = RoundedCornerShape(4.dp),
                                                modifier = Modifier
                                                    .align(Alignment.BottomStart)
                                                    .padding(4.dp)
                                            ) {
                                                Text(
                                                    text = "Hal ${idx + 1}",
                                                    color = Color.White,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                                )
                                            }
                                            // Delete icon
                                            IconButton(
                                                onClick = { viewModel.removeSelectedImage(idx) },
                                                modifier = Modifier
                                                    .align(Alignment.TopEnd)
                                                    .size(28.dp)
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(22.dp)
                                                        .background(Color.Red, CircleShape),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(Icons.Default.Close, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        item {
                            OutlinedTextField(
                                value = imagePdfTitle,
                                onValueChange = { imagePdfTitle = it },
                                label = { Text("Nama Dokumen PDF") },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("convert_image_pdf_title_input"),
                                shape = RoundedCornerShape(12.dp)
                            )
                        }

                        item {
                            Button(
                                onClick = { viewModel.executeConvertImagesToPdf(imagePdfTitle) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp)
                                    .testTag("convert_image_execute_btn"),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.PictureAsPdf, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Konversi ${selectedImages.size} Foto ke PDF A4", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            } else {
                // Word to PDF tab
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(
                                    text = "Pilih Berkas Microsoft Word (.docx)",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Button(
                                        onClick = {
                                            docxPicker.launch(arrayOf(
                                                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                                                "application/msword",
                                                "*/*"
                                            ))
                                        },
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("convert_pick_docx_btn"),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Icon(Icons.Default.UploadFile, contentDescription = null)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Pilih File .docx")
                                    }

                                    OutlinedButton(
                                        onClick = {
                                            coroutineScope.launch {
                                                val sampleDocx = PdfEngine.generateSampleDocx(context)
                                                viewModel.importDocx(Uri.fromFile(sampleDocx))
                                            }
                                        },
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("convert_sample_docx_btn"),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Icon(Icons.Default.AutoAwesome, contentDescription = null)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Gunakan Contoh")
                                    }
                                }
                            }
                        }
                    }

                    selectedDocx?.let { docxFile ->
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(44.dp)
                                            .background(Color(0xFF2563EB), RoundedCornerShape(10.dp)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Description,
                                            contentDescription = null,
                                            tint = Color.White
                                        )
                                    }

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = docxFile.name,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "Ukuran: ${FileUtils.formatFileSize(docxFile.length())} • Siap Dikonversi",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }

                        if (docxPreview.isNotBlank()) {
                            item {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                ) {
                                    Column(
                                        modifier = Modifier.padding(14.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                                            Text(
                                                text = "Pratinjau Isi Dokumen Word",
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                        Text(
                                            text = docxPreview,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 6,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }

                        item {
                            OutlinedTextField(
                                value = docxPdfTitle,
                                onValueChange = { docxPdfTitle = it },
                                label = { Text("Nama PDF Hasil Konversi") },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("convert_docx_title_input"),
                                shape = RoundedCornerShape(12.dp)
                            )
                        }

                        item {
                            Button(
                                onClick = { viewModel.executeConvertDocxToPdf(docxPdfTitle) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp)
                                    .testTag("convert_docx_execute_btn"),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.Transform, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Konversi Word (.docx) ke PDF", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}
