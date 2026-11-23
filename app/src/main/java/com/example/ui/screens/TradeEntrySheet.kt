package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.analytics.TradingAnalytics
import com.example.data.model.Timeframe
import com.example.data.model.TradeDirection
import com.example.data.model.TradeEntry
import com.example.data.model.TradeResult
import com.example.ui.theme.LocalTradingColors
import com.example.ui.theme.RoyalBlue
import com.example.ui.theme.VibrantPurple
import com.example.utils.ImageUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TradeEntryScreen(
    existingTrade: TradeEntry?,
    ownerName: String,
    isBusy: Boolean,
    onDismiss: () -> Unit,
    onSave: (
        tradeDate: String,
        tradeTimestampMillis: Long,
        instrument: String,
        direction: String,
        timeframe: String,
        entryPrice: Double,
        exitPrice: Double,
        stopLoss: Double,
        takeProfit: Double,
        lotSize: Double,
        riskAmount: Double,
        profitLoss: Double,
        result: String,
        strategy: String,
        entryReason: String,
        exitReason: String,
        mistakes: String,
        lessonsLearned: String,
        notes: String,
        chartScreenshotBase64: String?
    ) -> Unit
) {
    BackHandler(onBack = onDismiss)

    val context = LocalContext.current
    val colors = LocalTradingColors.current

    var userNameField by remember(existingTrade) {
        mutableStateOf(existingTrade?.userName?.ifBlank { ownerName } ?: ownerName)
    }
    var tradeDate by remember(existingTrade) {
        mutableStateOf(existingTrade?.tradeDate?.ifBlank { TradingAnalytics.todayIsoDate() } ?: TradingAnalytics.todayIsoDate())
    }
    var tradeMillis by remember(existingTrade) {
        mutableLongStateOf(existingTrade?.tradeTimestampMillis?.takeIf { it > 0L } ?: System.currentTimeMillis())
    }
    var showDatePicker by remember { mutableStateOf(false) }

    var instrument by remember(existingTrade) { mutableStateOf(existingTrade?.instrument ?: "") }
    var direction by remember(existingTrade) { mutableStateOf(existingTrade?.directionEnum ?: TradeDirection.BUY) }
    var timeframe by remember(existingTrade) { mutableStateOf(existingTrade?.timeframeEnum ?: Timeframe.M15) }

    var entryPriceStr by remember(existingTrade) {
        mutableStateOf(existingTrade?.entryPrice?.takeIf { it > 0.0 }?.toString() ?: "")
    }
    var exitPriceStr by remember(existingTrade) {
        mutableStateOf(existingTrade?.exitPrice?.takeIf { it > 0.0 }?.toString() ?: "")
    }
    var stopLossStr by remember(existingTrade) {
        mutableStateOf(existingTrade?.stopLoss?.takeIf { it > 0.0 }?.toString() ?: "")
    }
    var takeProfitStr by remember(existingTrade) {
        mutableStateOf(existingTrade?.takeProfit?.takeIf { it > 0.0 }?.toString() ?: "")
    }
    var lotSizeStr by remember(existingTrade) {
        mutableStateOf(existingTrade?.lotSize?.toString() ?: "1")
    }
    var riskAmountStr by remember(existingTrade) {
        mutableStateOf(existingTrade?.riskAmount?.takeIf { it > 0.0 }?.toString() ?: "")
    }
    var profitLossStr by remember(existingTrade) {
        mutableStateOf(existingTrade?.let { it.profitLoss.toString() } ?: "")
    }
    var result by remember(existingTrade) { mutableStateOf(existingTrade?.resultEnum ?: TradeResult.WIN) }
    var strategy by remember(existingTrade) { mutableStateOf(existingTrade?.strategy ?: "") }
    var entryReason by remember(existingTrade) { mutableStateOf(existingTrade?.entryReason ?: "") }
    var exitReason by remember(existingTrade) { mutableStateOf(existingTrade?.exitReason ?: "") }
    var mistakes by remember(existingTrade) { mutableStateOf(existingTrade?.mistakes ?: "") }
    var lessonsLearned by remember(existingTrade) { mutableStateOf(existingTrade?.lessonsLearned ?: "") }
    var notes by remember(existingTrade) { mutableStateOf(existingTrade?.notes ?: "") }
    var screenshotBase64 by remember(existingTrade) { mutableStateOf(existingTrade?.chartScreenshotBase64) }
    var validationError by remember { mutableStateOf<String?>(null) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            val encoded = ImageUtils.encodeUriToBase64(context, uri)
            if (encoded != null) {
                screenshotBase64 = encoded
                validationError = null
            } else {
                validationError = "Could not compress selected image. Please pick another screenshot."
            }
        }
    }

    val quickInstruments = listOf("NIFTY", "BANKNIFTY", "FINNIFTY", "XAUUSD", "BTCUSD", "EURUSD", "RELIANCE")
    val quickStrategies = listOf("Price Action", "SMC", "Breakout", "VWAP Reversal", "EMA Crossover", "Scalping")

    fun autoFillPnlIfEmpty() {
        val entry = entryPriceStr.toDoubleOrNull() ?: return
        val exit = exitPriceStr.toDoubleOrNull() ?: return
        val qty = lotSizeStr.toDoubleOrNull() ?: 1.0
        val diff = if (direction == TradeDirection.BUY) (exit - entry) else (entry - exit)
        val pnl = diff * qty
        profitLossStr = String.format(Locale.US, "%.2f", pnl)
        result = when {
            pnl > 0.0 -> TradeResult.WIN
            pnl < 0.0 -> TradeResult.LOSS
            else -> TradeResult.BREAK_EVEN
        }
        val sl = stopLossStr.toDoubleOrNull()
        if (sl != null && sl > 0.0 && riskAmountStr.isBlank()) {
            val risk = abs(entry - sl) * qty
            riskAmountStr = String.format(Locale.US, "%.2f", risk)
        }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding(),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = if (existingTrade == null) "Log New Trade" else "Edit Trade",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = existingTrade?.tradeCode ?: "Trade ID: Auto-generated on save",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.gold
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("close_trade_entry_button")
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close trade entry")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (!validationError.isNullOrBlank()) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = colors.lossContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = validationError ?: "",
                        color = colors.loss,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = userNameField,
                    onValueChange = { userNameField = it },
                    label = { Text("User Name") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = tradeDate,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Date") },
                    trailingIcon = {
                        IconButton(onClick = { showDatePicker = true }) {
                            Icon(Icons.Default.CalendarToday, contentDescription = "Pick date")
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .clickable { showDatePicker = true }
                )
            }

            OutlinedTextField(
                value = instrument,
                onValueChange = { instrument = it.uppercase(Locale.US) },
                label = { Text("Trading Instrument * (e.g. NIFTY, XAUUSD)") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("trade_instrument_input")
            )

            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                quickInstruments.forEach { sym ->
                    FilterChip(
                        selected = instrument == sym,
                        onClick = { instrument = sym },
                        label = { Text(sym) }
                    )
                }
            }

            // Direction & Timeframe
            Text("Buy / Sell", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TradeDirection.entries.forEach { dir ->
                    FilterChip(
                        selected = direction == dir,
                        onClick = { direction = dir },
                        label = { Text(dir.label.uppercase()) },
                        modifier = Modifier.testTag("direction_${dir.name.lowercase()}")
                    )
                }
            }

            Text("Timeframe", style = MaterialTheme.typography.labelLarge)
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Timeframe.entries.forEach { tf ->
                    FilterChip(
                        selected = timeframe == tf,
                        onClick = { timeframe = tf },
                        label = { Text(tf.label) },
                        modifier = Modifier.testTag("timeframe_${tf.name.lowercase()}")
                    )
                }
            }

            // Prices
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = entryPriceStr,
                    onValueChange = { entryPriceStr = it },
                    label = { Text("Entry Price") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("trade_entry_price_input")
                )
                OutlinedTextField(
                    value = exitPriceStr,
                    onValueChange = { exitPriceStr = it },
                    label = { Text("Exit Price") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("trade_exit_price_input")
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = stopLossStr,
                    onValueChange = { stopLossStr = it },
                    label = { Text("Stop Loss") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = takeProfitStr,
                    onValueChange = { takeProfitStr = it },
                    label = { Text("Take Profit") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = lotSizeStr,
                    onValueChange = { lotSizeStr = it },
                    label = { Text("Lot Size *") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = riskAmountStr,
                    onValueChange = { riskAmountStr = it },
                    label = { Text("Risk Amount (₹)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = profitLossStr,
                    onValueChange = {
                        profitLossStr = it
                        val v = it.toDoubleOrNull()
                        if (v != null) {
                            result = when {
                                v > 0.0 -> TradeResult.WIN
                                v < 0.0 -> TradeResult.LOSS
                                else -> TradeResult.BREAK_EVEN
                            }
                        }
                    },
                    label = { Text("Actual Profit / Loss (₹) *") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("trade_pnl_input")
                )
                OutlinedButton(
                    onClick = { autoFillPnlIfEmpty() },
                    modifier = Modifier.height(54.dp)
                ) {
                    Text("Auto Calc")
                }
            }

            Text("Result", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TradeResult.entries.forEach { res ->
                    FilterChip(
                        selected = result == res,
                        onClick = { result = res },
                        label = { Text(res.label) },
                        modifier = Modifier.testTag("result_${res.name.lowercase()}")
                    )
                }
            }

            OutlinedTextField(
                value = strategy,
                onValueChange = { strategy = it },
                label = { Text("Trading Strategy") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                quickStrategies.forEach { st ->
                    FilterChip(
                        selected = strategy == st,
                        onClick = { strategy = st },
                        label = { Text(st) }
                    )
                }
            }

            OutlinedTextField(
                value = entryReason,
                onValueChange = { entryReason = it },
                label = { Text("Entry Reason") },
                minLines = 2,
                maxLines = 4,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = exitReason,
                onValueChange = { exitReason = it },
                label = { Text("Exit Reason") },
                minLines = 2,
                maxLines = 4,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = mistakes,
                onValueChange = { mistakes = it },
                label = { Text("Mistakes (comma-separated for tracking)") },
                minLines = 2,
                maxLines = 4,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = lessonsLearned,
                onValueChange = { lessonsLearned = it },
                label = { Text("Lessons Learned") },
                minLines = 2,
                maxLines = 4,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Additional Notes") },
                minLines = 2,
                maxLines = 4,
                modifier = Modifier.fillMaxWidth()
            )

            // Chart Screenshot Upload via Zero-Permission Photo Picker
            Text("Chart Screenshot Upload", style = MaterialTheme.typography.labelLarge)
            val decodedPreview = remember(screenshotBase64) { ImageUtils.decodeBase64ToImageBitmap(screenshotBase64) }
            if (decodedPreview != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(190.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .border(1.dp, colors.cardBorder, RoundedCornerShape(16.dp))
                ) {
                    Image(
                        bitmap = decodedPreview,
                        contentDescription = "Selected chart screenshot",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                    IconButton(
                        onClick = { screenshotBase64 = null },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color.Black.copy(alpha = 0.7f))
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Remove screenshot", tint = Color.White)
                    }
                }
            }

            OutlinedButton(
                onClick = {
                    photoPickerLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("upload_screenshot_button")
            ) {
                Icon(Icons.Default.AddPhotoAlternate, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(if (decodedPreview == null) "Upload Chart Screenshot" else "Change Chart Screenshot")
            }

            Spacer(Modifier.height(6.dp))
            Button(
                onClick = {
                    val cleanInst = instrument.trim()
                    val lot = lotSizeStr.toDoubleOrNull()
                    val pnl = profitLossStr.toDoubleOrNull()
                    val entry = entryPriceStr.toDoubleOrNull() ?: 0.0
                    val exit = exitPriceStr.toDoubleOrNull() ?: 0.0
                    val sl = stopLossStr.toDoubleOrNull() ?: 0.0
                    val tp = takeProfitStr.toDoubleOrNull() ?: 0.0
                    val risk = riskAmountStr.toDoubleOrNull() ?: 0.0

                    when {
                        cleanInst.isBlank() -> validationError = "Please enter a Trading Instrument (e.g. NIFTY)."
                        lot == null || lot <= 0.0 -> validationError = "Lot Size must be greater than 0."
                        pnl == null -> validationError = "Please enter Actual Profit / Loss (use 0 for Break Even)."
                        entry < 0.0 || exit < 0.0 || sl < 0.0 || tp < 0.0 || risk < 0.0 ->
                            validationError = "Prices and risk amount cannot be negative."
                        else -> {
                            validationError = null
                            onSave(
                                tradeDate,
                                tradeMillis,
                                cleanInst,
                                direction.name,
                                timeframe.name,
                                entry,
                                exit,
                                sl,
                                tp,
                                lot,
                                risk,
                                pnl,
                                result.name,
                                strategy,
                                entryReason,
                                exitReason,
                                mistakes,
                                lessonsLearned,
                                notes,
                                screenshotBase64
                            )
                        }
                    }
                },
                enabled = !isBusy,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("save_trade_button"),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                contentPadding = androidx.compose.foundation.layout.PaddingValues()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Brush.horizontalGradient(listOf(RoyalBlue, VibrantPurple))),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null, tint = Color.White)
                        Text(
                            text = if (isBusy) "Saving..." else if (existingTrade == null) "Save Trade Record" else "Update Trade Record",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color.White
                        )
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showDatePicker) {
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = tradeMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    val picked = pickerState.selectedDateMillis
                    if (picked != null) {
                        tradeMillis = picked
                        tradeDate = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(picked))
                    }
                    showDatePicker = false
                }) {
                    Text("Confirm")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Cancel")
                }
            }
        ) {
            DatePicker(state = pickerState)
        }
    }
}
