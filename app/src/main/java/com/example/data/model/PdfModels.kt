package com.example.data.model

import android.net.Uri
import java.io.File
import java.util.UUID

data class PdfDocumentItem(
    val id: String,
    val title: String,
    val file: File,
    val uri: Uri? = null,
    val pageCount: Int = 0,
    val fileSizeFormatted: String = "",
    val lastModified: Long = System.currentTimeMillis()
)

enum class PageType {
    EXISTING,
    BLANK,
    IMAGE
}

data class PdfPageItem(
    val id: String = UUID.randomUUID().toString(),
    val pageIndex: Int, // >= 0 for existing page in source file
    val pageNumber: Int,
    val type: PageType = if (pageIndex >= 0) PageType.EXISTING else PageType.BLANK,
    val imageUri: Uri? = null,
    var isSelected: Boolean = false
)

data class MergeItem(
    val id: String,
    val name: String,
    val file: File,
    val pageCount: Int,
    val sizeFormatted: String
)

enum class ReaderMode(val label: String) {
    SINGLE_PAGE("Halaman Tunggal"),
    CONTINUOUS_SCROLL("Scroll Kontinu")
}

enum class CompressionLevel(val title: String, val description: String, val scale: Float, val quality: Int) {
    LOW(
        title = "Kualitas Tinggi (Kompresi Ringan)",
        description = "Gambar di dalam PDF dikompres ringan; teks tetap tajam dan bisa diseleksi",
        scale = 0.9f,
        quality = 85
    ),
    RECOMMENDED(
        title = "Disarankan (Kompresi Sedang)",
        description = "Gambar diperkecil sedang; keseimbangan ukuran dan ketajaman",
        scale = 0.7f,
        quality = 65
    ),
    EXTREME(
        title = "Kompresi Maksimal",
        description = "Gambar diperkecil maksimal untuk email/WhatsApp. Hasil tergantung isi: PDF tanpa gambar hanya sedikit mengecil",
        scale = 0.5f,
        quality = 45
    )
}

enum class ExportFormat(val extension: String, val mimeType: String) {
    PNG("png", "image/png"),
    JPG("jpg", "image/jpeg"),
    TXT("txt", "text/plain")
}

sealed class OperationState {
    object Idle : OperationState()
    data class Loading(val message: String, val progress: Float = -1f) : OperationState()
    data class Success(val message: String, val outputFile: File? = null, val exportedFiles: List<File> = emptyList()) : OperationState()
    data class Error(val errorMessage: String) : OperationState()
}

enum class ScreenTab {
    HOME,
    READER,
    PAGE_MANAGER,
    MERGE,
    CONVERT,
    COMPRESS_EXPORT
}
