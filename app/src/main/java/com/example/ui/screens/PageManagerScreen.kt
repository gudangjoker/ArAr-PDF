package com.example.ui.screens

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import coil.compose.AsyncImage
import com.example.data.model.PageType
import com.example.data.model.PdfPageItem
import com.example.data.model.ScreenTab
import com.example.data.pdf.PdfEngine
import com.example.ui.components.AppTopBar
import com.example.ui.viewmodel.PdfViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PageManagerScreen(
    viewModel: PdfViewModel,
    modifier: Modifier = Modifier
) {
    val activeDoc by viewModel.activeManagerDoc.collectAsState()
    val pageOrder by viewModel.pageOrder.collectAsState()
    val selectedPages by viewModel.selectedPages.collectAsState()

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.insertImagePage(it) }
    }

    BackHandler {
        viewModel.navigateTo(ScreenTab.HOME)
    }

    if (activeDoc == null) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Default.Layers, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
                Text("Pilih dokumen untuk dikelola", style = MaterialTheme.typography.titleMedium)
                Button(onClick = { viewModel.navigateTo(ScreenTab.HOME) }) {
                    Text("Pilih Dokumen")
                }
            }
        }
        return
    }

    val doc = activeDoc!!

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            AppTopBar(
                title = "Kelola Halaman",
                subtitle = "${doc.title} (${pageOrder.size} Halaman)",
                canNavigateBack = true,
                onNavigateBack = { viewModel.navigateTo(ScreenTab.HOME) },
                actions = {
                    Button(
                        onClick = { viewModel.savePageManagerChanges() },
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .testTag("page_manager_save_btn"),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Simpan", fontWeight = FontWeight.Bold)
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
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Actions row: Reorder / Delete / Insert
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Delete button
                        Button(
                            onClick = { viewModel.deleteSelectedPages() },
                            enabled = selectedPages.isNotEmpty() && pageOrder.size > 1,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("page_manager_delete_btn"),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Hapus (${selectedPages.size})")
                        }

                        // Insert Blank Page button
                        OutlinedButton(
                            onClick = { viewModel.insertBlankPage() },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("page_manager_insert_blank_btn"),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("+ Kosong")
                        }

                        // Insert Image Page button
                        OutlinedButton(
                            onClick = { imagePicker.launch("image/*") },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("page_manager_insert_image_btn"),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("+ Foto")
                        }
                    }

                    // Reorder indicator helper
                    Text(
                        text = "Tips: Ketuk halaman untuk memilih, lalu gunakan tombol panah di tiap kartu untuk memindah urutan.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    ) { innerPadding ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 140.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            itemsIndexed(pageOrder, key = { _, item -> item.id }) { index, pageItem ->
                val isSelected = selectedPages.contains(pageItem.id)

                PageGridCard(
                    docFile = doc.file,
                    pageItem = pageItem,
                    displayIndex = index + 1,
                    isSelected = isSelected,
                    canMoveLeft = index > 0,
                    canMoveRight = index < pageOrder.size - 1,
                    onToggleSelect = { viewModel.togglePageSelection(pageItem.id) },
                    onMoveLeft = { viewModel.movePage(index, index - 1) },
                    onMoveRight = { viewModel.movePage(index, index + 1) }
                )
            }
        }
    }
}

@Composable
fun PageGridCard(
    docFile: java.io.File,
    pageItem: PdfPageItem,
    displayIndex: Int,
    isSelected: Boolean,
    canMoveLeft: Boolean,
    canMoveRight: Boolean,
    onToggleSelect: () -> Unit,
    onMoveLeft: () -> Unit,
    onMoveRight: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("page_card_$displayIndex"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        border = if (isSelected) CardDefaults.outlinedCardBorder().copy(
            brush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),
            width = 2.dp
        ) else null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Page Preview Box
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.White)
                    .clickable(onClick = onToggleSelect),
                contentAlignment = Alignment.Center
            ) {
                when (pageItem.type) {
                    PageType.EXISTING -> {
                        val bitmapState = produceState<Bitmap?>(initialValue = null, key1 = pageItem.pageIndex, key2 = docFile.absolutePath) {
                            value = PdfEngine.renderPageBitmap(docFile, pageItem.pageIndex, targetWidth = 320)
                        }
                        val bmp = bitmapState.value
                        if (bmp != null) {
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = "Halaman $displayIndex",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                        }
                    }
                    PageType.IMAGE -> {
                        if (pageItem.imageUri != null) {
                            AsyncImage(
                                model = pageItem.imageUri,
                                contentDescription = "Foto Disisipkan $displayIndex",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Icon(Icons.Default.Image, contentDescription = null, tint = Color(0xFF64748B), modifier = Modifier.size(36.dp))
                        }
                    }
                    PageType.BLANK -> {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.NoteAdd,
                                contentDescription = null,
                                tint = Color(0xFF94A3B8),
                                modifier = Modifier.size(36.dp)
                            )
                            Text(
                                text = "Halaman Kosong",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF64748B)
                            )
                        }
                    }
                }

                // Checkbox badge in top-left
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                        .size(24.dp)
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = 0.4f),
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (isSelected) {
                        Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                    } else {
                        Text(
                            text = "$displayIndex",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Reorder arrows
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onMoveLeft,
                    enabled = canMoveLeft,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Pindah Kiri",
                        tint = if (canMoveLeft) MaterialTheme.colorScheme.primary else Color.LightGray,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Text(
                    text = "Posisi $displayIndex",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                )

                IconButton(
                    onClick = onMoveRight,
                    enabled = canMoveRight,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowForward,
                        contentDescription = "Pindah Kanan",
                        tint = if (canMoveRight) MaterialTheme.colorScheme.primary else Color.LightGray,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
