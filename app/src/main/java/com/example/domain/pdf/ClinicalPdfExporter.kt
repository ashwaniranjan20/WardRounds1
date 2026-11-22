package com.example.domain.pdf

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.FileProvider
import com.example.data.model.DischargeSummaryEntity
import com.example.data.model.HandoverEntryEntity
import com.example.data.model.HandoverStatus
import com.example.data.model.IllnessSeverity
import com.example.data.model.PatientEntity
import com.example.data.model.ShiftType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class PdfExportOutput(
    val file: File,
    val uri: Uri,
    val title: String,
    val suggestedFileName: String
)

object ClinicalPdfExporter {

    private const val PAGE_WIDTH = 595 // A4 width in points (72 dpi)
    private const val PAGE_HEIGHT = 842 // A4 height in points (72 dpi)
    private const val MARGIN_X = 36f
    private const val CONTENT_WIDTH = PAGE_WIDTH - (MARGIN_X * 2)
    private const val TOP_START_Y = 40f
    private const val BOTTOM_LIMIT_Y = PAGE_HEIGHT - 48f

    // Hospital Clinical Color Tokens for Print
    private val COLOR_NAVY = Color.rgb(17, 53, 139)
    private val COLOR_PERIWINKLE = Color.rgb(97, 146, 252)
    private val COLOR_LIME = Color.rgb(199, 239, 102)
    private val COLOR_TEXT_PRIMARY = Color.rgb(21, 33, 61)
    private val COLOR_TEXT_SECONDARY = Color.rgb(100, 112, 138)
    private val COLOR_BORDER = Color.rgb(220, 226, 238)
    private val COLOR_BG_SUBTLE = Color.rgb(244, 246, 251)
    private val COLOR_ALERT = Color.rgb(217, 74, 74)
    private val COLOR_AMBER = Color.rgb(180, 115, 0)
    private val COLOR_GREEN = Color.rgb(46, 125, 50)

    suspend fun generateDischargeSummaryPdf(
        context: Context,
        patient: PatientEntity,
        summary: DischargeSummaryEntity,
        clinicianName: String
    ): PdfExportOutput = withContext(Dispatchers.IO) {
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date())
        val safeUhid = patient.uhid.replace(Regex("[^A-Za-z0-9_-]"), "_")
        val fileName = "Discharge_Summary_${safeUhid}.pdf"

        val pdfFile = createPdfFile(context, fileName) { writer ->
            writer.drawHospitalBanner(
                docTitle = "INPATIENT DISCHARGE SUMMARY",
                subtitle = if (summary.isFinalized) "FINALIZED CLINICAL RECORD" else "UNFINALIZED DRAFT — FOR CLINICIAN REVIEW",
                generatedBy = "Author: $clinicianName • $timestamp",
                isDraft = !summary.isFinalized
            )

            // Patient Demographic Card
            writer.drawInfoBox(
                title = "PATIENT IDENTIFICATION & ADMISSION",
                rows = listOf(
                    "Patient Name" to "${patient.name} (${patient.age ?: "--"}y / ${patient.sex ?: "--"})",
                    "UHID / Adm No." to "${patient.uhid}  |  ${patient.admissionNumber.ifBlank { summary.admissionId }}",
                    "Ward & Bed" to "${patient.ward} — Bed ${patient.bedNumber} (${patient.department})",
                    "Consultant" to patient.consultant,
                    "Admission Date" to "${patient.admissionDate}  |  Status: ${summary.dischargeStatus.name.replace("_", " ")}",
                    "Known Allergies" to patient.allergies.ifBlank { "NKDA" }
                )
            )

            writer.drawSection(
                heading = "DIAGNOSIS",
                body = buildString {
                    append("Admission Diagnosis: ${summary.admissionDiagnosis.ifBlank { patient.primaryDiagnosis }}\n")
                    append("Final Diagnosis: ${summary.finalDiagnosis.ifBlank { "Pending clinician final diagnosis" }}")
                }
            )

            writer.drawSection(
                heading = "CLINICAL COURSE & HOSPITAL TRAJECTORY",
                body = summary.relevantClinicalCourse.ifBlank { "No clinical course recorded." }
            )

            if (summary.importantExamFindings.isNotBlank()) {
                writer.drawSection(
                    heading = "KEY EXAMINATION FINDINGS",
                    body = summary.importantExamFindings
                )
            }

            if (summary.importantInvestigationResults.isNotBlank()) {
                writer.drawSection(
                    heading = "KEY INVESTIGATION RESULTS",
                    body = summary.importantInvestigationResults
                )
            }

            writer.drawSection(
                heading = "CONDITION AT DISCHARGE",
                body = summary.conditionAtDischarge.ifBlank { "Stable" }
            )

            writer.drawSection(
                heading = "DISCHARGE MEDICATIONS (DOSE / ROUTE / FREQUENCY / DURATION)",
                body = summary.dischargeMedications.ifBlank { "No discharge medications specified." },
                highlightBox = true
            )

            writer.drawSection(
                heading = "DIET, ACTIVITY & CARE INSTRUCTIONS",
                body = buildString {
                    append("• Diet Advice: ${summary.dietAdvice}\n")
                    append("• Activity Advice: ${summary.activityAdvice}\n")
                    if (summary.woundOrDeviceCare.isNotBlank()) {
                        append("• Wound / Device Care: ${summary.woundOrDeviceCare}\n")
                    }
                    if (summary.additionalInstructions.isNotBlank()) {
                        append("• Additional Notes: ${summary.additionalInstructions}")
                    }
                }
            )

            writer.drawSection(
                heading = "RED FLAG WARNING SIGNS & URGENT CARE",
                body = buildString {
                    append("Warning Signs: ${summary.warningSigns}\n")
                    append("Urgent Care: ${summary.urgentCareInstructions}")
                },
                accentColor = COLOR_ALERT
            )

            writer.drawSection(
                heading = "OUTPATIENT FOLLOW-UP & PENDING REPORTS",
                body = buildString {
                    append("Follow-up Department: ${summary.followUpDepartment}\n")
                    append("Follow-up Date: ${summary.followUpDate.ifBlank { "To be scheduled" }}\n")
                    append("Scheduled Investigations: ${summary.followUpInvestigations}\n")
                    if (summary.pendingInvestigationReports.isNotBlank()) {
                        append("Pending Reports: ${summary.pendingInvestigationReports}")
                    }
                }
            )

            writer.drawSignatureFooterBlock(
                primaryLabel = "Responsible Clinician",
                primaryValue = summary.responsibleClinician.ifBlank { clinicianName },
                secondaryLabel = if (summary.isFinalized) "Finalized Timestamp" else "Document Status",
                secondaryValue = summary.finalizedAt ?: "DRAFT — Requires Physician Sign-Off"
            )
        }

        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", pdfFile)
        PdfExportOutput(
            file = pdfFile,
            uri = uri,
            title = "Discharge Summary — ${patient.name}",
            suggestedFileName = fileName
        )
    }

    suspend fun generateHandoverPacketPdf(
        context: Context,
        handovers: List<HandoverEntryEntity>,
        shiftDate: String,
        shiftType: ShiftType,
        clinicianName: String
    ): PdfExportOutput = withContext(Dispatchers.IO) {
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date())
        val fileName = "Shift_Handover_${shiftDate}_${shiftType.name}.pdf"

        val ordered = handovers.sortedWith(
            compareBy<HandoverEntryEntity> {
                when (it.illnessSeverity) {
                    IllnessSeverity.UNSTABLE -> 0
                    IllnessSeverity.WATCHER -> 1
                    IllnessSeverity.STABLE -> 2
                }
            }.thenBy { it.ward }.thenBy { it.bedNumber }
        )

        val pdfFile = createPdfFile(context, fileName) { writer ->
            writer.drawHospitalBanner(
                docTitle = "CLINICAL SHIFT HANDOVER PACKET (SBAR • I-PASS)",
                subtitle = "${shiftType.label}  |  Shift Date: $shiftDate",
                generatedBy = "Generated by: $clinicianName • $timestamp",
                isDraft = false
            )

            val unstableCount = ordered.count { it.illnessSeverity == IllnessSeverity.UNSTABLE }
            val watcherCount = ordered.count { it.illnessSeverity == IllnessSeverity.WATCHER }
            val ackCount = ordered.count { it.status == HandoverStatus.ACKNOWLEDGED }

            writer.drawInfoBox(
                title = "SHIFT HANDOVER SUMMARY",
                rows = listOf(
                    "Total Patients Handed Over" to "${ordered.size} active ward patient(s)",
                    "Illness Severity Breakdown" to "Unstable: $unstableCount   |   Watcher: $watcherCount   |   Stable: ${ordered.size - unstableCount - watcherCount}",
                    "Sign-Off Status" to "$ackCount of ${ordered.size} acknowledged by incoming clinician"
                )
            )

            if (ordered.isEmpty()) {
                writer.drawSection(
                    heading = "NO HANDOVERS RECORDED",
                    body = "No handover entries were found for the selected shift."
                )
            } else {
                ordered.forEachIndexed { idx, item ->
                    val severityColor = when (item.illnessSeverity) {
                        IllnessSeverity.UNSTABLE -> COLOR_ALERT
                        IllnessSeverity.WATCHER -> COLOR_AMBER
                        IllnessSeverity.STABLE -> COLOR_GREEN
                    }

                    val signOffText = if (item.status == HandoverStatus.ACKNOWLEDGED) {
                        "ACKNOWLEDGED by ${item.incomingClinician ?: "--"} (${item.acknowledgedAt ?: "--"}) • Read-Back: ${if (item.synthesisConfirmed) "Confirmed" else "Pending"}"
                    } else {
                        "${item.status.label} — Prepared by ${item.outgoingClinician} (${item.outgoingTime})"
                    }

                    val body = buildString {
                        append("[I] Illness Severity: ${item.illnessSeverity.label.uppercase()}   |   Ward: ${item.ward} Bed ${item.bedNumber}\n\n")
                        append("[S] Situation: ${item.situation.ifBlank { "--" }}\n")
                        append("[B] Background: ${item.background.ifBlank { "--" }}\n")
                        append("[A] Assessment & Vitals: ${item.assessment.ifBlank { "--" }}\n")
                        append("[R / Action List]:\n${item.recommendationAndActions.ifBlank { "Routine monitoring" }}\n\n")
                        append("[Contingency / If–Then]: ${item.contingencyPlan.ifBlank { "--" }}\n")
                        append("[Pending Critical Items]: ${item.pendingCriticalItems.ifBlank { "None" }}\n\n")
                        append("[Sign-Off]: $signOffText")
                    }

                    writer.drawSection(
                        heading = "${idx + 1}. ${item.patientName} (UHID: ${item.uhid}) — ${item.illnessSeverity.label.uppercase()}",
                        body = body,
                        accentColor = severityColor,
                        highlightBox = item.illnessSeverity != IllnessSeverity.STABLE
                    )
                }
            }
        }

        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", pdfFile)
        PdfExportOutput(
            file = pdfFile,
            uri = uri,
            title = "Shift Handover (${shiftType.label})",
            suggestedFileName = fileName
        )
    }

    suspend fun generateClinicalReportPdf(
        context: Context,
        reportTitle: String,
        bodyText: String,
        clinicianName: String,
        department: String
    ): PdfExportOutput = withContext(Dispatchers.IO) {
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date())
        val safeTitle = reportTitle.replace(Regex("[^A-Za-z0-9_-]"), "_")
        val fileName = "WardRound_${safeTitle}.pdf"

        val pdfFile = createPdfFile(context, fileName) { writer ->
            writer.drawHospitalBanner(
                docTitle = reportTitle.uppercase(Locale.US),
                subtitle = "WARDROUND CLINICAL AUDIT REPORT",
                generatedBy = "$clinicianName ($department) • $timestamp",
                isDraft = false
            )

            // Split raw report into logical chunks so pagination is graceful across long lists
            val blocks = bodyText.split("\n\n").filter { it.isNotBlank() }
            blocks.forEach { block ->
                val lines = block.lines()
                val header = lines.firstOrNull()?.take(70) ?: "Section"
                val content = if (lines.size > 1) lines.drop(1).joinToString("\n") else block
                writer.drawSection(
                    heading = header,
                    body = content
                )
            }
        }

        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", pdfFile)
        PdfExportOutput(
            file = pdfFile,
            uri = uri,
            title = reportTitle,
            suggestedFileName = fileName
        )
    }

    fun sharePdf(context: Context, uri: Uri, title: String) {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, title)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Share Clinical PDF"))
    }

    fun copyPdfToUri(context: Context, sourceFile: File, destinationUri: Uri) {
        context.contentResolver.openOutputStream(destinationUri)?.use { out ->
            FileInputStream(sourceFile).use { input ->
                input.copyTo(out)
            }
        }
    }

    fun printPdf(context: Context, pdfFile: File, jobName: String) {
        val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager ?: return
        val adapter = object : PrintDocumentAdapter() {
            override fun onLayout(
                oldAttributes: PrintAttributes?,
                newAttributes: PrintAttributes?,
                cancellationSignal: CancellationSignal?,
                callback: LayoutResultCallback?,
                extras: Bundle?
            ) {
                if (cancellationSignal?.isCanceled == true) {
                    callback?.onLayoutCancelled()
                    return
                }
                val info = PrintDocumentInfo.Builder(pdfFile.name)
                    .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                    .build()
                callback?.onLayoutFinished(info, true)
            }

            override fun onWrite(
                pages: Array<out PageRange>?,
                destination: ParcelFileDescriptor?,
                cancellationSignal: CancellationSignal?,
                callback: WriteResultCallback?
            ) {
                val fd = destination?.fileDescriptor
                if (fd == null) {
                    callback?.onWriteFailed("Invalid print destination")
                    return
                }
                try {
                    FileInputStream(pdfFile).use { input ->
                        FileOutputStream(fd).use { output ->
                            input.copyTo(output)
                        }
                    }
                    callback?.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
                } catch (e: Exception) {
                    callback?.onWriteFailed(e.localizedMessage)
                }
            }
        }
        printManager.print(jobName, adapter, PrintAttributes.Builder().build())
    }

    private fun createPdfFile(
        context: Context,
        fileName: String,
        block: (A4PdfWriter) -> Unit
    ): File {
        val pdfDir = File(context.cacheDir, "pdfs")
        if (!pdfDir.exists()) pdfDir.mkdirs()
        val outFile = File(pdfDir, fileName)

        val document = PdfDocument()
        val writer = A4PdfWriter(document)
        block(writer)
        writer.finishAllPages()

        FileOutputStream(outFile).use { fos ->
            document.writeTo(fos)
        }
        document.close()
        return outFile
    }

    private class A4PdfWriter(private val document: PdfDocument) {
        private var currentPage: PdfDocument.Page? = null
        private var currentCanvas: Canvas? = null
        private var cursorY: Float = TOP_START_Y
        private var pageNumber: Int = 0
        private var currentDocTitle: String = "WardRound Clinical Document"

        private val headingPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_NAVY
            textSize = 10.5f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }

        private val bodyPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_TEXT_PRIMARY
            textSize = 9.5f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        }

        private val labelPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_TEXT_SECONDARY
            textSize = 8.5f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }

        private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }

        private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 0.9f
            color = COLOR_BORDER
        }

        init {
            startNewPage(firstPage = true)
        }

        private fun startNewPage(firstPage: Boolean = false) {
            currentPage?.let {
                document.finishPage(it)
            }
            pageNumber++
            val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
            val page = document.startPage(pageInfo)
            currentPage = page
            currentCanvas = page.canvas

            // White paper base
            fillPaint.color = Color.WHITE
            page.canvas.drawRect(0f, 0f, PAGE_WIDTH.toFloat(), PAGE_HEIGHT.toFloat(), fillPaint)

            if (!firstPage) {
                // Subtle continuation header
                val contPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = COLOR_TEXT_SECONDARY
                    textSize = 8f
                    typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.ITALIC)
                }
                page.canvas.drawText("$currentDocTitle (Continued)", MARGIN_X, 24f, contPaint)
                strokePaint.color = COLOR_BORDER
                page.canvas.drawLine(MARGIN_X, 28f, PAGE_WIDTH - MARGIN_X, 28f, strokePaint)
                cursorY = 40f
            } else {
                cursorY = TOP_START_Y
            }
        }

        private fun ensureSpace(neededHeight: Float) {
            if (cursorY + neededHeight > BOTTOM_LIMIT_Y) {
                drawPageFooter()
                startNewPage(firstPage = false)
            }
        }

        private fun drawPageFooter() {
            val canvas = currentCanvas ?: return
            strokePaint.color = COLOR_BORDER
            canvas.drawLine(MARGIN_X, PAGE_HEIGHT - 36f, PAGE_WIDTH - MARGIN_X, PAGE_HEIGHT - 36f, strokePaint)

            val footerPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = COLOR_TEXT_SECONDARY
                textSize = 7.5f
            }
            canvas.drawText(
                "CONFIDENTIAL PROTECTED HEALTH INFORMATION — WardRound Medical Platform",
                MARGIN_X,
                PAGE_HEIGHT - 22f,
                footerPaint
            )
            val pageLabel = "Page $pageNumber"
            val width = footerPaint.measureText(pageLabel)
            canvas.drawText(pageLabel, PAGE_WIDTH - MARGIN_X - width, PAGE_HEIGHT - 22f, footerPaint)
        }

        fun finishAllPages() {
            drawPageFooter()
            currentPage?.let { document.finishPage(it) }
            currentPage = null
        }

        fun drawHospitalBanner(
            docTitle: String,
            subtitle: String,
            generatedBy: String,
            isDraft: Boolean
        ) {
            currentDocTitle = docTitle
            val canvas = currentCanvas ?: return

            // Top Navy Banner
            val bannerRect = RectF(MARGIN_X, cursorY, PAGE_WIDTH - MARGIN_X, cursorY + 62f)
            fillPaint.color = COLOR_NAVY
            canvas.drawRoundRect(bannerRect, 8f, 8f, fillPaint)

            // Lime Accent strip on left
            fillPaint.color = COLOR_LIME
            canvas.drawRoundRect(
                RectF(MARGIN_X, cursorY, MARGIN_X + 6f, cursorY + 62f),
                4f,
                4f,
                fillPaint
            )

            val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = 13.5f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            }
            val subPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = if (isDraft) COLOR_LIME else Color.rgb(210, 224, 255)
                textSize = 9f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            }
            val metaPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(225, 232, 248)
                textSize = 8f
            }

            canvas.drawText(docTitle, MARGIN_X + 16f, cursorY + 21f, titlePaint)
            canvas.drawText(subtitle, MARGIN_X + 16f, cursorY + 37f, subPaint)
            canvas.drawText(generatedBy, MARGIN_X + 16f, cursorY + 51f, metaPaint)

            cursorY += 74f
        }

        fun drawInfoBox(title: String, rows: List<Pair<String, String>>) {
            val rowHeight = 14.5f
            val totalHeight = 24f + (rows.size * rowHeight) + 10f
            ensureSpace(totalHeight)

            val canvas = currentCanvas ?: return
            val boxRect = RectF(MARGIN_X, cursorY, PAGE_WIDTH - MARGIN_X, cursorY + totalHeight)
            fillPaint.color = COLOR_BG_SUBTLE
            canvas.drawRoundRect(boxRect, 6f, 6f, fillPaint)
            strokePaint.color = COLOR_BORDER
            canvas.drawRoundRect(boxRect, 6f, 6f, strokePaint)

            headingPaint.color = COLOR_NAVY
            canvas.drawText(title, MARGIN_X + 10f, cursorY + 16f, headingPaint)

            var y = cursorY + 31f
            rows.forEach { (k, v) ->
                canvas.drawText("$k:", MARGIN_X + 10f, y, labelPaint)
                canvas.drawText(v, MARGIN_X + 115f, y, bodyPaint)
                y += rowHeight
            }

            cursorY += totalHeight + 10f
        }

        fun drawSection(
            heading: String,
            body: String,
            accentColor: Int = COLOR_NAVY,
            highlightBox: Boolean = false
        ) {
            val textWidth = (CONTENT_WIDTH - 20f).toInt()
            val layout = StaticLayout.Builder
                .obtain(body, 0, body.length, bodyPaint, textWidth)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(2.5f, 1.0f)
                .build()

            val headerHeight = 18f
            val totalHeight = headerHeight + layout.height + 14f

            // If a single section is taller than a page, split lines smoothly; otherwise move whole section to next page
            if (totalHeight < (BOTTOM_LIMIT_Y - 60f)) {
                ensureSpace(totalHeight)
                val canvas = currentCanvas ?: return

                if (highlightBox) {
                    val bgRect = RectF(MARGIN_X, cursorY, PAGE_WIDTH - MARGIN_X, cursorY + totalHeight - 4f)
                    fillPaint.color = COLOR_BG_SUBTLE
                    canvas.drawRoundRect(bgRect, 6f, 6f, fillPaint)
                    strokePaint.color = accentColor
                    canvas.drawRoundRect(bgRect, 6f, 6f, strokePaint)
                }

                // Accent pill bar beside heading
                fillPaint.color = accentColor
                canvas.drawRect(MARGIN_X, cursorY + 3f, MARGIN_X + 3.5f, cursorY + 14f, fillPaint)

                headingPaint.color = accentColor
                canvas.drawText(heading, MARGIN_X + 9f, cursorY + 13f, headingPaint)

                canvas.save()
                canvas.translate(MARGIN_X + 9f, cursorY + headerHeight + 2f)
                layout.draw(canvas)
                canvas.restore()

                cursorY += totalHeight + 4f
            } else {
                // Chunk long body line-by-line across pages
                ensureSpace(34f)
                currentCanvas?.let { canvas ->
                    fillPaint.color = accentColor
                    canvas.drawRect(MARGIN_X, cursorY + 3f, MARGIN_X + 3.5f, cursorY + 14f, fillPaint)
                    headingPaint.color = accentColor
                    canvas.drawText(heading, MARGIN_X + 9f, cursorY + 13f, headingPaint)
                    cursorY += headerHeight + 4f
                }

                body.lines().forEach { line ->
                    val lineLayout = StaticLayout.Builder
                        .obtain(line.ifEmpty { " " }, 0, line.ifEmpty { " " }.length, bodyPaint, textWidth)
                        .build()
                    ensureSpace(lineLayout.height + 4f)
                    currentCanvas?.let { canvas ->
                        canvas.save()
                        canvas.translate(MARGIN_X + 9f, cursorY)
                        lineLayout.draw(canvas)
                        canvas.restore()
                    }
                    cursorY += lineLayout.height + 2f
                }
                cursorY += 8f
            }
        }

        fun drawSignatureFooterBlock(
            primaryLabel: String,
            primaryValue: String,
            secondaryLabel: String,
            secondaryValue: String
        ) {
            ensureSpace(56f)
            val canvas = currentCanvas ?: return
            val boxRect = RectF(MARGIN_X, cursorY + 6f, PAGE_WIDTH - MARGIN_X, cursorY + 52f)
            strokePaint.color = COLOR_NAVY
            canvas.drawRoundRect(boxRect, 6f, 6f, strokePaint)

            canvas.drawText("$primaryLabel: $primaryValue", MARGIN_X + 12f, cursorY + 24f, headingPaint)
            canvas.drawText("$secondaryLabel: $secondaryValue", MARGIN_X + 12f, cursorY + 41f, bodyPaint)
            cursorY += 58f
        }
    }
}
