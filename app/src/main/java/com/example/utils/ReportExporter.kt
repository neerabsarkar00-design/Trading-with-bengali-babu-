package com.example.utils

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import com.example.R
import com.example.data.analytics.DashboardStats
import com.example.data.analytics.formatCurrency
import com.example.data.analytics.formatSignedCurrency
import com.example.data.model.DisciplineEntry
import com.example.data.model.StudentInvite
import com.example.data.model.TradeEntry
import com.example.data.model.UserProfile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ReportExporter {

    private fun exportsDir(context: Context): File {
        val dir = File(context.cacheDir, "exports")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun exportAndSharePdf(
        context: Context,
        reportTitle: String,
        periodLabel: String,
        traderLabel: String,
        stats: DashboardStats,
        trades: List<TradeEntry>,
        disciplineEntries: List<DisciplineEntry>
    ): Result<String> {
        return try {
            val pdf = PdfDocument()
            val pageWidth = 595
            val pageHeight = 842
            var pageNum = 1

            fun newPage(): PdfDocument.Page {
                return pdf.startPage(
                    PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNum++).create()
                )
            }

            var page = newPage()
            var canvas: Canvas = page.canvas

            val navyPaint = Paint().apply { color = Color.rgb(12, 25, 51) }
            val goldPaint = Paint().apply {
                color = Color.rgb(245, 158, 11)
                textSize = 11f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val titlePaint = Paint().apply {
                color = Color.WHITE
                textSize = 18f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val subtitlePaint = Paint().apply {
                color = Color.rgb(203, 213, 225)
                textSize = 10f
            }
            val headingPaint = Paint().apply {
                color = Color.rgb(15, 23, 42)
                textSize = 13f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val bodyPaint = Paint().apply {
                color = Color.rgb(30, 41, 59)
                textSize = 9.5f
            }
            val mutedPaint = Paint().apply {
                color = Color.rgb(100, 116, 139)
                textSize = 8.5f
            }
            val profitPaint = Paint().apply {
                color = Color.rgb(5, 150, 105)
                textSize = 9.5f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val lossPaint = Paint().apply {
                color = Color.rgb(220, 38, 38)
                textSize = 9.5f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val cardBgPaint = Paint().apply { color = Color.rgb(241, 245, 249) }
            val linePaint = Paint().apply {
                color = Color.rgb(226, 232, 240)
                strokeWidth = 1f
            }

            // Header Banner
            canvas.drawRect(0f, 0f, pageWidth.toFloat(), 96f, navyPaint)
            val logoOpts = BitmapFactory.Options().apply { inSampleSize = 4 }
            val logoBmp = BitmapFactory.decodeResource(context.resources, R.drawable.img_brand_logo, logoOpts)
            if (logoBmp != null) {
                val scaled = Bitmap.createScaledBitmap(logoBmp, 64, 64, true)
                canvas.drawBitmap(scaled, 24f, 16f, null)
                scaled.recycle()
                logoBmp.recycle()
            }
            canvas.drawText("TRADING WITH BANGALI BABU", 102f, 34f, goldPaint)
            canvas.drawText(reportTitle, 102f, 56f, titlePaint)
            canvas.drawText("Trader: $traderLabel  |  Period: $periodLabel", 102f, 76f, subtitlePaint)

            var y = 118f
            canvas.drawText("Performance Summary", 24f, y, headingPaint)
            y += 12f

            val metrics = listOf(
                "Total Trades" to stats.totalTrades.toString(),
                "Win Rate" to "${stats.winRatePercent}% (${stats.winningTrades}W / ${stats.losingTrades}L / ${stats.breakEvenTrades}BE)",
                "Net Profit / Loss" to formatSignedCurrency(stats.netProfitLoss),
                "Profit Factor" to if (stats.profitFactorInfinite) "∞" else String.format(Locale.US, "%.2f", stats.profitFactor),
                "Total Profit" to formatCurrency(stats.totalProfit),
                "Total Loss" to formatCurrency(stats.totalLoss),
                "Avg Win / Loss" to "${formatCurrency(stats.averageWin)} / ${formatCurrency(stats.averageLoss)}",
                "Avg R:R | SL Discipline" to "1:${stats.averageRiskReward} | ${stats.stopLossDisciplinePercent}%"
            )

            val colWidth = (pageWidth - 60f) / 2f
            metrics.chunked(2).forEach { row ->
                var x = 24f
                row.forEach { (k, v) ->
                    canvas.drawRoundRect(RectF(x, y, x + colWidth, y + 38f), 6f, 6f, cardBgPaint)
                    canvas.drawText(k, x + 10f, y + 14f, mutedPaint)
                    val valuePaint = when {
                        k.contains("Net") && stats.netProfitLoss > 0 -> profitPaint
                        k.contains("Net") && stats.netProfitLoss < 0 -> lossPaint
                        else -> headingPaint.apply { textSize = 10.5f }
                    }
                    canvas.drawText(v.take(36), x + 10f, y + 30f, valuePaint)
                    x += colWidth + 12f
                }
                y += 44f
            }

            headingPaint.textSize = 13f
            y += 10f
            canvas.drawText("Trade History (${trades.size} Records)", 24f, y, headingPaint)
            y += 16f

            fun drawTableHeader() {
                canvas.drawRect(24f, y - 11f, pageWidth - 24f, y + 6f, navyPaint)
                val hdrPaint = Paint().apply {
                    color = Color.WHITE
                    textSize = 8.5f
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                }
                canvas.drawText("Trade ID", 28f, y, hdrPaint)
                canvas.drawText("Date", 118f, y, hdrPaint)
                canvas.drawText("Symbol", 184f, y, hdrPaint)
                canvas.drawText("Side/TF", 254f, y, hdrPaint)
                canvas.drawText("Entry->Exit", 312f, y, hdrPaint)
                canvas.drawText("Result", 412f, y, hdrPaint)
                canvas.drawText("P&L", 480f, y, hdrPaint)
                y += 14f
            }

            drawTableHeader()

            trades.forEach { trade ->
                if (y > pageHeight - 48) {
                    pdf.finishPage(page)
                    page = newPage()
                    canvas = page.canvas
                    y = 40f
                    drawTableHeader()
                }
                canvas.drawText(trade.tradeCode.take(15), 28f, y, bodyPaint)
                canvas.drawText(trade.tradeDate.take(10), 118f, y, bodyPaint)
                canvas.drawText(trade.instrument.take(11), 184f, y, bodyPaint)
                canvas.drawText("${trade.direction}/${trade.timeframe}", 254f, y, bodyPaint)
                canvas.drawText(
                    String.format(Locale.US, "%.2f->%.2f", trade.entryPrice, trade.exitPrice).take(16),
                    312f,
                    y,
                    bodyPaint
                )
                canvas.drawText(trade.resultEnum.label, 412f, y, bodyPaint)
                val pnlPaint = when {
                    trade.profitLoss > 0 -> profitPaint
                    trade.profitLoss < 0 -> lossPaint
                    else -> bodyPaint
                }
                canvas.drawText(formatSignedCurrency(trade.profitLoss), 480f, y, pnlPaint)
                y += 6f
                canvas.drawLine(24f, y, pageWidth - 24f, y, linePaint)
                y += 12f
            }

            if (disciplineEntries.isNotEmpty()) {
                if (y > pageHeight - 100) {
                    pdf.finishPage(page)
                    page = newPage()
                    canvas = page.canvas
                    y = 40f
                }
                y += 8f
                canvas.drawText("Discipline & Psychology Notes (${disciplineEntries.size})", 24f, y, headingPaint)
                y += 16f
                disciplineEntries.take(8).forEach { entry ->
                    if (y > pageHeight - 40) {
                        pdf.finishPage(page)
                        page = newPage()
                        canvas = page.canvas
                        y = 40f
                    }
                    canvas.drawText(
                        "${entry.periodLabel} [${entry.entryTypeEnum.label}] Score ${entry.disciplineScore}/10 • ${entry.moodTag}",
                        24f,
                        y,
                        bodyPaint
                    )
                    y += 12f
                    if (entry.psychologyNotes.isNotBlank()) {
                        canvas.drawText("Note: ${entry.psychologyNotes.take(90)}", 32f, y, mutedPaint)
                        y += 12f
                    }
                }
            }

            pdf.finishPage(page)

            val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val outFile = File(exportsDir(context), "BangaliBabu_Report_$ts.pdf")
            FileOutputStream(outFile).use { fos -> pdf.writeTo(fos) }
            pdf.close()

            shareFile(context, outFile, "application/pdf", "Share Trading PDF Report")
            Result.success(outFile.name)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun exportAndShareCsv(
        context: Context,
        trades: List<TradeEntry>,
        filePrefix: String = "BangaliBabu_Trades"
    ): Result<String> {
        return try {
            val sb = StringBuilder()
            // UTF-8 BOM for clean Excel opening
            sb.append('\uFEFF')
            sb.appendLine(
                "Trade ID,User Name,Date,Instrument,Direction,Timeframe,Entry Price,Exit Price,Stop Loss,Take Profit,Lot Size,Risk Amount,Profit/Loss,Result,R:R Ratio,SL Used,Strategy,Entry Reason,Exit Reason,Mistakes,Lessons Learned,Additional Notes"
            )
            trades.forEach { t ->
                val row = listOf(
                    t.tradeCode,
                    t.userName,
                    t.tradeDate,
                    t.instrument,
                    t.direction,
                    t.timeframe,
                    t.entryPrice.toString(),
                    t.exitPrice.toString(),
                    t.stopLoss.toString(),
                    t.takeProfit.toString(),
                    t.lotSize.toString(),
                    t.riskAmount.toString(),
                    t.profitLoss.toString(),
                    t.resultEnum.label,
                    t.riskRewardRatio.toString(),
                    if (t.stopLossUsed) "Yes" else "No",
                    t.strategy,
                    t.entryReason,
                    t.exitReason,
                    t.mistakes,
                    t.lessonsLearned,
                    t.notes
                ).joinToString(",") { csvEscape(it) }
                sb.appendLine(row)
            }

            val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val outFile = File(exportsDir(context), "${filePrefix}_$ts.csv")
            outFile.writeText(sb.toString(), Charsets.UTF_8)
            shareFile(context, outFile, "text/csv", "Share Excel / CSV Report")
            Result.success(outFile.name)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun exportAndShareBackupJson(
        context: Context,
        currentUser: UserProfile,
        trades: List<TradeEntry>,
        disciplineEntries: List<DisciplineEntry>,
        students: List<UserProfile>,
        invites: List<StudentInvite>
    ): Result<String> {
        return try {
            val root = JSONObject().apply {
                put("brand", "Trading with Bangali Babu")
                put("exportedAt", SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(Date()))
                put("exportedBy", currentUser.email)
                put("role", currentUser.role)
                put("tradesCount", trades.size)
                put("trades", JSONArray().apply {
                    trades.forEach { t ->
                        put(JSONObject().apply {
                            put("id", t.id)
                            put("userId", t.userId)
                            put("tradeCode", t.tradeCode)
                            put("userName", t.userName)
                            put("tradeDate", t.tradeDate)
                            put("instrument", t.instrument)
                            put("direction", t.direction)
                            put("timeframe", t.timeframe)
                            put("entryPrice", t.entryPrice)
                            put("exitPrice", t.exitPrice)
                            put("stopLoss", t.stopLoss)
                            put("takeProfit", t.takeProfit)
                            put("lotSize", t.lotSize)
                            put("riskAmount", t.riskAmount)
                            put("profitLoss", t.profitLoss)
                            put("result", t.result)
                            put("riskRewardRatio", t.riskRewardRatio)
                            put("stopLossUsed", t.stopLossUsed)
                            put("strategy", t.strategy)
                            put("entryReason", t.entryReason)
                            put("exitReason", t.exitReason)
                            put("mistakes", t.mistakes)
                            put("lessonsLearned", t.lessonsLearned)
                            put("notes", t.notes)
                            put("hasScreenshot", !t.chartScreenshotBase64.isNullOrBlank())
                        })
                    }
                })
                put("disciplineEntries", JSONArray().apply {
                    disciplineEntries.forEach { d ->
                        put(JSONObject().apply {
                            put("id", d.id)
                            put("userId", d.userId)
                            put("userName", d.userName)
                            put("entryType", d.entryType)
                            put("periodLabel", d.periodLabel)
                            put("moodTag", d.moodTag)
                            put("followedRules", d.followedRules)
                            put("respectedStopLoss", d.respectedStopLoss)
                            put("overtraded", d.overtraded)
                            put("psychologyNotes", d.psychologyNotes)
                            put("mistakesSummary", d.mistakesSummary)
                            put("improvementsPlan", d.improvementsPlan)
                            put("disciplineScore", d.disciplineScore)
                        })
                    }
                })
                if (currentUser.isAdminUser) {
                    put("students", JSONArray().apply {
                        students.forEach { s ->
                            put(JSONObject().apply {
                                put("userId", s.userId)
                                put("name", s.name)
                                put("email", s.email)
                                put("status", s.status)
                                put("tradingCapital", s.tradingCapital)
                                put("dailyGoal", s.dailyGoal)
                                put("dailyMaxLoss", s.dailyMaxLoss)
                                put("maxTradesPerDay", s.maxTradesPerDay)
                            })
                        }
                    })
                    put("invites", JSONArray().apply {
                        invites.forEach { inv ->
                            put(JSONObject().apply {
                                put("email", inv.email)
                                put("name", inv.name)
                                put("status", inv.status)
                                put("tradingCapital", inv.tradingCapital)
                            })
                        }
                    })
                }
            }

            val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val outFile = File(exportsDir(context), "BangaliBabu_Backup_$ts.json")
            outFile.writeText(root.toString(2), Charsets.UTF_8)
            shareFile(context, outFile, "application/json", "Share Database Backup")
            Result.success(outFile.name)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun shareFile(context: Context, file: File, mimeType: String, chooserTitle: String) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, chooserTitle))
    }

    private fun csvEscape(raw: String): String {
        val cleaned = raw.replace("\r", " ").replace("\n", " ")
        return "\"${cleaned.replace("\"", "\"\"")}\""
    }
}
