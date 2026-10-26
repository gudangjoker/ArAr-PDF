package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import com.example.data.model.MergeItem
import com.example.data.model.ScreenTab
import com.example.ui.components.AppTopBar
import com.example.ui.viewmodel.PdfViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MergePdfScreen(
    viewModel: PdfViewModel,
    modifier: Modifier = Modifier
) {
    val mergeList by viewModel.mergeList.collectAsState()
    val availableDocs by viewModel.recentDocuments.collectAsState()

    var mergeTitle by remember { mutableStateOf("Gabungan_Dokumen") }
    var showAddAvailableDialog by remember { mutableStateOf(false) }

    // SAF picker for multiple PDFs
    val multiPdfPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            viewModel.addPdfsToMerge(uris)
        }
    }

    BackHandler {
        viewModel.navigateTo(ScreenTab.HOME)
    }

    val totalPages = mergeList.sumOf { it.pageCount }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            AppTopBar(
                title = "Kombinasi File (Merge)",
                subtitle = "${mergeList.size} file dipilih ($totalPages halaman)",
                canNavigateBack = true,
                onNavigateBack = { viewModel.navigateTo(ScreenTab.HOME) },
                actions = {
                    if (mergeList.isNotEmpty()) {
                        IconButton(
                            onClick = { viewModel.clearMergeList() },
                            modifier = Modifier.testTag("merge_clear_all_btn")
                        ) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = "Kosongkan")
                        }
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
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = mergeTitle,
                        onValueChange = { mergeTitle = it },
                        label = { Text("Nama Berkas Hasil Penggabungan") },
                        placeholder = { Text("Contoh: Berkas_Lengkap_Final") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("merge_title_input"),
                        shape = RoundedCornerShape(12.dp)
                    )

                    Button(
                        onClick = { viewModel.executeMerge(mergeTitle) },
                        enabled = mergeList.size >= 2,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("merge_execute_btn"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.MergeType, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (mergeList.size >= 2) "Gabungkan ${mergeList.size} File ($totalPages Halaman)"
                            else "Pilih Minimal 2 File PDF",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Add File Actions Card
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
                            text = "Tambah Berkas PDF",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = { multiPdfPicker.launch(arrayOf("application/pdf")) },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("merge_pick_saf_btn"),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.FileOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Pilih dari HP")
                            }

                            OutlinedButton(
                                onClick = { showAddAvailableDialog = true },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("merge_pick_available_btn"),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Dari Aplikasi")
                            }
                        }
                    }
                }
            }

            // List of chosen items
            if (mergeList.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CallMerge,
                                contentDescription = null,
                                modifier = Modifier.size(56.dp),
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                            )
                            Text(
                                text = "Daftar Berkas Masih Kosong",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Pilih 2 atau lebih file PDF untuk menggabungkannya ke dalam satu file utuh.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                item {
                    Text(
                        text = "Urutan Penggabungan Dokumen (${mergeList.size})",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                itemsIndexed(mergeList, key = { _, item -> item.id }) { index, item ->
                    MergeItemCard(
                        item = item,
                        index = index,
                        canMoveUp = index > 0,
                        canMoveDown = index < mergeList.size - 1,
                        onMoveUp = { viewModel.moveMergeItem(index, index - 1) },
                        onMoveDown = { viewModel.moveMergeItem(index, index + 1) },
                        onRemove = { viewModel.removeMergeItem(index) }
                    )
                }
            }
        }
    }

    // Dialog for picking from available docs
    if (showAddAvailableDialog) {
        AlertDialog(
            onDismissRequest = { showAddAvailableDialog = false },
            title = { Text("Pilih Dokumen dari Aplikasi", fontWeight = FontWeight.Bold) },
            text = {
                if (availableDocs.isEmpty()) {
                    Text("Belum ada dokumen yang tersedia di aplikasi.")
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 300.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(availableDocs.size) { idx ->
                            val doc = availableDocs[idx]
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(doc.title, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text("${doc.pageCount} Hal • ${doc.fileSizeFormatted}", style = MaterialTheme.typography.labelSmall)
                                    }
                                    Button(
                                        onClick = {
                                            viewModel.addDocumentToMerge(doc)
                                            showAddAvailableDialog = false
                                        },
                                        modifier = Modifier.testTag("dialog_add_doc_${idx}")
                                    ) {
                                        Text("Tambah")
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAddAvailableDialog = false }) {
                    Text("Tutup")
                }
            }
        )
    }
}

@Composable
fun MergeItemCard(
    item: MergeItem,
    index: Int,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("merge_item_$index"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Index badge
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "${index + 1}",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.labelMedium
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${item.pageCount} Halaman • ${item.sizeFormatted}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Move buttons & delete
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onMoveUp,
                    enabled = canMoveUp,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Default.ArrowUpward,
                        contentDescription = "Pindah Ke Atas",
                        tint = if (canMoveUp) MaterialTheme.colorScheme.primary else Color.LightGray,
                        modifier = Modifier.size(18.dp)
                    )
                }

                IconButton(
                    onClick = onMoveDown,
                    enabled = canMoveDown,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Default.ArrowDownward,
                        contentDescription = "Pindah Ke Bawah",
                        tint = if (canMoveDown) MaterialTheme.colorScheme.primary else Color.LightGray,
                        modifier = Modifier.size(18.dp)
                    )
                }

                IconButton(
                    onClick = onRemove,
                    modifier = Modifier
                        .size(32.dp)
                        .testTag("merge_item_remove_$index")
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Hapus",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
