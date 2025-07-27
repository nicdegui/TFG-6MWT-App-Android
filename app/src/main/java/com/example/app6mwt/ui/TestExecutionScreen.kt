package com.example.app6mwt.ui

import android.app.Activity
import android.util.Log
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.app6mwt.ui.theme.*
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import java.util.*
import java.util.concurrent.TimeUnit

@Composable
fun BluetoothIconStatus.toActualComposeColor(): Color {
    return when (this) {
        BluetoothIconStatus.GREEN -> Color(0xFF4CAF50)
        BluetoothIconStatus.YELLOW -> Color(0xFFFFC107)
        BluetoothIconStatus.RED -> MaterialTheme.colorScheme.error
        BluetoothIconStatus.CONNECTING -> Color(0xFF2196F3)
        BluetoothIconStatus.GRAY -> Color.Gray
    }
}

@Composable
fun StatusColor.toActualColor(): Color {
    return when (this) {
        StatusColor.NORMAL -> Color(0xFF4CAF50)
        StatusColor.WARNING -> Color(0xFFFFC107)
        StatusColor.CRITICAL -> MaterialTheme.colorScheme.error
        StatusColor.UNKNOWN -> Color.Gray
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TestExecutionScreen(
    preparationData: TestPreparationData,
    onNavigateBackFromScreen: () -> Unit,
    onNavigateToResults: (testExecutionSummaryData: TestExecutionSummaryData) -> Unit,
    viewModel: TestExecutionViewModel = hiltViewModel() // Hilt se encarga de la creación y dependencias
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val window = (LocalView.current.context as? Activity)?.window

    SideEffect {
        if (uiState.isTestRunning) {
            window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    // Manejo del botón de retroceso del dispositivo
    BackHandler(enabled = true) {
        Log.d("TestExecutionScreen", "BackHandler presionado. Solicitando confirmación de salida.")
        viewModel.requestExitConfirmation()
    }

    LaunchedEffect(key1 = preparationData) {
        Log.d("TestExecutionScreen", "LaunchedEffect: Initializing ViewModel with preparationData: ID ${preparationData.patientId}")
        viewModel.initializeTest(preparationData)
    }

    LaunchedEffect(uiState.testSummaryDataForNavigation, uiState.isTestFinished, uiState.testFinishedInfoMessage, uiState.showNavigateToResultsConfirmationDialog) {
        val summary = uiState.testSummaryDataForNavigation
        if (summary != null && uiState.isTestFinished && uiState.testFinishedInfoMessage == null && !uiState.showNavigateToResultsConfirmationDialog){
            onNavigateToResults(summary)
            viewModel.onNavigationToResultsCompleted()
        }
    }

    LaunchedEffect(uiState.userMessage) {
        uiState.userMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.clearUserMessage()
        }
    }
    App6MWTTheme {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            text = "EJECUCIÓN PRUEBA 6MWT - ${uiState.patientFullName.uppercase()}",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontSize = 25.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    navigationIcon = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // Icono 1: Flecha de retroceso (siempre visible)
                            IconButton(onClick = {
                                Log.d("TestExecutionScreen", "TopAppBar Back presionado. Solicitando confirmación de salida.")
                                viewModel.requestExitConfirmation()
                            }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Regresar", tint = TextOnSecondary)
                            }
                            // Icono 2: "Continuar a Resultados" (visible solo cuando sea necesario)
                            if (uiState.canNavigateToResults) {
                                IconButton(
                                    onClick = { viewModel.onContinueToResultsClicked() },
                                    modifier = Modifier
                                        .padding(start = 8.dp)
                                        .size(40.dp)
                                        .background(
                                            SuccessGreenColor,
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                        contentDescription = "Continuar a resultados",
                                        tint = Color.White,
                                        modifier = Modifier.size(24.dp),

                                    )
                                }
                            }
                        }
                    },
                    actions = {
                        Text(
                            text = "ID: ${uiState.patientId.uppercase()}",
                            modifier = Modifier
                                .padding(end = 16.dp)
                                .align(Alignment.CenterVertically),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            fontSize = 25.sp,
                            color = TextOnSecondary,
                        )
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        titleContentColor = TextOnSecondary,
                        actionIconContentColor = TextOnSecondary
                    )
                )
            },
            containerColor = BackgroundColor
        ) { paddingValues ->
            // CAMBIO: Mostrar contenido principal solo si preparationDataLoaded es true
            if (uiState.preparationDataLoaded) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    LeftSection(
                        modifier = Modifier
                            .weight(0.50f)
                            .fillMaxHeight(),
                        uiState = uiState
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    CentralSection(
                        modifier = Modifier
                            .weight(0.22f)
                            .fillMaxHeight(),
                        uiState = uiState,
                        viewModel = viewModel
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    RightSection(
                        modifier = Modifier
                            .weight(0.28f)
                            .fillMaxHeight(),
                        uiState = uiState,
                        viewModel = viewModel
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues), contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
        }

        // --- DIÁLOGOS ---
        // Diálogo de confirmación para RESTART (durante la prueba) o REINITIALIZE (después de la prueba)
        if (uiState.showMainActionConfirmationDialog) {
            val title: String
            val confirmText: String
            val onConfirmAction: () -> Unit

            when (uiState.mainButtonAction) {
                MainButtonAction.RESTART_DURING_TEST -> {
                    title = "Confirmar reinicio de prueba"
                    confirmText = "Reiniciar prueba"
                    onConfirmAction = { viewModel.confirmRestartTestAndReturnToConfig() }
                }
                MainButtonAction.REINITIALIZE_AFTER_TEST -> {
                    title = "Confirmar reconfiguración"
                    confirmText = "Reconfigurar"
                    onConfirmAction = { viewModel.confirmReinitializeTestToConfig() }
                }
                else -> { // MainButtonAction.START o un estado inesperado
                    title = "Confirmar acción" // Fallback
                    confirmText = "Confirmar" // Fallback
                    onConfirmAction = {} // No debería llegar aquí si el botón START no muestra este diálogo
                }
            }

            ConfirmationDialog(
                title = title,
                text = uiState.mainActionConfirmationMessage, // El mensaje viene del ViewModel
                onConfirm = {
                    onConfirmAction()
                    // El ViewModel es responsable de poner showMainActionConfirmationDialog a false
                },
                onDismiss = { viewModel.dismissMainActionConfirmationDialog() },
                confirmButtonText = confirmText,
                dismissButtonText = "Cancelar"
            )
        }

        if (uiState.showExitConfirmationDialog) {
            ConfirmationDialog(
                title = "Confirmar salida",
                text = "Si sale, la prueba actual se cancelará y los datos no guardados se perderán. ¿Está seguro?",
                onConfirm = {
                    viewModel.confirmExitTest()
                    onNavigateBackFromScreen()
                },
                onDismiss = { viewModel.dismissExitConfirmation() },
                confirmButtonText = "Salir",
                dismissButtonText = "Cancelar"
            )
        }
        // Diálogo para el mensaje informativo de fin de prueba (completada o detenida)
        uiState.testFinishedInfoMessage?.let { message ->
            val isCompletedNormally = uiState.currentTimeMillis >= TEST_DURATION_MILLIS
            val titleText = if (isCompletedNormally) "Prueba finalizada" else "Prueba detenida"
            InfoDialog(
                title = titleText,
                text = message,
                onDismiss = {
                    viewModel.dismissTestFinishedInfoDialog()
                },
                buttonText = "Entendido"
            )
        }

        if (uiState.showStopConfirmationDialog) {
            CountdownDialog(
                title = "Deteniendo prueba",
                countdownValue = uiState.stopCountdownSeconds,
                onCancel = { viewModel.cancelStopTest() }
            )
        }

        if (uiState.showNavigateToResultsConfirmationDialog) {
            ConfirmationDialog(
                title = "Ver resultados",
                text = NAVIGATE_TO_RESULTS_CONFIRMATION_MESSAGE,
                onConfirm = { viewModel.confirmNavigateToResults() },
                onDismiss = { viewModel.dismissNavigateToResultsConfirmation() },
                confirmButtonText = "Continuar",
                dismissButtonText = "Cancelar"
            )
        }

        if (uiState.showDeleteLastStopConfirmationDialog) {
            ConfirmationDialog(
                title = "Eliminar última parada",
                text = "¿Está seguro de que desea eliminar la última parada registrada?",
                onConfirm = { viewModel.confirmDeleteLastStop() },
                onDismiss = { viewModel.dismissDeleteLastStopConfirmation() },
                confirmButtonText = "Eliminar",
                dismissButtonText = "Cancelar"
            )
        }

        val isCriticalAlarm = uiState.spo2StatusColor == StatusColor.CRITICAL || uiState.heartRateStatusColor == StatusColor.CRITICAL
        if (isCriticalAlarm && uiState.isTestRunning) {
            LaunchedEffect(
                uiState.spo2StatusColor,
                uiState.heartRateStatusColor,
                uiState.isTestRunning
            ) {

                if (uiState.isTestRunning && (uiState.spo2StatusColor == StatusColor.CRITICAL || uiState.heartRateStatusColor == StatusColor.CRITICAL)) {
                    Toast.makeText(context, "¡ALERTA! Valor crítico detectado.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}

@Composable
fun LeftSection(modifier: Modifier = Modifier, uiState: TestExecutionUiState) {
    val chartTextColor = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()
    val chartGridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f).toArgb()
    val chartLineColorSpo2 = MaterialTheme.colorScheme.tertiary.toArgb()
    val chartLineColorHr = MaterialTheme.colorScheme.secondary.toArgb()

    Column(
        modifier = modifier.fillMaxHeight(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ExecutionSectionCard(
            modifier = Modifier.weight(1f),
            title = "SpO2 (%)",
            titleStyle = MaterialTheme.typography.titleLarge.copy(
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        ) {

            if (!uiState.isConfigPhase && uiState.spo2DataPoints.isNotEmpty()) {
                LineChartComposable(
                    modifier = Modifier.fillMaxSize(),
                    dataPoints = uiState.spo2DataPoints,
                    yAxisMin = 85f,
                    yAxisMax = 100f,
                    yAxisLabelCount = 5,
                    xAxisLabelCount = 7,
                    lineColor = chartLineColorSpo2,
                    textColor = chartTextColor,
                    gridColor = chartGridColor
                )
            } else {
                ChartPlaceholder(
                    modifier = Modifier.fillMaxSize(),
                    text = if (uiState.isConfigPhase) "Esperando inicio de prueba..." else "No hay datos de SpO2"
                )
            }
        }

        // --- Sección FC (Frecuencia Cardíaca) ---
        ExecutionSectionCard(
            modifier = Modifier.weight(1f),
            title = "FC (lpm)",
            titleStyle = MaterialTheme.typography.titleLarge.copy(
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        ) {

            if (!uiState.isConfigPhase && uiState.heartRateDataPoints.isNotEmpty()) {
                LineChartComposable(
                    modifier = Modifier.fillMaxSize(),
                    dataPoints = uiState.heartRateDataPoints,
                    yAxisMin = 60f,
                    yAxisMax = 160f,
                    yAxisLabelCount = 8,
                    xAxisLabelCount = 7,
                    lineColor = chartLineColorHr,
                    textColor = chartTextColor,
                    gridColor = chartGridColor
                )
            } else {
                ChartPlaceholder(
                    modifier = Modifier.fillMaxSize(),
                    text = if (uiState.isConfigPhase) "Esperando inicio de prueba..." else "No hay datos de FC"
                )
            }
        }
    }
}

@Composable
fun ChartPlaceholder(modifier: Modifier, text: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 17.sp
        )
    }
}


@Composable
fun CentralSection(
    modifier: Modifier = Modifier,
    uiState: TestExecutionUiState,
    viewModel: TestExecutionViewModel
) {
    val focusManager = LocalFocusManager.current

    // Usar los flags del UiState directamente para la lógica de habilitación
    val isConfigPhase = uiState.isConfigPhase
    val isDuringTest = uiState.isTestRunning
    val isAfterTest = uiState.isTestFinished

    var trackLengthText by remember { mutableStateOf(uiState.trackLength.toString()) }
    LaunchedEffect(uiState.trackLength) {
        if (uiState.trackLength.toString() != trackLengthText) {
            trackLengthText = uiState.trackLength.toString()
        }
    }

    Column(
        modifier = modifier.padding(horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Text(
            "TIEMPO",
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 20.sp
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(55.dp)
                .background(ElementBackgroundColor, RoundedCornerShape(12.dp))
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp, vertical = 5.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = uiState.currentTimeFormatted,
                fontSize = 38.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        // --- BOTÓN PRINCIPAL (START / RESTART / REINICIAR) ---
        Button(
            onClick = {
                viewModel.onMainButtonClicked()
                focusManager.clearFocus()
            },
            enabled = true,
            modifier = Modifier
                .fillMaxWidth()
                .height(55.dp),
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = ButtonActionColor, // Siempre el mismo color de acción
                contentColor = Color.White
            )
        ) {
            val icon = when (uiState.mainButtonAction) {
                MainButtonAction.START -> Icons.Filled.PlayArrow
                MainButtonAction.RESTART_DURING_TEST -> Icons.Filled.Replay // O un icono específico de "restart"
                MainButtonAction.REINITIALIZE_AFTER_TEST -> Icons.Filled.Replay
            }
            val text = when (uiState.mainButtonAction) {
                MainButtonAction.START -> "START"
                MainButtonAction.RESTART_DURING_TEST -> "RESTART"
                MainButtonAction.REINITIALIZE_AFTER_TEST -> "REINICIAR"
            }

            Icon(
                imageVector = icon,
                contentDescription = text,
                modifier = Modifier.size(26.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = text,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        }

        // --- BOTÓN STOP ---
        Button(
            onClick = {
                viewModel.onStopTestInitiated()
                focusManager.clearFocus()
            },
            // Habilitado solo durante la prueba (isDuringTest)
            enabled = isDuringTest,
            modifier = Modifier
                .fillMaxWidth()
                .height(55.dp),
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError,
                disabledContainerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.4f),
                disabledContentColor = MaterialTheme.colorScheme.onError.copy(alpha = 0.6f)
            )
        ) {
            Icon(Icons.Filled.Stop, "Detener Prueba", modifier = Modifier.size(26.dp))
            Spacer(Modifier.width(8.dp))
            Text("STOP", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }

        // Input para Longitud de Pista (Track Length) - Sin botones +/-, solo TextField
        LabeledDisplay(
            label = "Metros pista",
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp), // Reduce el padding vertical
            borderColor = MaterialTheme.colorScheme.outline,
            labelStyle = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp),
            backgroundColor = ElementBackgroundColor
        ) {
            OutlinedTextField(
                value = trackLengthText,
                onValueChange = { newValue ->
                    if (newValue.all { it.isDigit() } && newValue.length <= 3) { // Permite hasta 3 dígitos
                        trackLengthText = newValue
                        viewModel.onTrackLengthChanged(newValue)
                    } else if (newValue.isEmpty()) {
                        trackLengthText = "" // Permite borrar
                        viewModel.onTrackLengthChanged("") // Notifica al VM
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = isConfigPhase, // Solo editable en fase de configuración
                textStyle = LocalTextStyle.current.copy(
                    fontSize = 20.sp,
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Bold,
                    color = if (isConfigPhase) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(
                        alpha = 0.7f
                    )
                ),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = {
                    viewModel.onTrackLengthChanged(trackLengthText)
                    focusManager.clearFocus(true)
                }),
                singleLine = true,
                shape = RoundedCornerShape(8.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color.Transparent, // MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = Color.Transparent,
                    disabledBorderColor = Color.Transparent,
                    cursorColor = MaterialTheme.colorScheme.primary,
                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                    disabledTextColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent,
                )
            )
        }

        LabeledDisplayWithInput(
            label = "VUELTAS",
            valueText = uiState.lapsInputText,
            onValueChange = { viewModel.onLapsInputChanged(it) }, // Permitir edición de texto
            onIncrement = { viewModel.onIncrementLaps(); focusManager.clearFocus(true) },
            onDecrement = { viewModel.onDecrementLaps(); focusManager.clearFocus(true) },
            // Habilitado durante la prueba Y después para ajustes finales
            incrementEnabled = isDuringTest || isAfterTest,
            decrementEnabled = (isDuringTest || isAfterTest) && (uiState.lapsInputText.toFloatOrNull() ?: 0f) >= 1.0f,
            textFieldEnabled = isDuringTest || isAfterTest, // TextField editable durante y después
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = {
                // Al finalizar la edición, asegurar que el VM procese el valor
                val currentLapsFloat = uiState.lapsInputText.toFloatOrNull() ?: uiState.laps
                viewModel.onLapsInputChanged(viewModel.formatLapsDisplay(currentLapsFloat)) // Formatear y actualizar
                focusManager.clearFocus(true)
            }),
            borderColor = MaterialTheme.colorScheme.outline,
            activeColor = MaterialTheme.colorScheme.onSurface
        )

        LabeledDisplay(
            label = "DISTANCIA",
            borderColor = MaterialTheme.colorScheme.outline,
            backgroundColor = ElementBackgroundColor.copy(alpha = 0.7f) // Ligeramente diferente para indicar que es solo display
        ) {
            Text(
                text = String.format(Locale.US, "%.0f metros", uiState.distanceMeters),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        // --- SpO2 y FC ---
        LiveValueWithIndicator(
            label = "SpO2",
            value = "${uiState.currentSpo2?.takeIf { it > 0 } ?: "--"} %",
            trend = uiState.spo2Trend,
            statusColor = uiState.spo2StatusColor.toActualColor(),
            valueFontSize = 20.sp,
            borderColor = MaterialTheme.colorScheme.outline,
            backgroundColor = ElementBackgroundColor.copy(alpha = 0.7f)
        )

        LiveValueWithIndicator(
            label = "FC",
            value = "${uiState.currentHeartRate?.takeIf { it > 0 } ?: "--"} lpm",
            trend = uiState.heartRateTrend,
            statusColor = uiState.heartRateStatusColor.toActualColor(),
            valueFontSize = 20.sp,
            borderColor = MaterialTheme.colorScheme.outline,
            backgroundColor = ElementBackgroundColor.copy(alpha = 0.7f)
        )
    }
}

@Composable
fun RightSection(
    modifier: Modifier = Modifier,
    uiState: TestExecutionUiState,
    viewModel: TestExecutionViewModel
) {
    // Lógica de habilitación
    val isDuringTest = uiState.isTestRunning && !uiState.isTestFinished
    val isAfterTest = uiState.isTestFinished && !uiState.isTestRunning
    // Lógica para habilitar borrado de parada (puede ser durante o después de la prueba)
    val canAddStop = isDuringTest
    val canDeleteStop = uiState.stopsCount > 0 && (isDuringTest || isAfterTest)

    Column(modifier = modifier) {
        Column(modifier = Modifier.weight(1f, fill = false)) {
            ExecutionSectionCard(
                titleStyle = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Start),
                backgroundColor = ElementBackgroundColor,
                borderColor = MaterialTheme.colorScheme.outline
            ) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "PARADAS: ${uiState.stopsCount}",
                            style = MaterialTheme.typography.titleMedium.copy(fontSize = 20.sp, fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = { viewModel.onAddStop() },
                                enabled = canAddStop,
                                modifier = Modifier.size(38.dp)
                            ) {
                                Icon(
                                    Icons.Filled.Add, "Añadir Parada",
                                    tint = if (canAddStop) ButtonActionColor else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                )
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            IconButton(
                                onClick = { viewModel.requestDeleteLastStopConfirmation() },
                                enabled = canDeleteStop,
                                modifier = Modifier.size(38.dp)
                            ) {
                                Icon(
                                    Icons.Filled.Delete, "Eliminar Última Parada",
                                    tint = if (canDeleteStop) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                "Registro de Paradas:",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 18.sp),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
            StopsTable(
                stops = uiState.stopRecords,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 220.dp),
                // Lógica del placeholder basada en el estado del ViewModel
                showPlaceholder = uiState.stopRecords.isEmpty(),
                placeholderText = if (uiState.isConfigPhase) "Las paradas se registrarán al iniciar la prueba."
                else if (uiState.stopRecords.isEmpty()) "No hay paradas registradas."
                else ""
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        LabeledDisplay(label = "SpO2 MÍNIMO", borderColor = MaterialTheme.colorScheme.outline, backgroundColor = ElementBackgroundColor.copy(alpha = 0.7f)) {
            Text(
                text = uiState.minSpo2Record?.value?.let { "$it %" } ?: "---",
                fontSize = 20.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        LabeledDisplay(label = "FC MÍNIMA", borderColor = MaterialTheme.colorScheme.outline, backgroundColor = ElementBackgroundColor.copy(alpha = 0.7f)) {
            Text(
                text = uiState.minHeartRateRecord?.value?.let { "$it lpm" } ?: "---",
                fontSize = 20.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        LabeledDisplay(label = "FC MÁXIMA", borderColor = MaterialTheme.colorScheme.outline, backgroundColor = ElementBackgroundColor.copy(alpha = 0.7f)) {
            Text(
                text = uiState.maxHeartRateRecord?.value?.let { "$it lpm" } ?: "---",
                fontSize = 20.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        BluetoothStatusIndicatorButton(
            status = uiState.bluetoothIconStatus,
            message = uiState.bluetoothStatusMessage,
            isAttemptingReconnect = uiState.isAttemptingForceReconnect,
            onClick = { viewModel.onBluetoothIconClicked() },
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .align(Alignment.CenterHorizontally)
        )
    }
}

// --- Componentes Reutilizables ---
@Composable
fun ExecutionSectionCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    titleStyle: TextStyle = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp, fontWeight = FontWeight.SemiBold),
    backgroundColor: Color = ElementBackgroundColor,
    borderColor: Color = MaterialTheme.colorScheme.outline,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        border = BorderStroke(1.dp, borderColor)
    ) {
        Column {
            if (title != null) {
                Text(
                    title,
                    style = titleStyle,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(backgroundColor.copy(alpha = 0.3f))
                        .padding(vertical = 5.dp, horizontal = 8.dp),
                    color = MaterialTheme.colorScheme.onSurface
                )
                HorizontalDivider(color = borderColor)
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
                    .padding(bottom = 4.dp),
            ){
                content()
            }
        }
    }
}

@Composable
fun LineChartComposable(
    modifier: Modifier,
    dataPoints: List<DataPoint>,
    yAxisMin: Float,
    yAxisMax: Float,
    yAxisLabelCount: Int,
    xAxisLabelCount: Int = 7,
    lineColor: Int,
    textColor: Int,
    gridColor: Int
) {
    AndroidView(
        factory = {
            LineChart(it).apply {
                description.isEnabled = false
                setDrawGridBackground(false)
                setTouchEnabled(true)
                isDragEnabled = true
                setScaleEnabled(true)
                setPinchZoom(true)
                isAutoScaleMinMaxEnabled = false

                xAxis.apply {
                    position = XAxis.XAxisPosition.BOTTOM
                    setDrawGridLines(true)
                    this.textColor = textColor
                    this.gridColor = gridColor
                    axisMinimum = 0f
                    axisMaximum = TEST_DURATION_MILLIS.toFloat()
                    setLabelCount(xAxisLabelCount, true)
                    valueFormatter = object : ValueFormatter() {
                        override fun getAxisLabel(value: Float, axis: com.github.mikephil.charting.components.AxisBase?): String {
                            val minutes = TimeUnit.MILLISECONDS.toMinutes(value.toLong())
                            val seconds = TimeUnit.MILLISECONDS.toSeconds(value.toLong()) % 60
                            return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
                        }
                    }
                }

                axisLeft.apply {
                    setDrawGridLines(true)
                    this.textColor = textColor
                    this.gridColor = gridColor
                    axisMinimum = yAxisMin
                    axisMaximum = yAxisMax
                    setLabelCount(yAxisLabelCount, true)
                }
                axisRight.isEnabled = false
                legend.isEnabled = false
            }
        },
        update = { chart ->
            val entries = dataPoints.map { Entry(it.timeMillis.toFloat(), it.value) }
            val dataSet = LineDataSet(entries, "Data").apply {
                this.color = lineColor
                setCircleColor(lineColor)
                circleRadius = 2.0f
                setDrawCircleHole(false)
                lineWidth = 1.8f
                setDrawValues(false)
                mode = LineDataSet.Mode.LINEAR
            }
            chart.data = LineData(dataSet)
            chart.setVisibleXRangeMaximum(TEST_DURATION_MILLIS.toFloat())
            chart.notifyDataSetChanged()
            chart.invalidate()
        },
        modifier = Modifier
            .fillMaxSize()
            .heightIn(min = 180.dp)
    )
}

@Composable
fun LabeledDisplay(
    label: String,
    labelStyle: TextStyle = MaterialTheme.typography.titleSmall.copy(fontSize = 13.sp, fontWeight = FontWeight.Normal),
    borderColor: Color = MaterialTheme.colorScheme.outline,
    backgroundColor: Color = ElementBackgroundColor,
    contentPadding: PaddingValues = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
    content: @Composable () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            label.uppercase(),
            style = labelStyle,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 17.sp,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(backgroundColor, RoundedCornerShape(10.dp))
                .border(1.dp, borderColor, RoundedCornerShape(10.dp))
                .padding(contentPadding),
            contentAlignment = Alignment.Center
        ) {
            content()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LabeledDisplayWithInput(
    label: String,
    valueText: String,
    onValueChange: (String) -> Unit,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
    incrementEnabled: Boolean,
    decrementEnabled: Boolean,
    keyboardOptions: KeyboardOptions,
    keyboardActions: KeyboardActions,
    modifier: Modifier = Modifier,
    textFieldEnabled: Boolean, // Este es clave para habilitar/deshabilitar el TextField
    borderColor: Color = MaterialTheme.colorScheme.outline,
    activeColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            label.uppercase(),
            style = MaterialTheme.typography.titleSmall.copy(fontSize = 17.sp, fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .background(ElementBackgroundColor, RoundedCornerShape(10.dp))
                .border(1.dp, borderColor, RoundedCornerShape(10.dp))
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = onDecrement, enabled = decrementEnabled, modifier = Modifier.size(40.dp)) {
                Icon(
                    Icons.Filled.Remove, "Decrementar",
                    tint = if (decrementEnabled) ButtonActionColor else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                )
            }
            OutlinedTextField(
                value = valueText,
                onValueChange = onValueChange,
                modifier = modifier
                    .weight(1f)
                    .padding(horizontal = 0.dp),
                enabled = textFieldEnabled, // Usar el parámetro textFieldEnabled
                textStyle = LocalTextStyle.current.copy(
                    fontSize = 20.sp,
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.SemiBold,
                    color = if (textFieldEnabled) activeColor else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                ),
                keyboardOptions = keyboardOptions,
                keyboardActions = keyboardActions,
                singleLine = true,
                shape = RoundedCornerShape(8.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color.Transparent, // Sin borde propio, el Row lo gestiona
                    unfocusedBorderColor = Color.Transparent,
                    disabledBorderColor = Color.Transparent,
                    cursorColor = activeColor, // Usa el activeColor pasado
                    focusedTextColor = activeColor,
                    unfocusedTextColor = if (textFieldEnabled) activeColor else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    disabledTextColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    // Colores de contenedor transparentes para que se vea el del Row
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent,
                    errorContainerColor = Color.Transparent
                )
            )
            IconButton(onClick = onIncrement, enabled = incrementEnabled, modifier = Modifier.size(40.dp)) {
                Icon(
                    Icons.Filled.Add, "Incrementar",
                    tint = if (incrementEnabled) ButtonActionColor else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                )
            }
        }
    }
}

@Composable
fun LiveValueWithIndicator(
    label: String,
    value: String,
    trend: Trend,
    statusColor: Color, // Ya es Color, no StatusColor
    valueFontSize: TextUnit = 22.sp,
    iconSize: Dp = 28.dp,
    borderColor: Color = MaterialTheme.colorScheme.outline,
    backgroundColor: Color = ElementBackgroundColor // Usar directamente ElementBackgroundColor si es el deseado
) {
    LabeledDisplay( // Reutilizamos LabeledDisplay para la estructura base del título
        label = label,
        borderColor = borderColor,
        backgroundColor = backgroundColor, // El fondo del LabeledDisplay actuará como fondo general
        contentPadding = PaddingValues(vertical = 8.dp, horizontal = 12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceAround // O SpaceBetween si se prefiere
        ) {
            Text(
                value,
                fontSize = valueFontSize,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface, // Color del texto del valor
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f) // Darle peso para que ocupe el espacio disponible
            )
            TrendArrow(trend, iconSize) // El tamaño del icono se pasa aquí
            StatusLight(statusColor, iconSize) // El tamaño del icono se pasa aquí
        }
    }
}

@Composable
fun TrendArrow(trend: Trend, iconSize: Dp = 28.dp) {
    val icon = when (trend) {
        Trend.UP -> Icons.Filled.ArrowUpward
        Trend.DOWN -> Icons.Filled.ArrowDownward
        else -> null // Para Trend.STABLE o UNKNOWN
    }
    Box(
        modifier = Modifier.size(iconSize), // El tamaño del Box se ajusta al iconSize
        contentAlignment = Alignment.Center
    ) {
        if (icon != null) {
            Icon(
                icon,
                contentDescription = "Tendencia ${trend.name.lowercase(Locale.getDefault())}",
                tint = MaterialTheme.colorScheme.onSurfaceVariant, // Color del icono de tendencia
                modifier = Modifier.size(iconSize * 0.70f) // El icono en sí un poco más pequeño que el Box
            )
        } else {
            // Mostrar un guion si la tendencia es estable o desconocida
            Text(
                "—", // Guion largo o corto
                fontSize = (iconSize.value * 0.60f).sp, // Tamaño del texto relativo al iconSize
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun StatusLight(color: Color, size: Dp = 28.dp) { // color ya es Color
    Box(
        modifier = Modifier
            .size(size)
            .padding(size * 0.2f)
            .clip(CircleShape)
            .background(color)
    )
}

@Composable
fun StopsTable(
    stops: List<StopRecord>,
    modifier: Modifier = Modifier,
    showPlaceholder: Boolean = false,
    placeholderText: String = "No hay paradas registradas"
) {
    val tableHeaderStyle = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
    val tableCellStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val cellPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp)

    Column(
        modifier = modifier
            .background(ElementBackgroundColor.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
            .padding(6.dp)
    ) {
        // Encabezado de la tabla
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    ElementBackgroundColor, // Fondo sólido para el encabezado
                    RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp)
                )
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Nº", modifier = Modifier
                .weight(0.12f)
                .padding(cellPadding), style = tableHeaderStyle, fontSize = 17.sp, textAlign = TextAlign.Center)
            Text("Tiempo", modifier = Modifier
                .weight(0.30f)
                .padding(cellPadding), style = tableHeaderStyle, fontSize = 17.sp, textAlign = TextAlign.Center)
            Text("SpO2", modifier = Modifier
                .weight(0.20f)
                .padding(cellPadding), style = tableHeaderStyle, fontSize = 17.sp, textAlign = TextAlign.Center)
            Text("FC", modifier = Modifier
                .weight(0.25f) // Ajustar pesos
                .padding(cellPadding), style = tableHeaderStyle, fontSize = 17.sp, textAlign = TextAlign.Center)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.7f))

        if (showPlaceholder || stops.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    // Usar placeholderText
                    text = if (showPlaceholder) placeholderText else "No hay paradas registradas",
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 17.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                )
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                itemsIndexed(stops) { index, stop ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                if (index % 2 == 0) Color.Transparent else ElementBackgroundColor.copy(
                                    alpha = 0.2f
                                )
                            )
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text((index + 1).toString(), modifier = Modifier
                            .weight(0.12f)
                            .padding(cellPadding), style = tableCellStyle, fontSize = 17.sp, textAlign = TextAlign.Center)
                        Text(stop.stopTimeFormatted, modifier = Modifier
                            .weight(0.30f)
                            .padding(cellPadding), style = tableCellStyle, fontSize = 17.sp, textAlign = TextAlign.Center)
                        Text("${stop.spo2AtStopTime}", modifier = Modifier
                            .weight(0.20f)
                            .padding(cellPadding), style = tableCellStyle, fontSize = 17.sp, textAlign = TextAlign.Center)
                        Text("${stop.heartRateAtStopTime}", modifier = Modifier
                            .weight(0.25f)
                            .padding(cellPadding), style = tableCellStyle, fontSize = 17.sp, textAlign = TextAlign.Center)
                    }
                    if (index < stops.size - 1) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                    }
                }
            }
        }
    }
}


// --- DIÁLOGOS (Comunes) ---
@Composable
fun ConfirmationDialog(
    title: String,
    text: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    confirmButtonText: String = "Salir",
    dismissButtonText: String = "Cancelar"
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = title, style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp)) },
        text = { Text(text = text, style = MaterialTheme.typography.bodyMedium, fontSize = 17.sp) },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm()
                },
                colors = ButtonDefaults.buttonColors(containerColor = ButtonActionColor)
            ) {
                Text(confirmButtonText, color = Color.White, fontSize = 17.sp)
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = ButtonActionColor),
                border = BorderStroke(1.dp, ButtonActionColor)
            ) {
                Text(dismissButtonText, fontSize = 17.sp)
            }
        },
        shape = RoundedCornerShape(16.dp),
        containerColor = BackgroundColor,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurface
    )
}

@Composable
fun InfoDialog(
    title: String,
    text: String,
    onDismiss: () -> Unit,
    buttonText: String = "Entendido"
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = title, style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp)) },
        text = { Text(text = text, style = MaterialTheme.typography.bodyMedium, fontSize = 17.sp) },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = ButtonActionColor)
            ) {
                Text(buttonText, color = Color.White, fontSize = 17.sp)
            }
        },
        shape = RoundedCornerShape(16.dp),
        containerColor = BackgroundColor,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurface
    )
}

@Composable
fun CountdownDialog(
    title: String,
    countdownValue: Int,
    onCancel: () -> Unit
) {
    Dialog(onDismissRequest = { /* No se puede descartar arrastrando */ }) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = BackgroundColor),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "$countdownValue",
                    style = MaterialTheme.typography.displayLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp
                    ),
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "La prueba se detendrá automáticamente...",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    fontSize = 17.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error)
                ) {
                    Text("CANCELAR DETENCIÓN")
                }
            }
        }
    }
}

// --- INDICADOR/BOTÓN DE BLUETOOTH ---
@Composable
fun BluetoothStatusIndicatorButton(
    status: BluetoothIconStatus,
    message: String,
    isAttemptingReconnect: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val iconColor = status.toActualComposeColor()
    val infiniteTransition = rememberInfiniteTransition(label = "bt_reconnect_glow")

    val isSystemBluetoothActuallyOff = message.contains("active bluetooth", ignoreCase = true)
    val isRedAndCanReconnect = status == BluetoothIconStatus.RED && message.startsWith("Pérdida de conexión")
    val isActuallyClickable = !isSystemBluetoothActuallyOff && (isRedAndCanReconnect || isAttemptingReconnect || status == BluetoothIconStatus.CONNECTING)

    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = if (isAttemptingReconnect || status == BluetoothIconStatus.CONNECTING || isRedAndCanReconnect) 0.3f else 0f,
        targetValue = if (isAttemptingReconnect || status == BluetoothIconStatus.CONNECTING || isRedAndCanReconnect) 0.7f else 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "bt_glow_alpha"
    )

    // Animación de rotación para el estado CONNECTING o cuando isAttemptingReconnect es true
    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = if (isAttemptingReconnect || status == BluetoothIconStatus.CONNECTING) 360f else 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ), label = "bt_rotation_angle"
    )

    val animatedIconColor by animateColorAsState(
        targetValue = iconColor,
        animationSpec = tween(300), label = "bt_icon_color_animation"
    )

    // Modificador para la apariencia de "botón" cuando es rojo y reconectable
    val containerModifier = if (isRedAndCanReconnect && !isAttemptingReconnect) {
        modifier
            .clip(RoundedCornerShape(12.dp)) // Bordes más redondeados para el área del botón
            .background(
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.15f), // Fondo sutil
                RoundedCornerShape(12.dp)
            )
            .border(
                BorderStroke(1.5.dp, MaterialTheme.colorScheme.error), // Borde más pronunciado
                RoundedCornerShape(12.dp)
            )
    } else {
        modifier
    }

    Column(
        modifier = containerModifier
            .clickable(
                enabled = isActuallyClickable,
                onClick = onClick,
                indication = androidx.compose.material.ripple.rememberRipple(bounded = true),
                interactionSource = remember { MutableInteractionSource() }
            )
            .padding(vertical = 8.dp, horizontal = 12.dp), // Padding alrededor del conjunto icono + texto
        horizontalAlignment = Alignment.CenterHorizontally // Centra el icono y el texto dentro de la columna
    ) {
        Box(
            modifier = Modifier
                .size(42.dp) // Tamaño del área del icono
                .clip(CircleShape)
                .background(
                    animatedIconColor.copy(alpha = if (isAttemptingReconnect || status == BluetoothIconStatus.CONNECTING || isRedAndCanReconnect) glowAlpha else if (isSystemBluetoothActuallyOff) 0.5f else 1f)
                ) // Si BT está apagado, atenuar un poco el icono también
                .border(
                    1.dp,
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                    CircleShape
                ) // Borde sutil
                .padding(6.dp), // Padding interno para el icono en sí
            contentAlignment = Alignment.Center
        ) {
            val iconToShow = when {
                isAttemptingReconnect || status == BluetoothIconStatus.CONNECTING -> Icons.Filled.Autorenew // O Sync
                isRedAndCanReconnect -> Icons.Filled.Refresh // Para indicar "pulsar para reconectar"
                isSystemBluetoothActuallyOff -> Icons.Filled.BluetoothDisabled
                status == BluetoothIconStatus.RED -> Icons.Filled.ErrorOutline // Otro tipo de error
                status == BluetoothIconStatus.YELLOW -> Icons.Filled.WarningAmber
                status == BluetoothIconStatus.GREEN -> Icons.Filled.BluetoothConnected
                else -> Icons.Filled.Bluetooth // Default o GRAY
            }
            Icon(
                imageVector = iconToShow,
                contentDescription = "Estado Bluetooth: $message",
                tint = Color.White,
                modifier = Modifier
                    .fillMaxSize(0.85f)
                    .rotate(if (status == BluetoothIconStatus.CONNECTING || isAttemptingReconnect) rotationAngle else 0f)
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = if (isRedAndCanReconnect && !isAttemptingReconnect) MaterialTheme.colorScheme.error
                    else if (isSystemBluetoothActuallyOff) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            fontSize = 15.sp
        )
    }
}
