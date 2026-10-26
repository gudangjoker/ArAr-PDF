package com.example.ui.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.CompressionLevel
import com.example.data.model.ExportFormat
import com.example.data.model.MergeItem
import com.example.data.model.OperationState
import com.example.data.model.PdfDocumentItem
import com.example.data.model.PdfPageItem
import com.example.data.model.ScreenTab
import com.example.data.pdf.PdfEngine
import com.example.data.util.FileUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

class PdfViewModel(application: Application) : AndroidViewModel(application) {

    private val context get() = getApplication<Application>().applicationContext

    private val _currentTab = MutableStateFlow(ScreenTab.HOME)
    val currentTab: StateFlow<ScreenTab> = _currentTab.asStateFlow()

    private val _recentDocuments = MutableStateFlow<List<PdfDocumentItem>>(emptyList())
    val recentDocuments: StateFlow<List<PdfDocumentItem>> = _recentDocuments.asStateFlow()

    private val _operationState = MutableStateFlow<OperationState>(OperationState.Idle)
    val operationState: StateFlow<OperationState> = _operationState.asStateFlow()

    // --- READER STATE ---
    private val _activeReaderDoc = MutableStateFlow<PdfDocumentItem?>(null)
    val activeReaderDoc: StateFlow<PdfDocumentItem?> = _activeReaderDoc.asStateFlow()

    private val _activeReaderPage = MutableStateFlow(0)
    val activeReaderPage: StateFlow<Int> = _activeReaderPage.asStateFlow()

    private val _readerNightMode = MutableStateFlow(false)
    val readerNightMode: StateFlow<Boolean> = _readerNightMode.asStateFlow()

    private val _readerSepiaMode = MutableStateFlow(false)
    val readerSepiaMode: StateFlow<Boolean> = _readerSepiaMode.asStateFlow()

    private val _readerMode = MutableStateFlow(com.example.data.model.ReaderMode.SINGLE_PAGE)
    val readerMode: StateFlow<com.example.data.model.ReaderMode> = _readerMode.asStateFlow()

    private val _docxPreview = MutableStateFlow("")
    val docxPreview: StateFlow<String> = _docxPreview.asStateFlow()

    // --- PAGE MANAGER STATE ---
    private val _activeManagerDoc = MutableStateFlow<PdfDocumentItem?>(null)
    val activeManagerDoc: StateFlow<PdfDocumentItem?> = _activeManagerDoc.asStateFlow()

    private val _pageOrder = MutableStateFlow<List<PdfPageItem>>(emptyList())
    val pageOrder: StateFlow<List<PdfPageItem>> = _pageOrder.asStateFlow()

    private val _selectedPages = MutableStateFlow<Set<String>>(emptySet())
    val selectedPages: StateFlow<Set<String>> = _selectedPages.asStateFlow()

    // --- MERGE STATE ---
    private val _mergeList = MutableStateFlow<List<MergeItem>>(emptyList())
    val mergeList: StateFlow<List<MergeItem>> = _mergeList.asStateFlow()

    // --- CONVERSION STATE ---
    private val _convertSubTab = MutableStateFlow(0) // 0: Images, 1: Docx
    val convertSubTab: StateFlow<Int> = _convertSubTab.asStateFlow()

    private val _selectedImages = MutableStateFlow<List<Uri>>(emptyList())
    val selectedImages: StateFlow<List<Uri>> = _selectedImages.asStateFlow()

    private val _selectedDocx = MutableStateFlow<File?>(null)
    val selectedDocx: StateFlow<File?> = _selectedDocx.asStateFlow()

    // --- COMPRESSION & REVERSE EXPORT STATE ---
    private val _compressExportSubTab = MutableStateFlow(0) // 0: Compress, 1: Export
    val compressExportSubTab: StateFlow<Int> = _compressExportSubTab.asStateFlow()

    private val _compressTargetDoc = MutableStateFlow<PdfDocumentItem?>(null)
    val compressTargetDoc: StateFlow<PdfDocumentItem?> = _compressTargetDoc.asStateFlow()

    private val _selectedCompression = MutableStateFlow(CompressionLevel.RECOMMENDED)
    val selectedCompression: StateFlow<CompressionLevel> = _selectedCompression.asStateFlow()

    private val _exportTargetDoc = MutableStateFlow<PdfDocumentItem?>(null)
    val exportTargetDoc: StateFlow<PdfDocumentItem?> = _exportTargetDoc.asStateFlow()

    private val _selectedExportFormat = MutableStateFlow(ExportFormat.PNG)
    val selectedExportFormat: StateFlow<ExportFormat> = _selectedExportFormat.asStateFlow()

    init {
        PdfEngine.init(context)
        loadDocuments()
    }

    fun navigateTo(tab: ScreenTab) {
        _currentTab.value = tab
    }

    fun dismissOperationDialog() {
        _operationState.value = OperationState.Idle
    }

    fun loadDocuments() {
        viewModelScope.launch {
            val docs = mutableListOf<PdfDocumentItem>()
            
            // Generate sample PDF and DOCX on first run so user has immediate rich content
            val samplePdf = PdfEngine.generateSamplePdf(context)
            val sampleDocx = PdfEngine.generateSampleDocx(context)

            // Scrape exported files & sample files
            val searchDirs = listOf(
                File(context.filesDir, "samples"),
                File(context.filesDir, "exports"),
                File(context.filesDir, "imports"),
                File(context.cacheDir, "imports")
            )

            for (dir in searchDirs) {
                if (dir.exists()) {
                    dir.listFiles()?.forEach { file ->
                        if (file.isFile && file.extension.equals("pdf", ignoreCase = true) && file.length() > 0) {
                            val count = PdfEngine.getPageCount(file)
                            val title = file.nameWithoutExtension.replace("_", " ")
                            docs.add(
                                PdfDocumentItem(
                                    id = file.absolutePath,
                                    title = title,
                                    file = file,
                                    pageCount = count,
                                    fileSizeFormatted = FileUtils.formatFileSize(file.length()),
                                    lastModified = file.lastModified()
                                )
                            )
                        }
                    }
                }
            }

            // Deduplicate by absolute path & sort by most recent
            val sortedDocs = docs.distinctBy { it.file.absolutePath }
                .sortedByDescending { it.lastModified }
            _recentDocuments.value = sortedDocs

            // Set default targets if null
            if (_activeReaderDoc.value == null && sortedDocs.isNotEmpty()) {
                _activeReaderDoc.value = sortedDocs.first()
            }
            if (_compressTargetDoc.value == null && sortedDocs.isNotEmpty()) {
                _compressTargetDoc.value = sortedDocs.first()
            }
            if (_exportTargetDoc.value == null && sortedDocs.isNotEmpty()) {
                _exportTargetDoc.value = sortedDocs.first()
            }
            if (_selectedDocx.value == null && sampleDocx.exists()) {
                _selectedDocx.value = sampleDocx
                _docxPreview.value = PdfEngine.getDocxPreviewText(sampleDocx)
            }
        }
    }

    // --- ACTIONS FOR OPENING MODULES ---

    fun openInReader(doc: PdfDocumentItem) {
        _activeReaderDoc.value = doc
        _activeReaderPage.value = 0
        _currentTab.value = ScreenTab.READER
    }

    fun openInPageManager(doc: PdfDocumentItem) {
        _activeManagerDoc.value = doc
        val pages = (0 until doc.pageCount).map { idx ->
            PdfPageItem(pageIndex = idx, pageNumber = idx + 1)
        }
        _pageOrder.value = pages
        _selectedPages.value = emptySet()
        _currentTab.value = ScreenTab.PAGE_MANAGER
    }

    fun openInCompress(doc: PdfDocumentItem) {
        _compressTargetDoc.value = doc
        _compressExportSubTab.value = 0
        _currentTab.value = ScreenTab.COMPRESS_EXPORT
    }

    fun openInExport(doc: PdfDocumentItem) {
        _exportTargetDoc.value = doc
        _compressExportSubTab.value = 1
        _currentTab.value = ScreenTab.COMPRESS_EXPORT
    }

    fun setConvertSubTab(index: Int) {
        _convertSubTab.value = index
    }

    fun setCompressExportSubTab(index: Int) {
        _compressExportSubTab.value = index
    }

    // --- IMPORTING FILES VIA SAF ---

    fun importPdf(uri: Uri) {
        viewModelScope.launch {
            _operationState.value = OperationState.Loading("Mengimpor berkas PDF...")
            try {
                val tempFile = FileUtils.copyUriToTempFile(context, uri, "imported", "pdf", persistent = true)
                val pageCount = PdfEngine.getPageCount(tempFile)
                val newItem = PdfDocumentItem(
                    id = tempFile.absolutePath,
                    title = FileUtils.getFileName(context, uri).replace(".pdf", ""),
                    file = tempFile,
                    uri = uri,
                    pageCount = pageCount,
                    fileSizeFormatted = FileUtils.formatFileSize(tempFile.length())
                )
                loadDocuments()
                _activeReaderDoc.value = newItem
                _operationState.value = OperationState.Success("PDF berhasil diimpor!", tempFile)
            } catch (e: Exception) {
                _operationState.value = OperationState.Error("Gagal mengimpor PDF: ${e.localizedMessage}")
            }
        }
    }

    fun importDocx(uri: Uri) {
        viewModelScope.launch {
            _operationState.value = OperationState.Loading("Mengimpor file Word...")
            try {
                val tempFile = FileUtils.copyUriToTempFile(context, uri, "doc", "docx")
                _selectedDocx.value = tempFile
                _docxPreview.value = PdfEngine.getDocxPreviewText(tempFile)
                _convertSubTab.value = 1
                _operationState.value = OperationState.Success("File Word siap dikonversi!", tempFile)
            } catch (e: Exception) {
                _operationState.value = OperationState.Error("Gagal mengimpor file Word: ${e.localizedMessage}")
            }
        }
    }

    fun importImages(uris: List<Uri>) {
        if (uris.isEmpty()) return
        val current = _selectedImages.value.toMutableList()
        current.addAll(uris)
        _selectedImages.value = current
        _convertSubTab.value = 0
    }

    fun removeSelectedImage(index: Int) {
        val current = _selectedImages.value.toMutableList()
        if (index in current.indices) {
            current.removeAt(index)
            _selectedImages.value = current
        }
    }

    fun clearSelectedImages() {
        _selectedImages.value = emptyList()
    }

    // --- READER CONTROLS ---

    fun setReaderPage(page: Int) {
        val total = _activeReaderDoc.value?.pageCount ?: 1
        _activeReaderPage.value = page.coerceIn(0, (total - 1).coerceAtLeast(0))
    }

    fun toggleNightMode() {
        _readerNightMode.value = !_readerNightMode.value
        if (_readerNightMode.value) _readerSepiaMode.value = false
    }

    fun toggleSepiaMode() {
        _readerSepiaMode.value = !_readerSepiaMode.value
        if (_readerSepiaMode.value) _readerNightMode.value = false
    }

    fun toggleReaderMode() {
        _readerMode.value = if (_readerMode.value == com.example.data.model.ReaderMode.SINGLE_PAGE) {
            com.example.data.model.ReaderMode.CONTINUOUS_SCROLL
        } else {
            com.example.data.model.ReaderMode.SINGLE_PAGE
        }
    }

    // --- PAGE MANAGER OPERATIONS ---

    fun togglePageSelection(pageId: String) {
        val current = _selectedPages.value.toMutableSet()
        if (current.contains(pageId)) {
            current.remove(pageId)
        } else {
            current.add(pageId)
        }
        _selectedPages.value = current
    }

    fun movePage(fromPos: Int, toPos: Int) {
        val list = _pageOrder.value.toMutableList()
        if (fromPos in list.indices && toPos in list.indices) {
            val item = list.removeAt(fromPos)
            list.add(toPos, item)
            _pageOrder.value = list
        }
    }

    fun deleteSelectedPages() {
        val toDelete = _selectedPages.value
        if (toDelete.isEmpty()) return
        val remaining = _pageOrder.value.filter { !toDelete.contains(it.id) }
        _pageOrder.value = remaining
        _selectedPages.value = emptySet()
    }

    fun insertBlankPage() {
        val current = _pageOrder.value.toMutableList()
        val newPageNumber = (current.maxOfOrNull { it.pageNumber } ?: 0) + 1
        current.add(
            PdfPageItem(
                pageIndex = -1,
                pageNumber = newPageNumber,
                type = com.example.data.model.PageType.BLANK
            )
        )
        _pageOrder.value = current
    }

    fun insertImagePage(uri: Uri) {
        val current = _pageOrder.value.toMutableList()
        val newPageNumber = (current.maxOfOrNull { it.pageNumber } ?: 0) + 1
        current.add(
            PdfPageItem(
                pageIndex = -2,
                pageNumber = newPageNumber,
                type = com.example.data.model.PageType.IMAGE,
                imageUri = uri
            )
        )
        _pageOrder.value = current
    }

    fun savePageManagerChanges() {
        val doc = _activeManagerDoc.value ?: return
        viewModelScope.launch {
            _operationState.value = OperationState.Loading("Menyimpan susunan halaman...")
            val outputFile = FileUtils.createExportFile(context, "Edited_${doc.title.take(15)}", "pdf")
            val newOrder = _pageOrder.value

            val success = PdfEngine.saveManagedPages(context, doc.file, newOrder, outputFile)
            if (success) {
                PdfEngine.clearCache()
                loadDocuments()
                val updatedCount = PdfEngine.getPageCount(outputFile)
                val newItem = PdfDocumentItem(
                    id = outputFile.absolutePath,
                    title = outputFile.nameWithoutExtension.replace("_", " "),
                    file = outputFile,
                    pageCount = updatedCount,
                    fileSizeFormatted = FileUtils.formatFileSize(outputFile.length())
                )
                _activeReaderDoc.value = newItem
                _operationState.value = OperationState.Success("Perubahan halaman berhasil disimpan!", outputFile)
            } else {
                _operationState.value = OperationState.Error("Gagal menyimpan perubahan halaman.")
            }
        }
    }

    // --- MERGE OPERATIONS ---

    fun addPdfsToMerge(uris: List<Uri>) {
        viewModelScope.launch {
            _operationState.value = OperationState.Loading("Memuat berkas untuk digabungkan...")
            val current = _mergeList.value.toMutableList()
            var rejected = 0
            for (uri in uris) {
                var tempFile: File? = null
                try {
                    tempFile = FileUtils.copyUriToTempFile(context, uri, "merge", "pdf")
                    val count = PdfEngine.getPageCount(tempFile)
                    if (count <= 0) {
                        tempFile.delete()
                        rejected++
                        continue
                    }
                    current.add(
                        MergeItem(
                            id = "${tempFile.absolutePath}_${System.currentTimeMillis()}",
                            name = FileUtils.getFileName(context, uri).removeSuffix(".pdf"),
                            file = tempFile,
                            pageCount = count,
                            sizeFormatted = FileUtils.formatFileSize(tempFile.length())
                        )
                    )
                } catch (e: Exception) {
                    tempFile?.delete()
                    rejected++
                }
            }
            _mergeList.value = current
            _operationState.value = if (rejected > 0) {
                OperationState.Error("$rejected berkas tidak dapat dibaca sebagai PDF dan dilewati.")
            } else {
                OperationState.Idle
            }
        }
    }

    fun addDocumentToMerge(doc: PdfDocumentItem) {
        val current = _mergeList.value.toMutableList()
        current.add(
            MergeItem(
                id = "${doc.id}_${System.currentTimeMillis()}",
                name = doc.title,
                file = doc.file,
                pageCount = doc.pageCount,
                sizeFormatted = doc.fileSizeFormatted
            )
        )
        _mergeList.value = current
    }

    fun moveMergeItem(fromPos: Int, toPos: Int) {
        val list = _mergeList.value.toMutableList()
        if (fromPos in list.indices && toPos in list.indices) {
            val item = list.removeAt(fromPos)
            list.add(toPos, item)
            _mergeList.value = list
        }
    }

    fun removeMergeItem(index: Int) {
        val list = _mergeList.value.toMutableList()
        if (index in list.indices) {
            list.removeAt(index)
            _mergeList.value = list
        }
    }

    fun clearMergeList() {
        _mergeList.value = emptyList()
    }

    fun executeMerge(customTitle: String = "Merged_Document") {
        val items = _mergeList.value
        if (items.size < 2) {
            _operationState.value = OperationState.Error("Pilih minimal 2 file PDF untuk digabungkan.")
            return
        }

        viewModelScope.launch {
            _operationState.value = OperationState.Loading("Menggabungkan ${items.size} file PDF...")
            val outputFile = FileUtils.createExportFile(context, customTitle.replace(" ", "_"), "pdf")
            val files = items.map { it.file }
            val success = PdfEngine.mergePdfFiles(files, outputFile)

            if (success) {
                loadDocuments()
                val totalPages = PdfEngine.getPageCount(outputFile)
                val newItem = PdfDocumentItem(
                    id = outputFile.absolutePath,
                    title = outputFile.nameWithoutExtension.replace("_", " "),
                    file = outputFile,
                    pageCount = totalPages,
                    fileSizeFormatted = FileUtils.formatFileSize(outputFile.length())
                )
                _activeReaderDoc.value = newItem
                _operationState.value = OperationState.Success(
                    "Berhasil menggabungkan ${items.size} dokumen menjadi $totalPages halaman!",
                    outputFile
                )
            } else {
                _operationState.value = OperationState.Error("Gagal menggabungkan berkas PDF.")
            }
        }
    }

    // --- CONVERSION OPERATIONS ---

    fun executeConvertImagesToPdf(customName: String = "Foto_Ke_PDF") {
        val uris = _selectedImages.value
        if (uris.isEmpty()) {
            _operationState.value = OperationState.Error("Pilih minimal satu gambar untuk dikonversi.")
            return
        }
        viewModelScope.launch {
            _operationState.value = OperationState.Loading("Mengonversi ${uris.size} gambar ke PDF...")
            val outputFile = FileUtils.createExportFile(context, customName.replace(" ", "_"), "pdf")
            val success = PdfEngine.convertImagesToPdf(context, uris, outputFile)

            if (success) {
                loadDocuments()
                val pageCount = PdfEngine.getPageCount(outputFile)
                val newItem = PdfDocumentItem(
                    id = outputFile.absolutePath,
                    title = outputFile.nameWithoutExtension.replace("_", " "),
                    file = outputFile,
                    pageCount = pageCount,
                    fileSizeFormatted = FileUtils.formatFileSize(outputFile.length())
                )
                _activeReaderDoc.value = newItem
                _operationState.value = OperationState.Success(
                    "Berhasil mengonversi ${uris.size} foto menjadi PDF ($pageCount halaman)!",
                    outputFile
                )
            } else {
                _operationState.value = OperationState.Error("Gagal mengonversi gambar ke PDF.")
            }
        }
    }

    fun executeConvertDocxToPdf(customName: String = "Laporan_Docx_Ke_PDF") {
        val docxFile = _selectedDocx.value
        if (docxFile == null || !docxFile.exists()) {
            _operationState.value = OperationState.Error("Pilih file Word (.docx) terlebih dahulu.")
            return
        }
        viewModelScope.launch {
            _operationState.value = OperationState.Loading("Mengonversi berkas Word (.docx) ke PDF...")
            val outputFile = FileUtils.createExportFile(context, customName.replace(" ", "_"), "pdf")
            val success = PdfEngine.convertDocxToPdf(context, docxFile, outputFile)

            if (success) {
                loadDocuments()
                val pageCount = PdfEngine.getPageCount(outputFile)
                val newItem = PdfDocumentItem(
                    id = outputFile.absolutePath,
                    title = outputFile.nameWithoutExtension.replace("_", " "),
                    file = outputFile,
                    pageCount = pageCount,
                    fileSizeFormatted = FileUtils.formatFileSize(outputFile.length())
                )
                _activeReaderDoc.value = newItem
                _operationState.value = OperationState.Success(
                    "Berhasil mengonversi Word (.docx) ke PDF ($pageCount halaman)!",
                    outputFile
                )
            } else {
                _operationState.value = OperationState.Error("Gagal mengonversi Word (.docx) ke PDF.")
            }
        }
    }

    // --- COMPRESS OPERATIONS ---

    fun setCompressTarget(doc: PdfDocumentItem) {
        _compressTargetDoc.value = doc
    }

    fun setCompressionLevel(level: CompressionLevel) {
        _selectedCompression.value = level
    }

    fun executeCompression() {
        val target = _compressTargetDoc.value
        if (target == null) {
            _operationState.value = OperationState.Error("Pilih berkas PDF yang ingin dikompresi.")
            return
        }

        viewModelScope.launch {
            _operationState.value = OperationState.Loading("Mengompresi PDF (${_selectedCompression.value.title})...")
            val outputFile = FileUtils.createExportFile(context, "Compressed_${target.title.take(15)}", "pdf")
            val success = PdfEngine.compressPdf(target.file, _selectedCompression.value, outputFile)

            if (success) {
                loadDocuments()
                val oldSize = target.file.length()
                val newSize = outputFile.length()
                val savings = if (oldSize > 0) ((oldSize - newSize).toFloat() / oldSize * 100).toInt().coerceAtLeast(0) else 0

                val msg = "Kompresi selesai. Ukuran: ${FileUtils.formatFileSize(oldSize)} -> ${FileUtils.formatFileSize(newSize)} (Hemat $savings%)"
                _operationState.value = OperationState.Success(msg, outputFile)
            } else {
                _operationState.value = OperationState.Error("Gagal mengompresi PDF.")
            }
        }
    }

    // --- REVERSE EXPORT OPERATIONS ---

    fun setExportTarget(doc: PdfDocumentItem) {
        _exportTargetDoc.value = doc
    }

    fun setExportFormat(format: ExportFormat) {
        _selectedExportFormat.value = format
    }

    fun executeExport() {
        val target = _exportTargetDoc.value
        if (target == null) {
            _operationState.value = OperationState.Error("Pilih berkas PDF untuk diekspor.")
            return
        }

        viewModelScope.launch {
            val format = _selectedExportFormat.value
            _operationState.value = OperationState.Loading("Mengekspor halaman PDF ke format ${format.extension.uppercase()}...")

            if (format == ExportFormat.TXT) {
                val txtFile = FileUtils.createExportFile(context, "Text_${target.title.take(15)}", "txt")
                val success = PdfEngine.extractTextFromPdf(target.file, txtFile)
                if (success) {
                    _operationState.value = OperationState.Success(
                        "Teks dari PDF berhasil diekstrak (${FileUtils.formatFileSize(txtFile.length())})!",
                        txtFile,
                        listOf(txtFile)
                    )
                } else {
                    _operationState.value = OperationState.Error("Gagal mengekstrak teks dari PDF.")
                }
            } else {
                val exported = PdfEngine.exportPagesToImages(context, target.file, emptyList(), format)
                if (exported.isNotEmpty()) {
                    _operationState.value = OperationState.Success(
                        "Berhasil mengekspor ${exported.size} halaman menjadi gambar ${format.extension.uppercase()}!",
                        exported.first(),
                        exported
                    )
                } else {
                    _operationState.value = OperationState.Error("Gagal mengekspor halaman ke gambar.")
                }
            }
        }
    }
}
