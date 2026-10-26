package com.example.data.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object FileUtils {

    fun formatFileSize(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB")
        val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
        val index = digitGroups.coerceIn(0, units.size - 1)
        val value = bytes / Math.pow(1024.0, index.toDouble())
        return DecimalFormat("#,##0.#").format(value) + " " + units[index]
    }

    fun getFileName(context: Context, uri: Uri): String {
        var result: String? = null
        if (uri.scheme == "content") {
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val index = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index != -1) {
                        result = it.getString(index)
                    }
                }
            }
        }
        if (result == null) {
            result = uri.path
            val cut = result?.lastIndexOf('/') ?: -1
            if (cut != -1 && result != null) {
                result = result?.substring(cut + 1)
            }
        }
        return result ?: "document_${System.currentTimeMillis()}.pdf"
    }

    fun copyUriToTempFile(context: Context, uri: Uri, prefix: String = "import", ext: String = "pdf", persistent: Boolean = false): File {
        val originalName = getFileName(context, uri)
        val cleanName = originalName.replace("[^a-zA-Z0-9._-]".toRegex(), "_")
        val tempDir = (if (persistent) File(context.filesDir, "imports") else File(context.cacheDir, "imports")).apply { mkdirs() }
        val targetFile = File(tempDir, "${System.currentTimeMillis()}_$cleanName")
        
        try {
            val input = context.contentResolver.openInputStream(uri)
                ?: throw IOException("Tidak dapat membuka berkas yang dipilih.")
            input.use {
                FileOutputStream(targetFile).use { output -> it.copyTo(output) }
            }
            if (!targetFile.isFile || targetFile.length() == 0L) {
                throw IOException("Berkas kosong atau gagal disalin.")
            }
            return targetFile
        } catch (e: Exception) {
            targetFile.delete()
            throw e
        }
    }

    fun createExportFile(context: Context, prefix: String, extension: String): File {
        val exportsDir = File(context.filesDir, "exports").apply { mkdirs() }
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        return File(exportsDir, "${prefix}_$timeStamp.$extension")
    }

    fun getFileUri(context: Context, file: File): Uri {
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
    }

    fun shareFile(context: Context, file: File, mimeType: String = "application/pdf") {
        val uri = getFileUri(context, file)
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(shareIntent, "Bagikan Dokumen")
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }

    fun shareMultipleFiles(context: Context, files: List<File>, mimeType: String = "image/png") {
        if (files.isEmpty()) return
        val uris = ArrayList<Uri>()
        for (f in files) {
            uris.add(getFileUri(context, f))
        }
        val shareIntent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = mimeType
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(shareIntent, "Bagikan File Hasil Ekspor")
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }
}
