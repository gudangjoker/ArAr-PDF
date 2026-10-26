package com.example.data.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.LruCache
import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.StyleSpan
import android.util.Xml
import com.example.data.model.CompressionLevel
import com.example.data.model.ExportFormat
import com.example.data.model.PageType
import com.example.data.model.PdfPageItem
import com.example.data.util.FileUtils
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.cos.COSBase
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.multipdf.PDFMergerUtility
import com.tom_roush.pdfbox.pdmodel.PDResources
import com.tom_roush.pdfbox.pdmodel.graphics.form.PDFormXObject
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStreamWriter
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object PdfEngine {

    private var isInitialized = false

    // Cache for rendered bitmaps (key: "filePath_pageIndex_width")
    private val memoryCache: LruCache<String, Bitmap> by lazy {
        val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt()
        val cacheSize = maxMemory / 8 // 1/8th of available runtime memory
        object : LruCache<String, Bitmap>(cacheSize) {
            override fun sizeOf(key: String, bitmap: Bitmap): Int {
                return bitmap.byteCount / 1024
            }
        }
    }

    fun init(context: Context) {
        if (!isInitialized) {
            try {
                PDFBoxResourceLoader.init(context.applicationContext)
                isInitialized = true
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * Gets total page count from a PDF file using native Android PdfRenderer
     */
    suspend fun getPageCount(file: File): Int = withContext(Dispatchers.IO) {
        if (!file.exists() || file.length() == 0L) return@withContext 0
        var pfd: ParcelFileDescriptor? = null
        var renderer: PdfRenderer? = null
        try {
            pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            renderer = PdfRenderer(pfd)
            renderer.pageCount
        } catch (e: Exception) {
            // Fallback to PDFBox
            try {
                PDDocument.load(file).use { doc ->
                    doc.numberOfPages
                }
            } catch (ex: Exception) {
                0
            }
        } finally {
            renderer?.close()
            pfd?.close()
        }
    }

    /**
     * Renders a specific page of a PDF file to a high-resolution Bitmap.
     */
    suspend fun renderPageBitmap(
        file: File,
        pageIndex: Int,
        targetWidth: Int = 1080
    ): Bitmap = withContext(Dispatchers.IO) {
        val cacheKey = "${file.absolutePath}_${pageIndex}_$targetWidth"
        memoryCache.get(cacheKey)?.let { cached ->
            if (!cached.isRecycled) return@withContext cached
        }

        var pfd: ParcelFileDescriptor? = null
        var renderer: PdfRenderer? = null
        var page: PdfRenderer.Page? = null

        try {
            pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            renderer = PdfRenderer(pfd)
            if (pageIndex < 0 || pageIndex >= renderer.pageCount) {
                return@withContext createErrorBitmap("Halaman $pageIndex tidak ditemukan")
            }

            page = renderer.openPage(pageIndex)
            val pageW = page.width
            val pageH = page.height
            val scale = targetWidth.toFloat() / pageW.toFloat()
            val targetHeight = (pageH * scale).toInt().coerceAtLeast(1)

            val bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.WHITE)

            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            memoryCache.put(cacheKey, bitmap)
            bitmap
        } catch (e: Exception) {
            e.printStackTrace()
            createErrorBitmap("Gagal me-render: ${e.localizedMessage}")
        } finally {
            page?.close()
            renderer?.close()
            pfd?.close()
        }
    }

    private fun createErrorBitmap(message: String): Bitmap {
        val bitmap = Bitmap.createBitmap(600, 800, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.parseColor("#FFF1F2"))
        val paint = Paint().apply {
            color = Color.parseColor("#E11D48")
            textSize = 28f
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("Error Memuat Halaman", 300f, 380f, paint)
        paint.textSize = 20f
        canvas.drawText(message.take(35), 300f, 430f, paint)
        return bitmap
    }

    // ==========================================
    // 1. PAGE MANAGEMENT (REORDER, DELETE, INSERT)
    // ==========================================

    suspend fun saveManagedPages(
        context: Context,
        sourceFile: File,
        pages: List<PdfPageItem>,
        outputFile: File
    ): Boolean = withContext(Dispatchers.IO) {
        var sourceDoc: PDDocument? = null
        var targetDoc: PDDocument? = null
        outputFile.parentFile?.mkdirs()
        outputFile.delete()
        try {
            sourceDoc = PDDocument.load(sourceFile)
            targetDoc = PDDocument()
            val totalPages = sourceDoc.numberOfPages

            for (item in pages) {
                when (item.type) {
                    PageType.EXISTING -> {
                        if (item.pageIndex in 0 until totalPages) {
                            val page = sourceDoc.getPage(item.pageIndex)
                            targetDoc.importPage(page)
                        }
                    }
                    PageType.BLANK -> {
                        targetDoc.addPage(PDPage(PDRectangle.A4))
                    }
                    PageType.IMAGE -> {
                        if (item.imageUri != null) {
                            try {
                                context.contentResolver.openInputStream(item.imageUri)?.use { input ->
                                    val bmp = BitmapFactory.decodeStream(input)
                                    if (bmp != null) {
                                        val page = PDPage(PDRectangle.A4)
                                        targetDoc.addPage(page)
                                        val pdImage = com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory.createFromImage(targetDoc, bmp)
                                        com.tom_roush.pdfbox.pdmodel.PDPageContentStream(targetDoc, page).use { contentStream ->
                                            val margin = 36f
                                            val availW = PDRectangle.A4.width - (margin * 2)
                                            val availH = PDRectangle.A4.height - (margin * 2)
                                            val scale = Math.min(availW / bmp.width, availH / bmp.height)
                                            val drawW = bmp.width * scale
                                            val drawH = bmp.height * scale
                                            val drawX = margin + (availW - drawW) / 2f
                                            val drawY = margin + (availH - drawH) / 2f
                                            contentStream.drawImage(pdImage, drawX, drawY, drawW, drawH)
                                        }
                                        bmp.recycle()
                                    }
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                                targetDoc.addPage(PDPage(PDRectangle.A4))
                            }
                        } else {
                            targetDoc.addPage(PDPage(PDRectangle.A4))
                        }
                    }
                }
            }

            if (targetDoc.numberOfPages == 0) {
                targetDoc.addPage(PDPage(PDRectangle.A4))
            }
            targetDoc.save(outputFile)
            outputFile.isFile && outputFile.length() > 0L
        } catch (e: Exception) {
            e.printStackTrace()
            outputFile.delete()
            false
        } finally {
            targetDoc?.close()
            sourceDoc?.close()
        }
    }

    // ==========================================
    // 2. PDF MERGER
    // ==========================================

    suspend fun mergePdfFiles(
        inputFiles: List<File>,
        outputFile: File
    ): Boolean = withContext(Dispatchers.IO) {
        // Validate sources before invoking PDFBox. Never report success for an old/partial output.
        val validInputs = inputFiles.filter { it.isFile && it.length() > 0L }
        if (validInputs.isEmpty()) return@withContext false
        outputFile.parentFile?.mkdirs()
        outputFile.delete()
        try {
            val merger = PDFMergerUtility()
            merger.destinationFileName = outputFile.absolutePath
            validInputs.forEach { merger.addSource(it) }
            merger.mergeDocuments(MemoryUsageSetting.setupMixed(50L * 1024 * 1024))
            val validOutput = outputFile.isFile && outputFile.length() > 0L &&
                runCatching { PDDocument.load(outputFile).use { it.numberOfPages > 0 } }.getOrDefault(false)
            if (validOutput) return@withContext true
            outputFile.delete()
        } catch (e: Exception) {
            e.printStackTrace()
            outputFile.delete()
        }

        // Fallback only from validated, readable sources; clean partial output first.
        var targetDoc: PDDocument? = null
        try {
            targetDoc = PDDocument()
            for (file in validInputs) {
                PDDocument.load(file).use { srcDoc ->
                    for (i in 0 until srcDoc.numberOfPages) {
                        targetDoc.importPage(srcDoc.getPage(i))
                    }
                }
            }
            if (targetDoc.numberOfPages == 0) return@withContext false
            targetDoc.save(outputFile)
            outputFile.isFile && outputFile.length() > 0L
        } catch (ex: Exception) {
            ex.printStackTrace()
            outputFile.delete()
            false
        } finally {
            targetDoc?.close()
        }
    }

    // ==========================================
    // 3. FILE CONVERTER TO PDF (IMAGES & DOCX)
    // ==========================================

    suspend fun convertImagesToPdf(
        context: Context,
        imageUris: List<Uri>,
        outputFile: File
    ): Boolean = withContext(Dispatchers.IO) {
        if (imageUris.isEmpty()) return@withContext false
        val pdfDocument = PdfDocument()

        try {
            // Standard A4 point size (595 x 842 points at 72 dpi)
            val pageWidth = 595
            val pageHeight = 842
            val margin = 36f

            for ((index, uri) in imageUris.withIndex()) {
                val inputStream = context.contentResolver.openInputStream(uri) ?: continue
                val originalBitmap = BitmapFactory.decodeStream(inputStream)
                inputStream.close()
                if (originalBitmap == null) continue

                val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, index + 1).create()
                val page = pdfDocument.startPage(pageInfo)
                val canvas = page.canvas

                // Fill background white
                canvas.drawColor(Color.WHITE)

                val availableWidth = pageWidth - (margin * 2)
                val availableHeight = pageHeight - (margin * 2)

                val bmpW = originalBitmap.width.toFloat()
                val bmpH = originalBitmap.height.toFloat()

                val scale = Math.min(availableWidth / bmpW, availableHeight / bmpH)
                val destW = bmpW * scale
                val destH = bmpH * scale

                val left = margin + (availableWidth - destW) / 2f
                val top = margin + (availableHeight - destH) / 2f

                val destRect = RectF(left, top, left + destW, top + destH)
                val srcRect = Rect(0, 0, originalBitmap.width, originalBitmap.height)

                val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
                canvas.drawBitmap(originalBitmap, srcRect, destRect, paint)

                pdfDocument.finishPage(page)
                originalBitmap.recycle()
            }

            FileOutputStream(outputFile).use { out ->
                pdfDocument.writeTo(out)
            }
            outputFile.exists() && outputFile.length() > 0
        } catch (e: Exception) {
            e.printStackTrace()
            false
        } finally {
            pdfDocument.close()
        }
    }

    /**
     * Converts Microsoft Word (.docx) to PDF offline.
     * Unpacks OpenXML structure from ZIP stream and formats text with StaticLayout onto A4 pages.
     */
    suspend fun convertDocxToPdf(
        context: Context,
        docxFile: File,
        outputFile: File
    ): Boolean = withContext(Dispatchers.IO) {
        val paragraphs = extractParagraphsFromDocx(docxFile)
        if (paragraphs.isEmpty()) {
            return@withContext false
        }

        val pdfDocument = PdfDocument()
        try {
            val pageWidth = 595
            val pageHeight = 842
            val margin = 48f
            val printableWidth = (pageWidth - (margin * 2)).toInt()
            val pageBottom = pageHeight - margin

            fun paintFor(size: Float, colorHex: String, bold: Boolean) = TextPaint().apply {
                isAntiAlias = true
                textSize = size
                isFakeBoldText = bold
                color = Color.parseColor(colorHex)
            }
            val titlePaint = paintFor(20f, "#1E293B", true)
            val heading1Paint = paintFor(16f, "#0F172A", true)
            val heading2Paint = paintFor(14f, "#0F172A", true)
            val heading3Paint = paintFor(13f, "#0F172A", true)
            val bodyPaint = paintFor(12f, "#334155", false)

            val decorPaint = Paint().apply {
                color = Color.parseColor("#E2E8F0")
                strokeWidth = 1f
            }
            val brandPaint = Paint().apply {
                color = Color.parseColor("#DC2626")
                textSize = 9f
                isAntiAlias = true
            }

            var currentPageNumber = 0
            var page: PdfDocument.Page? = null
            var canvas: Canvas? = null
            var currentY = margin

            fun startNewPage() {
                page?.let { pdfDocument.finishPage(it) }
                currentPageNumber++
                val info = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, currentPageNumber).create()
                val p = pdfDocument.startPage(info)
                page = p
                val c = p.canvas
                canvas = c
                c.drawColor(Color.WHITE)
                c.drawLine(margin, margin - 12f, pageWidth - margin, margin - 12f, decorPaint)
                c.drawText("ARAR PDF CONVERTER - DOCX TO PDF", margin, margin - 18f, brandPaint)
                c.drawLine(margin, pageBottom + 12f, pageWidth - margin, pageBottom + 12f, decorPaint)
                c.drawText("Halaman $currentPageNumber", pageWidth - margin - 50f, pageBottom + 24f, brandPaint)
                currentY = margin
            }
            startNewPage()

            for ((index, para) in paragraphs.withIndex()) {
                val isTitle = para.level == DOCX_TITLE || (index == 0 && para.level == 0 && para.text.length < 80 && paragraphs.size > 1 && para.runs.all { it.bold })
                val level = if (isTitle) DOCX_TITLE else para.level
                val paint = when (level) {
                    DOCX_TITLE -> titlePaint
                    1 -> heading1Paint
                    2 -> heading2Paint
                    3 -> heading3Paint
                    else -> bodyPaint
                }

                val sb = SpannableStringBuilder()
                if (para.bullet) sb.append("\u2022  ")
                for (run in para.runs) {
                    val start = sb.length
                    sb.append(run.text)
                    val end = sb.length
                    if (end > start) {
                        val style = when {
                            run.bold && run.italic -> Typeface.BOLD_ITALIC
                            run.bold -> Typeface.BOLD
                            run.italic -> Typeface.ITALIC
                            else -> Typeface.NORMAL
                        }
                        if (style != Typeface.NORMAL) {
                            sb.setSpan(StyleSpan(style), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                        }
                    }
                }
                if (sb.isEmpty()) continue

                val layout = StaticLayout.Builder
                    .obtain(sb, 0, sb.length, paint, printableWidth)
                    .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                    .setLineSpacing(4f, 1.15f)
                    .build()

                // Draw line-by-line so a paragraph taller than the remaining space flows onto next pages
                var startLine = 0
                while (startLine < layout.lineCount) {
                    val availableHeight = pageBottom - currentY
                    val chunkTop = layout.getLineTop(startLine)
                    var endLine = startLine
                    while (endLine < layout.lineCount &&
                        layout.getLineBottom(endLine) - chunkTop <= availableHeight
                    ) {
                        endLine++
                    }
                    if (endLine == startLine) {
                        if (currentY > margin) {
                            startNewPage()
                            continue
                        }
                        endLine = startLine + 1 // a single line always fits on an empty page
                    }
                    val chunkBottom = layout.getLineBottom(endLine - 1)
                    val c = canvas!!
                    c.save()
                    c.translate(margin, currentY - chunkTop)
                    c.clipRect(0f, chunkTop.toFloat(), printableWidth.toFloat(), chunkBottom.toFloat())
                    layout.draw(c)
                    c.restore()
                    currentY += (chunkBottom - chunkTop).toFloat()
                    startLine = endLine
                    if (startLine < layout.lineCount) startNewPage()
                }

                currentY += when (level) {
                    DOCX_TITLE -> 20f
                    1, 2, 3 -> 14f
                    else -> 10f
                }
            }

            page?.let { pdfDocument.finishPage(it) }

            FileOutputStream(outputFile).use { out ->
                pdfDocument.writeTo(out)
            }
            outputFile.exists() && outputFile.length() > 0
        } catch (e: Exception) {
            e.printStackTrace()
            false
        } finally {
            pdfDocument.close()
        }
    }

    private const val DOCX_TITLE = -1

    private data class DocxRun(val text: String, val bold: Boolean, val italic: Boolean)

    /** level: DOCX_TITLE (-1), 0 = body, 1..3 = heading level */
    private data class DocxParagraph(val runs: List<DocxRun>, val level: Int, val bullet: Boolean) {
        val text: String get() = runs.joinToString("") { it.text }
    }

    private fun XmlPullParser.attrValue(local: String): String? {
        for (i in 0 until attributeCount) {
            if (getAttributeName(i).substringAfter(':') == local) return getAttributeValue(i)
        }
        return null
    }

    private fun isOn(value: String?): Boolean = value == null || (value != "0" && !value.equals("false", true) && !value.equals("off", true))

    /**
     * Minimal WordprocessingML reader: text runs with bold/italic, Title/Heading styles
     * (w:pStyle), and list items (w:numPr / ListParagraph). Tables are flattened cell by cell;
     * images, fonts, colours and page layout are not preserved.
     */
    private fun extractParagraphsFromDocx(docxFile: File): List<DocxParagraph> {
        val result = mutableListOf<DocxParagraph>()
        try {
            ZipInputStream(FileInputStream(docxFile)).use { zipIn ->
                var entry: ZipEntry? = zipIn.nextEntry
                while (entry != null) {
                    if (entry.name == "word/document.xml") {
                        val parser = Xml.newPullParser()
                        parser.setInput(zipIn, "UTF-8")
                        var eventType = parser.eventType

                        var runs = mutableListOf<DocxRun>()
                        var level = 0
                        var bullet = false
                        var inRun = false
                        var bold = false
                        var italic = false

                        while (eventType != XmlPullParser.END_DOCUMENT) {
                            val name = parser.name?.substringAfter(':')
                            when (eventType) {
                                XmlPullParser.START_TAG -> when (name) {
                                    "p" -> { runs = mutableListOf(); level = 0; bullet = false; inRun = false }
                                    "pStyle" -> {
                                        val style = parser.attrValue("val")?.lowercase() ?: ""
                                        when {
                                            style == "title" || style == "judul" -> level = DOCX_TITLE
                                            style.startsWith("heading") || style.startsWith("judul") -> {
                                                val n = style.filter { it.isDigit() }.toIntOrNull() ?: 1
                                                level = n.coerceIn(1, 3)
                                            }
                                            style.startsWith("listparagraph") || style.startsWith("list") -> bullet = true
                                        }
                                    }
                                    "numPr" -> bullet = true
                                    "r" -> { inRun = true; bold = false; italic = false }
                                    "b" -> if (inRun) bold = isOn(parser.attrValue("val"))
                                    "i" -> if (inRun) italic = isOn(parser.attrValue("val"))
                                    "tab" -> if (inRun) runs.add(DocxRun("    ", false, false))
                                    "br" -> if (inRun) runs.add(DocxRun("\n", false, false))
                                    "t" -> runs.add(DocxRun(parser.nextText(), bold, italic))
                                }
                                XmlPullParser.END_TAG -> when (name) {
                                    "r" -> inRun = false
                                    "p" -> {
                                        if (runs.any { it.text.isNotBlank() }) {
                                            result.add(DocxParagraph(runs.toList(), level, bullet))
                                        }
                                        runs = mutableListOf()
                                    }
                                }
                            }
                            eventType = parser.next()
                        }
                        break
                    }
                    entry = zipIn.nextEntry
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return result
    }

    fun getDocxPreviewText(docxFile: File, maxLines: Int = 4): String {
        val paras = extractParagraphsFromDocx(docxFile)
        return paras.take(maxLines).joinToString("\n\n") { it.text.trim() }
    }

    // ==========================================
    // 4. COMPRESSION & REVERSE EXPORT
    // ==========================================

    /**
     * Compresses by re-encoding the images embedded in the PDF as (optionally downscaled) JPEGs,
     * keeping text, vectors, links and forms intact. A PDF with no large images will barely shrink.
     * If the result is not smaller than the source, the original is copied unchanged.
     */
    suspend fun compressPdf(
        sourceFile: File,
        compressionLevel: CompressionLevel,
        outputFile: File
    ): Boolean = withContext(Dispatchers.IO) {
        var doc: PDDocument? = null
        try {
            doc = PDDocument.load(sourceFile, MemoryUsageSetting.setupMixed(50L * 1024 * 1024))
            val replaced = java.util.IdentityHashMap<COSBase, PDImageXObject>()
            val visitedForms = java.util.IdentityHashMap<COSBase, Boolean>()
            for (page in doc.pages) {
                recompressResources(doc, page.resources, compressionLevel, replaced, visitedForms)
            }
            doc.save(outputFile)
            doc.close()
            doc = null
            if (!outputFile.exists() || outputFile.length() == 0L) {
                false
            } else {
                if (outputFile.length() >= sourceFile.length()) {
                    sourceFile.copyTo(outputFile, overwrite = true)
                }
                true
            }
        } catch (e: Exception) {
            e.printStackTrace()
            outputFile.delete()
            false
        } finally {
            doc?.close()
        }
    }

    private fun recompressResources(
        doc: PDDocument,
        resources: PDResources?,
        level: CompressionLevel,
        replaced: java.util.IdentityHashMap<COSBase, PDImageXObject>,
        visitedForms: java.util.IdentityHashMap<COSBase, Boolean>
    ) {
        if (resources == null) return
        for (name in resources.xObjectNames.toList()) {
            val xObject = try { resources.getXObject(name) } catch (e: Exception) { null } ?: continue
            when (xObject) {
                is PDImageXObject -> {
                    val key: COSBase = xObject.cosObject
                    val replacement = replaced.getOrPut(key) { recompressImage(doc, xObject, level) ?: xObject }
                    if (replacement !== xObject) resources.put(name, replacement)
                }
                is PDFormXObject -> {
                    if (visitedForms.put(xObject.cosObject, true) == null) {
                        recompressResources(doc, xObject.resources, level, replaced, visitedForms)
                    }
                }
            }
        }
    }

    private fun recompressImage(doc: PDDocument, image: PDImageXObject, level: CompressionLevel): PDImageXObject? {
        return try {
            // Skip masks/transparency and tiny images: re-encoding would lose or not help
            if (image.isStencil || image.softMask != null || image.mask != null) return null
            if (image.width < 64 || image.height < 64) return null

            val bitmap = image.image ?: return null
            val w = (bitmap.width * level.scale).toInt().coerceAtLeast(1)
            val h = (bitmap.height * level.scale).toInt().coerceAtLeast(1)
            val scaled = if (w != bitmap.width || h != bitmap.height) {
                Bitmap.createScaledBitmap(bitmap, w, h, true)
            } else bitmap

            val jpeg = try {
                JPEGFactory.createFromImage(doc, scaled, level.quality / 100f)
            } finally {
                // PDImageXObject.image returns a decoded bitmap owned by this operation.
                // Release both scaled and original bitmaps, but never recycle the same object twice.
                if (scaled !== bitmap && !scaled.isRecycled) scaled.recycle()
                if (!bitmap.isRecycled) bitmap.recycle()
            }

            // Compare stream lengths without materializing large streams as byte arrays.
            val oldSize = image.stream.createRawInputStream().use { countBytes(it) }
            val newSize = jpeg.stream.createRawInputStream().use { countBytes(it) }
            if (oldSize > 0L && newSize in 1 until oldSize) jpeg else null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun countBytes(input: InputStream): Long {
        val buffer = ByteArray(8192)
        var total = 0L
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            total += count
        }
        return total
    }

    suspend fun exportPagesToImages(
        context: Context,
        sourceFile: File,
        pageIndices: List<Int>,
        format: ExportFormat
    ): List<File> = withContext(Dispatchers.IO) {
        val exportedFiles = mutableListOf<File>()
        var pfd: ParcelFileDescriptor? = null
        var renderer: PdfRenderer? = null

        try {
            pfd = ParcelFileDescriptor.open(sourceFile, ParcelFileDescriptor.MODE_READ_ONLY)
            renderer = PdfRenderer(pfd)
            val total = renderer.pageCount

            val targetIndices = if (pageIndices.isEmpty()) (0 until total).toList() else pageIndices

            val exportsDir = File(context.filesDir, "exported_images").apply { mkdirs() }
            val baseName = sourceFile.nameWithoutExtension

            for (idx in targetIndices) {
                if (idx !in 0 until total) continue
                val page = renderer.openPage(idx)
                var bitmap: Bitmap? = null
                try {
                    // 2x rendering with a pixel cap to avoid OOM on unusually large page sizes.
                    val requestedW = (page.width * 2f).toInt().coerceAtLeast(1)
                    val requestedH = (page.height * 2f).toInt().coerceAtLeast(1)
                    val maxPixels = 20_000_000L
                    val pixels = requestedW.toLong() * requestedH.toLong()
                    val downscale = if (pixels > maxPixels) kotlin.math.sqrt(maxPixels.toDouble() / pixels).toFloat() else 1f
                    val targetW = (requestedW * downscale).toInt().coerceAtLeast(1)
                    val targetH = (requestedH * downscale).toInt().coerceAtLeast(1)
                    val renderedBitmap = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
                    bitmap = renderedBitmap
                    Canvas(renderedBitmap).drawColor(Color.WHITE)
                    page.render(renderedBitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)

                    val outFile = File(exportsDir, "${baseName}_page_${idx + 1}.${format.extension}")
                    val encoded = FileOutputStream(outFile).use { fos ->
                        val compressFormat = if (format == ExportFormat.PNG) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG
                        renderedBitmap.compress(compressFormat, 90, fos)
                    }
                    if (encoded && outFile.exists() && outFile.length() > 0L) exportedFiles.add(outFile)
                    else outFile.delete()
                } finally {
                    bitmap?.takeIf { !it.isRecycled }?.recycle()
                    page.close()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            renderer?.close()
            pfd?.close()
        }
        exportedFiles
    }

    suspend fun extractTextFromPdf(
        sourceFile: File,
        outputFile: File
    ): Boolean = withContext(Dispatchers.IO) {
        var doc: PDDocument? = null
        try {
            doc = PDDocument.load(sourceFile)
            val stripper = PDFTextStripper()
            val text = stripper.getText(doc)

            OutputStreamWriter(FileOutputStream(outputFile), "UTF-8").use { writer ->
                writer.write(text)
            }
            outputFile.exists() && outputFile.length() > 0
        } catch (e: Exception) {
            e.printStackTrace()
            false
        } finally {
            doc?.close()
        }
    }

    // ==========================================
    // 5. SAMPLE GENERATORS (FOR IMMEDIATE TESTING)
    // ==========================================

    suspend fun generateSamplePdf(context: Context): File = withContext(Dispatchers.IO) {
        val sampleDir = File(context.filesDir, "samples").apply { mkdirs() }
        val sampleFile = File(sampleDir, "Contoh_Dokumen_ArAr_PDF.pdf")
        if (sampleFile.exists() && sampleFile.length() > 0) {
            return@withContext sampleFile
        }

        val pdfDocument = PdfDocument()
        val pageWidth = 595
        val pageHeight = 842

        val titlePaint = TextPaint().apply {
            color = Color.parseColor("#991B1B")
            textSize = 24f
            isFakeBoldText = true
            isAntiAlias = true
        }

        val headingPaint = TextPaint().apply {
            color = Color.parseColor("#1E293B")
            textSize = 16f
            isFakeBoldText = true
            isAntiAlias = true
        }

        val bodyPaint = TextPaint().apply {
            color = Color.parseColor("#334155")
            textSize = 12f
            isAntiAlias = true
        }

        val cardPaint = Paint().apply {
            color = Color.parseColor("#F8FAFC")
            style = Paint.Style.FILL
        }

        val strokePaint = Paint().apply {
            color = Color.parseColor("#E2E8F0")
            style = Paint.Style.STROKE
            strokeWidth = 1.5f
        }

        // --- PAGE 1: COVER & OVERVIEW ---
        val page1Info = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create()
        val page1 = pdfDocument.startPage(page1Info)
        val c1 = page1.canvas
        c1.drawColor(Color.WHITE)

        // Banner header
        val bannerPaint = Paint().apply { color = Color.parseColor("#DC2626") }
        c1.drawRect(0f, 0f, pageWidth.toFloat(), 180f, bannerPaint)

        val bannerTitle = Paint().apply {
            color = Color.WHITE
            textSize = 26f
            isFakeBoldText = true
            isAntiAlias = true
        }
        c1.drawText("ARAR PDF - MOBILE EDITOR", 40f, 85f, bannerTitle)

        val bannerSub = Paint().apply {
            color = Color.parseColor("#FEE2E2")
            textSize = 13f
            isAntiAlias = true
        }
        c1.drawText("Dokumen Resmi Demonstrasi Fitur Lengkap & Offline Engine", 40f, 115f, bannerSub)
        c1.drawText("Versi 1.0.0 • Arsitektur Jetpack Compose MVVM", 40f, 138f, bannerSub)

        var yPos = 220f
        c1.drawText("1. Ringkasan Eksekutif Aplikasi", 40f, yPos, headingPaint)
        yPos += 24f

        val p1Text = "Aplikasi ArAr PDF dirancang sebagai solusi pengolahan dokumen PDF terintegrasi tanpa memerlukan koneksi internet (100% offline). Seluruh manipulasi halaman, penggabungan file, konversi gambar dan Word (.docx), pembaca responsif, serta kompresi diproses secara lokal di perangkat Android."
        val p1Layout = StaticLayout.Builder.obtain(p1Text, 0, p1Text.length, bodyPaint, 515).build()
        c1.save()
        c1.translate(40f, yPos)
        p1Layout.draw(c1)
        c1.restore()
        yPos += p1Layout.height + 30f

        // Cards for features
        val features = listOf(
            "1. Manage Halaman" to "Menyisipkan, menghapus, dan mengatur ulang urutan halaman dokumen secara fleksibel.",
            "2. Kombinasi File (Merge)" to "Menggabungkan beberapa berkas PDF menjadi satu dokumen utuh dengan urutan kustom.",
            "3. Konversi ke PDF" to "Mengonversi gambar foto (JPG/PNG) dan dokumen Word (.docx) langsung menjadi berkas PDF standar.",
            "4. Mobile PDF Reader" to "Pembaca PDF responsif dengan rendering optimal, gesture zoom, jump page, dan thumbnail drawer.",
            "5. Kompresi & Ekspor Balik" to "Pengecilan ukuran berkas dengan mengompres gambar di dalam PDF serta ekspor halaman PDF kembali menjadi gambar JPG/PNG dan teks."
        )

        for ((title, desc) in features) {
            c1.drawRoundRect(40f, yPos, 555f, yPos + 60f, 8f, 8f, cardPaint)
            c1.drawRoundRect(40f, yPos, 555f, yPos + 60f, 8f, 8f, strokePaint)

            val bulletPaint = Paint().apply { color = Color.parseColor("#DC2626") }
            c1.drawCircle(58f, yPos + 22f, 4f, bulletPaint)

            val fTitlePaint = Paint().apply {
                color = Color.parseColor("#0F172A")
                textSize = 12f
                isFakeBoldText = true
                isAntiAlias = true
            }
            c1.drawText(title, 72f, yPos + 26f, fTitlePaint)

            val fDescPaint = Paint().apply {
                color = Color.parseColor("#64748B")
                textSize = 10f
                isAntiAlias = true
            }
            c1.drawText(desc, 72f, yPos + 44f, fDescPaint)

            yPos += 72f
        }

        // Footer
        val footPaint = Paint().apply {
            color = Color.parseColor("#94A3B8")
            textSize = 10f
            isAntiAlias = true
        }
        c1.drawText("Halaman 1 dari 3 • ArAr PDF", 40f, 810f, footPaint)
        pdfDocument.finishPage(page1)

        // --- PAGE 2: DETAILED SPECS ---
        val page2Info = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 2).create()
        val page2 = pdfDocument.startPage(page2Info)
        val c2 = page2.canvas
        c2.drawColor(Color.WHITE)

        c2.drawText("2. Spesifikasi Teknis & Kinerja", 40f, 60f, headingPaint)
        c2.drawLine(40f, 75f, 555f, 75f, strokePaint)

        yPos = 100f
        val tableRows = listOf(
            "Modul Inti" to "Implementasi",
            "Manipulasi Halaman" to "PdfBox-Android (PDDocument, PDPage, PDFMerger)",
            "Reader & Rendering" to "Android Native PdfRenderer + LruCache Memory",
            "Word to PDF" to "Offline XML Pull Parser + StaticLayout Canvas Engine",
            "Kompresor PDF" to "PdfBox: re-encode gambar tertanam (JPEG, skala & kualitas)",
            "Ekspor Gambar" to "Render bitmap 2x (~144 dpi) PNG & JPEG",
            "Penyimpanan File" to "Android Scoped Storage & FileProvider System"
        )

        for ((col1, col2) in tableRows) {
            val isHeader = col1 == "Modul Inti"
            val bgP = if (isHeader) Paint().apply { color = Color.parseColor("#F1F5F9") } else cardPaint
            c2.drawRect(40f, yPos, 555f, yPos + 32f, bgP)
            c2.drawRect(40f, yPos, 555f, yPos + 32f, strokePaint)

            val tP = Paint().apply {
                color = if (isHeader) Color.parseColor("#0F172A") else Color.parseColor("#334155")
                textSize = if (isHeader) 11f else 10f
                isFakeBoldText = isHeader
                isAntiAlias = true
            }
            c2.drawText(col1, 55f, yPos + 20f, tP)
            c2.drawText(col2, 220f, yPos + 20f, tP)
            yPos += 32f
        }

        yPos += 40f
        c2.drawText("3. Diagram Alur Pemrosesan Dokumen", 40f, yPos, headingPaint)
        yPos += 24f

        val diagramPaint = Paint().apply {
            color = Color.parseColor("#EFF6FF")
            style = Paint.Style.FILL
        }
        val diaBorder = Paint().apply {
            color = Color.parseColor("#3B82F6")
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }
        val diaText = Paint().apply {
            color = Color.parseColor("#1E3A8A")
            textSize = 11f
            isFakeBoldText = true
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }

        val boxes = listOf(
            "Input File\n(PDF/IMG/DOCX)" to 40f,
            "Engine Offline\n(Local Memory)" to 210f,
            "PDF Output\n(FileProvider)" to 380f
        )
        for ((label, x) in boxes) {
            c2.drawRoundRect(x, yPos, x + 135f, yPos + 60f, 8f, 8f, diagramPaint)
            c2.drawRoundRect(x, yPos, x + 135f, yPos + 60f, 8f, 8f, diaBorder)
            val lines = label.split("\n")
            c2.drawText(lines[0], x + 67f, yPos + 26f, diaText)
            c2.drawText(lines[1], x + 67f, yPos + 44f, diaText)
        }

        // Draw arrows between boxes
        val arrowPaint = Paint().apply {
            color = Color.parseColor("#3B82F6")
            strokeWidth = 2f
            style = Paint.Style.STROKE
        }
        c2.drawLine(175f, yPos + 30f, 210f, yPos + 30f, arrowPaint)
        c2.drawLine(345f, yPos + 30f, 380f, yPos + 30f, arrowPaint)

        c2.drawText("Halaman 2 dari 3 • ArAr PDF", 40f, 810f, footPaint)
        pdfDocument.finishPage(page2)

        // --- PAGE 3: TIPS & NOTES ---
        val page3Info = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 3).create()
        val page3 = pdfDocument.startPage(page3Info)
        val c3 = page3.canvas
        c3.drawColor(Color.WHITE)

        c3.drawText("4. Panduan & Tips Penggunaan Cepat", 40f, 60f, headingPaint)
        c3.drawLine(40f, 75f, 555f, 75f, strokePaint)

        val tips = listOf(
            "Mengatur Urutan Halaman:" to "Buka menu 'Kelola Halaman', pilih halaman yang ingin dipindah atau dihapus, lalu gunakan tombol panah untuk menukar posisi.",
            "Menggabungkan Dokumen (Merge):" to "Pilih 2 atau lebih file PDF sekaligus. Anda dapat mengubah urutan penggabungan file sebelum menekan 'Gabungkan PDF'.",
            "Konversi Gambar ke PDF:" to "Pilih beberapa foto dari galeri kamera. Foto akan otomatis diskalakan dengan rapi ke ukuran kertas A4.",
            "Kompresi Dokumen:" to "Gunakan mode 'Disarankan' untuk PDF yang banyak berisi foto/scan. Teks tetap tajam; PDF tanpa gambar hanya sedikit mengecil.",
            "Ekspor Balik ke Gambar/Teks:" to "Gunakan opsi 'Ekspor Gambar' untuk menyimpan halaman PDF sebagai file PNG beresolusi tinggi yang siap dibagikan ke media sosial atau chat."
        )

        yPos = 100f
        for ((title, detail) in tips) {
            val tTitlePaint = Paint().apply {
                color = Color.parseColor("#DC2626")
                textSize = 12f
                isFakeBoldText = true
                isAntiAlias = true
            }
            c3.drawText(title, 40f, yPos, tTitlePaint)
            yPos += 18f

            val layout = StaticLayout.Builder.obtain(detail, 0, detail.length, bodyPaint, 515).build()
            c3.save()
            c3.translate(40f, yPos)
            layout.draw(c3)
            c3.restore()
            yPos += layout.height + 24f
        }

        c3.drawText("Halaman 3 dari 3 • ArAr PDF", 40f, 810f, footPaint)
        pdfDocument.finishPage(page3)

        FileOutputStream(sampleFile).use { out ->
            pdfDocument.writeTo(out)
        }
        pdfDocument.close()
        sampleFile
    }

    /**
     * Generates a sample .docx file (OpenXML structure) for immediate testing of Word to PDF conversion.
     */
    suspend fun generateSampleDocx(context: Context): File = withContext(Dispatchers.IO) {
        val sampleDir = File(context.filesDir, "samples").apply { mkdirs() }
        val sampleFile = File(sampleDir, "Dokumen_Laporan_Proyek_v2.docx")
        if (sampleFile.exists() && sampleFile.length() > 0) {
            return@withContext sampleFile
        }

        val contentTypesXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
</Types>"""

        val relsXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
</Relationships>"""

        val documentXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
  <w:body>
    <w:p><w:pPr><w:pStyle w:val="Title"/></w:pPr><w:r><w:t>LAPORAN EVALUASI &amp; PERFORMA SISTEM</w:t></w:r></w:p>
    <w:p><w:r><w:t xml:space="preserve">Dokumen ini adalah contoh berkas Word (.docx) untuk menguji fitur Konversi Word ke PDF di aplikasi ArAr PDF. Teks </w:t></w:r><w:r><w:rPr><w:b/></w:rPr><w:t>tebal</w:t></w:r><w:r><w:t xml:space="preserve"> dan </w:t></w:r><w:r><w:rPr><w:i/></w:rPr><w:t>miring</w:t></w:r><w:r><w:t xml:space="preserve"> ikut dipertahankan.</w:t></w:r></w:p>
    <w:p><w:pPr><w:pStyle w:val="Heading1"/></w:pPr><w:r><w:t>1. Pendahuluan &amp; Latar Belakang</w:t></w:r></w:p>
    <w:p><w:r><w:t>Transformasi digital menuntut efisiensi tinggi dalam penanganan berkas dan arsip digital. Kebutuhan konversi dokumen dari format Microsoft Word (.docx) ke format Portable Document Format (PDF) merupakan fungsi mendasar untuk memastikan konsistensi visual di berbagai perangkat seluler.</w:t></w:r></w:p>
    <w:p><w:pPr><w:pStyle w:val="Heading1"/></w:pPr><w:r><w:t>2. Keunggulan Pemrosesan Offline</w:t></w:r></w:p>
    <w:p><w:pPr><w:pStyle w:val="ListParagraph"/><w:numPr><w:ilvl w:val="0"/><w:numId w:val="1"/></w:numPr></w:pPr><w:r><w:t>Tanpa unggahan ke server</w:t></w:r></w:p>
    <w:p><w:pPr><w:pStyle w:val="ListParagraph"/><w:numPr><w:ilvl w:val="0"/><w:numId w:val="1"/></w:numPr></w:pPr><w:r><w:t>Data tetap di perangkat</w:t></w:r></w:p>
    <w:p><w:pPr><w:pStyle w:val="Heading1"/></w:pPr><w:r><w:t>3. Kesimpulan</w:t></w:r></w:p>
    <w:p><w:r><w:t>Konverter membaca paragraf, judul, teks tebal/miring, dan daftar dari berkas docx lalu menyusunnya ke halaman A4. Gambar, tabel kompleks, dan font khusus belum didukung.</w:t></w:r></w:p>
  </w:body>
</w:document>"""

        ZipOutputStream(FileOutputStream(sampleFile)).use { zipOut ->
            // [Content_Types].xml
            zipOut.putNextEntry(ZipEntry("[Content_Types].xml"))
            zipOut.write(contentTypesXml.toByteArray(Charsets.UTF_8))
            zipOut.closeEntry()

            // _rels/.rels
            zipOut.putNextEntry(ZipEntry("_rels/.rels"))
            zipOut.write(relsXml.toByteArray(Charsets.UTF_8))
            zipOut.closeEntry()

            // word/document.xml
            zipOut.putNextEntry(ZipEntry("word/document.xml"))
            zipOut.write(documentXml.toByteArray(Charsets.UTF_8))
            zipOut.closeEntry()
        }

        sampleFile
    }

    /**
     * Clears cached bitmap renderings
     */
    fun clearCache() {
        memoryCache.evictAll()
    }
}
