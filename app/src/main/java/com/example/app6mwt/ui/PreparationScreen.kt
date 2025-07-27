package com.example.app6mwt.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.bluetooth.BluetoothAdapter
import android.provider.Settings
import androidx.hilt.navigation.compose.hiltViewModel
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.BluetoothSearching
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.app6mwt.bluetooth.BleConnectionStatus
import com.example.app6mwt.ui.theme.*
import kotlinx.coroutines.flow.collectLatest
import com.example.app6mwt.bluetooth.UiScannedDevice as ServiceUiScannedDevice

data class SexOption(val value: String, val displayName: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PreparationScreen(
    patientIdFromNav: String,
    patientNameFromNav: String,
    patientHasHistoryFromNav: Boolean,
    onNavigateBack: () -> Unit,
    onNavigateToTestExecution: (preparationData: TestPreparationData) -> Unit,
    viewModel: PreparationViewModel = hiltViewModel()
) {
    val showDialog by viewModel.showNavigateBackDialog.collectAsStateWithLifecycle()
    val patientFullName by viewModel.patientFullName.collectAsStateWithLifecycle()
    val patientIdForDisplay by viewModel.patientId.collectAsStateWithLifecycle()

    val keyboardController = LocalSoftwareKeyboardController.current

    // Launcher para solicitar permisos de Android (BLUETOOTH_SCAN, BLUETOOTH_CONNECT, ACCESS_FINE_LOCATION)
    val permissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissionsResult ->
        Log.d("PreparationScreen", "Resultado de permisos de Android: $permissionsResult")
        viewModel.onPermissionsResult(permissionsResult)
    }

    // Launcher para solicitar la activación de Bluetooth
    val enableBluetoothLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        Log.d("PreparationScreen", "Resultado de activación de Bluetooth: ${result.resultCode}")
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.onBluetoothEnabledResult(true)
        } else {
            viewModel.onBluetoothEnabledResult(false)
        }
    }

    // Launcher para solicitar la activación de los servicios de ubicación
    val enableLocationServicesLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        // No hay un resultCode estándar para esto, así que simplemente notificamos al ViewModel para que vuelva a verificar.
        Log.d("PreparationScreen", "Resultado de activación de servicios de ubicación (se re-verificará el estado).")
        viewModel.onLocationServicesEnabledResult(viewModel.bluetoothService.isLocationEnabled()) // Re-chequear el estado
    }

    LaunchedEffect(patientIdFromNav, patientNameFromNav, patientHasHistoryFromNav) {
        Log.d("PreparationScreen", "LaunchedEffect para inicializar ViewModel con datos del paciente.")
        viewModel.initialize(patientIdFromNav, patientNameFromNav, patientHasHistoryFromNav)
    }

    LaunchedEffect(viewModel.navigateToEvent) {
        viewModel.navigateToEvent.collectLatest { event ->
            when (event) {
                is PreparationNavigationEvent.ToTestExecution -> {
                    keyboardController?.hide()
                    onNavigateToTestExecution(event.preparationData)
                }
            }
        }
    }

    LaunchedEffect(key1 = viewModel) {
        viewModel.navigateBackEvent.collectLatest {
            Log.d(
                "ScreenPrep",
                "Evento navigateBackEvent recibido desde ViewModel. Ejecutando onNavigateBack()."
            )
            onNavigateBack()
        }
    }

    // Observar el evento para solicitar permisos de Android
    LaunchedEffect(viewModel.requestPermissionsEvent) {
        viewModel.requestPermissionsEvent.collectLatest { permissionsToRequestArray ->
            Log.d("PreparationScreen", "Evento requestPermissionsEvent recibido. Solicitando: ${permissionsToRequestArray.joinToString()}")
            if (permissionsToRequestArray.isNotEmpty()) {
                permissionsLauncher.launch(permissionsToRequestArray)
            }
        }
    }

    // Observar el evento para solicitar la activación de Bluetooth
    LaunchedEffect(viewModel.requestEnableBluetoothEvent) {
        viewModel.requestEnableBluetoothEvent.collectLatest {
            Log.d("PreparationScreen", "Evento requestEnableBluetoothEvent recibido. Lanzando intent para activar Bluetooth.")
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            enableBluetoothLauncher.launch(enableBtIntent)
        }
    }

    // Observar el evento para solicitar la activación de los servicios de ubicación
    LaunchedEffect(viewModel.requestLocationServicesEvent) {
        viewModel.requestLocationServicesEvent.collectLatest {
            Log.d("PreparationScreen", "Evento requestLocationServicesEvent recibido. Lanzando intent para configuración de ubicación.")
            val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            enableLocationServicesLauncher.launch(intent)
        }
    }

    val currentViewModel by rememberUpdatedState(viewModel)
    BackHandler(enabled = true) {
        Log.d(
            "ScreenPrep",
            "Botón de atrás del sistema presionado. Llamando a currentViewModel.requestNavigateBack()."
        )
        currentViewModel.requestNavigateBack()
    }

    Scaffold(
        topBar = {
            PreparationTopAppBar(
                patientName = patientFullName.takeIf { it.isNotBlank() } ?: patientNameFromNav,
                patientId = patientIdForDisplay ?: patientIdFromNav,
                onNavigateBackClicked = {
                    Log.d("ScreenPrep", "Flecha de TopAppBar presionada. Llamando a viewModel.requestNavigateBack().")
                    keyboardController?.hide()
                    viewModel.requestNavigateBack()
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        PreparationScreenContent(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
            viewModel = viewModel,
            onRequestPermissionsOrEnableFeatures = {
                Log.d("PreparationScreen", "onRequestPermissions llamado desde la UI. Llamando a viewModel.startBleScan() que gestionará permisos si es necesario.")
                viewModel.startBleProcessOrRequestPermissions()
            }
        )
    }

    if (showDialog) {
        ConfirmationDialog(
            title = "Confirmar Salida",
            text = "¿Está seguro de que quiere volver? Se perderán los datos no guardados de esta preparación.",
            onConfirm = { viewModel.confirmNavigateBack() },
            onDismiss = { viewModel.cancelNavigateBack() },
            confirmButtonText = "Salir",
            dismissButtonText = "Cancelar"
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PreparationTopAppBar(
    patientName: String,
    patientId: String,
    onNavigateBackClicked: () -> Unit
) {
    TopAppBar(
        title = {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(
                    text = "PREPARACIÓN PRUEBA 6MWT - ${patientName.uppercase()}",
                    fontSize = 25.sp, fontWeight = FontWeight.Bold, color = TextOnSecondary,
                    textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 58.dp)
                )
            }
        },
        navigationIcon = {
            IconButton(onClick = onNavigateBackClicked) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Regresar", tint = TextOnSecondary)
            }
        },
        actions = {
            Text(
                text = "ID: $patientId", fontSize = 25.sp, fontWeight = FontWeight.Bold,
                color = TextOnSecondary, textAlign = TextAlign.End,
                modifier = Modifier
                    .padding(end = 16.dp)
                    .align(Alignment.CenterVertically)
            )
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.primary, titleContentColor = TextOnSecondary,
            navigationIconContentColor = TextOnSecondary, actionIconContentColor = TextOnSecondary
        )
    )
}

@Composable
fun PreparationScreenContent(
    modifier: Modifier = Modifier,
    viewModel: PreparationViewModel,
    onRequestPermissionsOrEnableFeatures: () -> Unit
) {
    Row(modifier = modifier
        .padding(16.dp)
        .fillMaxSize()
        ) {
        Column(modifier = Modifier
            .weight(0.38f)
            .padding(end = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            SectionContainer { PatientDataSection(viewModel) }
            FormDivider()
            SectionContainer { DevicePlacementSection(viewModel) }
            Spacer(modifier = Modifier.weight(1f))
        }
        VerticalDivider(modifier = Modifier
            .fillMaxHeight()
            .padding(horizontal = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
        Column(modifier = Modifier
            .weight(0.28f)
            .padding(horizontal = 8.dp)) {
            SectionContainer { BasalValuesSection(viewModel) }
            Spacer(modifier = Modifier.weight(1f))
        }
        VerticalDivider(modifier = Modifier
            .fillMaxHeight()
            .padding(horizontal = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
        Column(modifier = Modifier
            .weight(0.34f)
            .padding(start = 8.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionContainer { ActionsTopSection(viewModel) }
                SectionContainer { BluetoothConnectionSection(viewModel = viewModel, onRequestPermissionsOrEnableFeatures = onRequestPermissionsOrEnableFeatures) }
            }
            Spacer(modifier = Modifier.weight(1f))
        }
    }
}

@Composable
fun SectionContainer(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier = modifier
        .fillMaxWidth()
        .background(ElementBackgroundColor, RoundedCornerShape(12.dp))
        .padding(5.dp), content = content)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PatientDataSection(viewModel: PreparationViewModel) {
    val patientSex by viewModel.patientSex.collectAsStateWithLifecycle()
    val patientAge by viewModel.patientAge.collectAsStateWithLifecycle()
    val patientHeightCm by viewModel.patientHeightCm.collectAsStateWithLifecycle()
    val patientWeightKg by viewModel.patientWeightKg.collectAsStateWithLifecycle()
    val theoreticalDistance by viewModel.theoreticalDistance
    val usesInhalers by viewModel.usesInhalers.collectAsStateWithLifecycle()
    val usesOxygen by viewModel.usesOxygen.collectAsStateWithLifecycle()

    val sexOptions = remember {
        listOf(
            SexOption("M", "M"),
            SexOption("F", "F")
        )
    }
    var sexExpanded by remember { mutableStateOf(false) }
    val selectedSexDisplay = patientSex

    Column {
        Text("Datos del paciente", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, fontSize = 20.sp, modifier = Modifier
            .padding(bottom = 10.dp)
            .align(Alignment.CenterHorizontally))
        DataRow("Sexo y edad:") {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 4.dp)
                ) {
                    ExposedDropdownMenuBox(
                        expanded = sexExpanded,
                        onExpandedChange = { sexExpanded = !sexExpanded },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField( // Este actúa como el "anchor" del menú
                            value = selectedSexDisplay, // Muestra "M", "F", o ""
                            onValueChange = { /* No editable directamente por teclado */ },
                            readOnly = true, // El usuario selecciona del menú
                            label = { Text("Sexo", fontSize = 12.sp) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = sexExpanded) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surface,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                                disabledContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.3f),
                                focusedBorderColor = DarkerBlueHighlight,
                                unfocusedBorderColor = LightBluePrimary.copy(alpha = 0.6f),
                                disabledBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                                cursorColor = DarkerBlueHighlight
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .menuAnchor()
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = 32.dp),
                            textStyle = LocalTextStyle.current.copy(fontSize = 15.sp, textAlign = TextAlign.Center)
                        )
                        ExposedDropdownMenu(
                            expanded = sexExpanded,
                            onDismissRequest = { sexExpanded = false }
                        ) {
                            if (patientSex.isNotBlank()) {
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            "-- Limpiar selección --",
                                            fontSize = 15.sp,
                                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                                        )
                                    },
                                    onClick = {
                                        viewModel.onPatientSexChange("") // Llama al ViewModel con valor vacío
                                        sexExpanded = false
                                    }
                                )
                                HorizontalDivider() // Separador opcional
                            }
                            sexOptions.forEach { selectionOption ->
                                DropdownMenuItem(
                                    text = {
                                        // Centrar el texto "M" o "F" en el DropdownMenuItem
                                        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                            Text(selectionOption.displayName, fontSize = 17.sp) // displayName ahora es "M" o "F"
                                        }
                                    },
                                    onClick = {
                                        viewModel.onPatientSexChange(selectionOption.value) // Envía "M" o "F"
                                        sexExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            CustomTextField(patientAge, viewModel::onPatientAgeChange, "Edad", Modifier
                .weight(1f)
                .padding(start = 4.dp), KeyboardOptions(keyboardType = KeyboardType.Number))
        }
        FormDivider()
        DataRow("Altura y peso:") {
            CustomTextField(patientHeightCm, viewModel::onPatientHeightChange, "Altura (cm)", Modifier
                .weight(1f)
                .padding(end = 4.dp), KeyboardOptions(keyboardType = KeyboardType.Number))
            CustomTextField(patientWeightKg, viewModel::onPatientWeightChange, "Peso (kg)", Modifier
                .weight(1f)
                .padding(start = 4.dp), KeyboardOptions(keyboardType = KeyboardType.Number))
        }
        FormDivider()
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) {
            Text("Distancia teórica:", fontWeight = FontWeight.Medium, fontSize = 17.sp, modifier = Modifier.weight(1f))
            Text(if (theoreticalDistance > 0.0) "%.1f metros".format(theoreticalDistance) else "0 metros", fontWeight = FontWeight.SemiBold, fontSize = 17.sp, color = TextOnPrimary)
        }
        FormDivider()
        Text("Medicación adicional:", fontSize = 17.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 4.dp, bottom = 4.dp))
        CheckboxRow("Usa inhaladores", usesInhalers, viewModel::onUsesInhalersChange)
        CheckboxRow("Usa oxígeno domiciliario", usesOxygen, viewModel::onUsesOxygenChange)
    }
}

@Composable
fun DataRow(label: String, content: @Composable RowScope.() -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
        Text(label, fontSize = 17.sp, fontWeight = FontWeight.Medium, modifier = Modifier
            .weight(0.35f)
            .padding(end = 8.dp))
        Row(modifier = Modifier.weight(0.65f), content = content, verticalAlignment = Alignment.CenterVertically)
    }
}

@SuppressLint("MissingPermission")
@Composable
fun BluetoothConnectionSection(viewModel: PreparationViewModel, onRequestPermissionsOrEnableFeatures: () -> Unit) {
    val connectionStatus by viewModel.connectionStatus.collectAsStateWithLifecycle()
    val latestBleSpo2 by viewModel.latestBleSpo2.collectAsStateWithLifecycle()
    val latestBleHeartRate by viewModel.latestBleHeartRate.collectAsStateWithLifecycle()
    val isBleReady by viewModel.isBleReady.collectAsStateWithLifecycle()
    val isScanning by viewModel.isScanning.collectAsStateWithLifecycle()
    val scannedDevicesResult by viewModel.scannedDevices.collectAsStateWithLifecycle()
    val latestBleNoFinger by viewModel.latestBleNoFinger.collectAsStateWithLifecycle()
    val latestBleSignalStrength by viewModel.latestBleSignalStrength.collectAsStateWithLifecycle()
    val connectedDeviceName by viewModel.connectedDeviceName.collectAsStateWithLifecycle()
    val currentBluetoothMessageForUi by viewModel.uiBluetoothMessage.collectAsStateWithLifecycle()

    Column {
        Text("Conexión pulsioxímetro", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, fontSize = 20.sp, modifier = Modifier
            .padding(bottom = 8.dp)
            .align(Alignment.CenterHorizontally))

        // --- INICIO BOTÓN PRINCIPAL DE ACCIÓN BLUETOOTH ---
        val mainButtonEnabled: Boolean
        val mainButtonText: String
        val mainButtonIconImageVector: androidx.compose.ui.graphics.vector.ImageVector
        val mainButtonAction: () -> Unit
        var showProgressInMainButton = false

        // Lógica para determinar el estado del botón principal
        if (!isBleReady) {
            mainButtonEnabled = true
            mainButtonIconImageVector = Icons.Filled.BluetoothDisabled
            mainButtonAction = onRequestPermissionsOrEnableFeatures

            mainButtonText = currentBluetoothMessageForUi?.let { msg ->
                when {
                    msg.contains("Activar Bluetooth", ignoreCase = true) -> "Activar Bluetooth"
                    msg.contains("permisos de Bluetooth", ignoreCase = true) -> "Conceder Permisos BT"
                    msg.contains("permiso de Ubicación", ignoreCase = true) -> "Conceder Ubicación"
                    msg.contains("Servicios de Ubicación", ignoreCase = true) -> "Activar Ubicación"
                    else -> "Revisar Config."
                }
            } ?: "Configurar Bluetooth"

        } else {
            when (connectionStatus) {
                BleConnectionStatus.SCANNING -> {
                    mainButtonEnabled = true
                    mainButtonText = "Detener Escaneo"
                    mainButtonIconImageVector = Icons.Filled.SearchOff
                    mainButtonAction = { viewModel.stopBleScan() }
                }
                BleConnectionStatus.CONNECTING, BleConnectionStatus.RECONNECTING -> {
                    mainButtonEnabled = false
                    mainButtonText = if (connectionStatus == BleConnectionStatus.RECONNECTING) "Reconectando..." else "Conectando..."
                    mainButtonIconImageVector = Icons.AutoMirrored.Filled.BluetoothSearching
                    showProgressInMainButton = true
                    mainButtonAction = { /* No acción directa, está conectando */ }
                }
                BleConnectionStatus.CONNECTED, BleConnectionStatus.SUBSCRIBED -> {
                    mainButtonEnabled = true
                    mainButtonText = "Desconectar de ${connectedDeviceName ?: "Dispositivo"}"
                    mainButtonIconImageVector = Icons.Filled.BluetoothConnected
                    mainButtonAction = { viewModel.disconnectBluetooth() }
                }
                else -> {
                    mainButtonEnabled = true
                    mainButtonText = "Escanear Dispositivos"
                    mainButtonIconImageVector = Icons.Filled.Bluetooth
                    mainButtonAction = onRequestPermissionsOrEnableFeatures
                }
            }
        }

        Button(
            onClick = mainButtonAction,
            enabled = mainButtonEnabled,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .defaultMinSize(minHeight = 48.dp)
        ) {
            if (showProgressInMainButton) {
                // Muestra un CircularProgressIndicator cuando está conectando/reconectando
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp
                )
            } else {
                Icon(mainButtonIconImageVector, contentDescription = null, Modifier.size(20.dp))
            }
            Spacer(Modifier.width(8.dp))
            Text(mainButtonText, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }

        // Indicador de progreso lineal cuando está escaneando Y ble está listo
        if (isScanning && isBleReady) {
            LinearProgressIndicator(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp, bottom = 8.dp)
            )
        }

        Row(modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier
                .weight(1f)
                .padding(end = 8.dp)) {
                Text("Estado actual:", style = MaterialTheme.typography.labelMedium, fontSize = 17.sp)
                Spacer(modifier = Modifier.height(5.dp))

                val statusColor = when {
                    // Si uiBluetoothMessage contiene palabras clave de error o solicitud de activación
                    currentBluetoothMessageForUi?.contains("desactivado", ignoreCase = true) == true ||
                            currentBluetoothMessageForUi?.contains("permisos", ignoreCase = true) == true ||
                            currentBluetoothMessageForUi?.contains("ubicación", ignoreCase = true) == true ||
                            currentBluetoothMessageForUi?.contains("error", ignoreCase = true) == true || // Error genérico
                            connectionStatus.name.startsWith("ERROR_") -> MaterialTheme.colorScheme.error

                    connectionStatus == BleConnectionStatus.SUBSCRIBED || connectionStatus == BleConnectionStatus.CONNECTED -> SuccessGreenColor
                    connectionStatus == BleConnectionStatus.CONNECTING || connectionStatus == BleConnectionStatus.SCANNING || connectionStatus == BleConnectionStatus.RECONNECTING -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.onSurfaceVariant // Para IDLE, DISCONNECTED_BY_USER, etc.
                }
                Text(
                    currentBluetoothMessageForUi ?: "Presiona el botón para iniciar.", // Mensaje por defecto si es nulo
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 17.sp,
                    color = statusColor,
                    maxLines = 2, // Aumentado a 2 para mensajes más largos
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        AnimatedVisibility(
            visible = isBleReady &&
                    (scannedDevicesResult.isNotEmpty() || isScanning) && // Mostrar si hay dispositivos O está escaneando
                    !(connectionStatus == BleConnectionStatus.CONNECTING ||
                            connectionStatus == BleConnectionStatus.RECONNECTING ||
                            connectionStatus == BleConnectionStatus.CONNECTED ||
                            connectionStatus == BleConnectionStatus.SUBSCRIBED)
        ) {
            Column(Modifier.padding(top = 8.dp)) {
                Text(if (isScanning) "Buscando dispositivos..." else "Dispositivos encontrados:", style = MaterialTheme.typography.titleSmall, fontSize = 17.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(bottom = 8.dp))
                Box(Modifier
                    .fillMaxWidth()
                    .heightIn(max = 250.dp) // Considera ajustar esta altura si es necesario
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        RoundedCornerShape(8.dp)
                    )) {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        if (scannedDevicesResult.isEmpty() && !isScanning) {
                            Text("No se encontraron dispositivos cercanos.",
                                modifier = Modifier
                                    .padding(16.dp)
                                    .align(Alignment.CenterHorizontally),
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center
                            )
                        }
                        scannedDevicesResult.forEach { device ->
                            DeviceRowItem(uiDevice = device, onDeviceClick = {
                                if (connectionStatus != BleConnectionStatus.CONNECTING && connectionStatus != BleConnectionStatus.RECONNECTING) {
                                    viewModel.connectToScannedDevice(device)
                                }
                            })
                            HorizontalDivider()
                        }
                    }
                }
            }
        }

        AnimatedVisibility(visible = isBleReady && (connectionStatus == BleConnectionStatus.SUBSCRIBED || connectionStatus == BleConnectionStatus.CONNECTED)) {
            Column(Modifier.padding(top = 8.dp)) {
                Text(
                    if (connectionStatus == BleConnectionStatus.CONNECTED) "Conectado a ${connectedDeviceName ?: "dispositivo"}. Esperando datos..."
                    else "Lecturas de ${connectedDeviceName ?: "dispositivo"}:",
                    style = MaterialTheme.typography.titleSmall, fontSize = 17.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(bottom = 4.dp)
                )
                val noFingerDetected = latestBleNoFinger == true
                val signalLowOrNull = connectionStatus == BleConnectionStatus.SUBSCRIBED && (latestBleSignalStrength == null || latestBleSignalStrength == 0) // Ajusta el umbral de señal baja si es necesario

                if (noFingerDetected) {
                    Text("Sensor: NO DEDO", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.padding(bottom = 4.dp))
                } else if (signalLowOrNull) {
                    Text("Sensor: SEÑAL BAJA/NULA", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.padding(bottom = 4.dp))
                }
                RealTimeDataRow("SpO₂ (BLE):", latestBleSpo2?.toString() ?: "---", "%")
                RealTimeDataRow("FC (BLE):", latestBleHeartRate?.toString() ?: "---", "lpm")
            }
        }
    }
}

@SuppressLint("MissingPermission")
@Composable
private fun DeviceRowItem(uiDevice: ServiceUiScannedDevice, onDeviceClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onDeviceClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(Modifier
            .weight(1f)
            .padding(end = 8.dp)) {
            Text(uiDevice.deviceName ?: "Dispositivo desconocido", fontWeight = FontWeight.Medium, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(uiDevice.address, style = MaterialTheme.typography.bodySmall, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (uiDevice.rssi != null) {
            Text("RSSI: ${uiDevice.rssi}", style = MaterialTheme.typography.bodySmall, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(end = 8.dp))
        }
        Icon(Icons.Filled.Bluetooth, "Conectar", tint = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun RealTimeDataRow(label: String, value: String, unit: String) {
    Row(Modifier
        .fillMaxWidth()
        .padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(0.6f), fontSize = 17.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("$value $unit", Modifier.weight(0.4f), fontWeight = FontWeight.Medium, fontSize = 18.sp, color = TextOnPrimary, textAlign = TextAlign.End)
    }
}

@Composable
fun BasalValuesSection(viewModel: PreparationViewModel) {
    val spo2Input by viewModel.spo2Input.collectAsStateWithLifecycle()
    val isValidSpo2 by viewModel.isValidSpo2.collectAsStateWithLifecycle()
    val spo2Hint by viewModel.spo2RangeHint

    val heartRateInput by viewModel.heartRateInput.collectAsStateWithLifecycle()
    val isValidHeartRate by viewModel.isValidHeartRate.collectAsStateWithLifecycle()
    val hrHint by viewModel.hrRangeHint

    val bloodPressureInput by viewModel.bloodPressureInput.collectAsStateWithLifecycle()
    val isValidBloodPressure by viewModel.isValidBloodPressure.collectAsStateWithLifecycle()
    val bpHint by viewModel.bpRangeHint

    val respiratoryRateInput by viewModel.respiratoryRateInput.collectAsStateWithLifecycle()
    val isValidRespiratoryRate by viewModel.isValidRespiratoryRate.collectAsStateWithLifecycle()
    val rrHint by viewModel.rrRangeHint

    val dyspneaBorgInput by viewModel.dyspneaBorgInput.collectAsStateWithLifecycle()
    val isValidDyspneaBorg by viewModel.isValidDyspneaBorg.collectAsStateWithLifecycle()
    val legPainBorgInput by viewModel.legPainBorgInput.collectAsStateWithLifecycle()
    val isValidLegPainBorg by viewModel.isValidLegPainBorg.collectAsStateWithLifecycle()
    val borgHint by viewModel.borgRangeHint


    val basalValuesStatusMessage by viewModel.basalValuesStatusMessage.collectAsStateWithLifecycle()
    val areBasalsValid by viewModel.areBasalsValid.collectAsStateWithLifecycle()
    val connectionStatus by viewModel.connectionStatus.collectAsStateWithLifecycle()

    // Para saber si el botón de capturar debe estar habilitado no solo por conexión, sino también si hay datos válidos para capturar
    val latestBleSpo2 by viewModel.latestBleSpo2.collectAsStateWithLifecycle()
    val latestBleHeartRate by viewModel.latestBleHeartRate.collectAsStateWithLifecycle()
    val latestBleNoFinger by viewModel.latestBleNoFinger.collectAsStateWithLifecycle()

    Column {
        Text("Valores basales", style = MaterialTheme.typography.titleMedium,fontWeight = FontWeight.Bold, fontSize = 20.sp, modifier = Modifier
            .padding(bottom = 10.dp)
            .align(Alignment.CenterHorizontally))
        Button(
            onClick = { viewModel.captureBasalFromBle() },
            enabled = connectionStatus == BleConnectionStatus.SUBSCRIBED &&
                    latestBleSpo2 != null && latestBleSpo2!! > 0 &&
                    latestBleHeartRate != null && latestBleHeartRate!! > 0 &&
                    latestBleNoFinger != true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 5.dp),
            colors = ButtonDefaults.buttonColors(containerColor =  MaterialTheme.colorScheme.primary)
        ) {
            Icon(Icons.Filled.BluetoothConnected, "Capturar desde BLE", modifier = Modifier.padding(end = 5.dp))
            Text("Capturar SpO₂ y FC")
        }
        FormDivider()
        BasalValueRow("SpO₂ (%):", spo2Input, isValidSpo2, viewModel::onSpo2InputChange, spo2Hint, keyboardType = KeyboardType.Number)
        FormDivider()
        BasalValueRow("FC (lpm):", heartRateInput, isValidHeartRate, viewModel::onHeartRateInputChange, hrHint, keyboardType = KeyboardType.Number)
        FormDivider()
        BasalValueRow("TA (mmHg):", bloodPressureInput, isValidBloodPressure, viewModel::onBloodPressureInputChange, bpHint, KeyboardType.Text)
        FormDivider()
        BasalValueRow("FR (rpm):", respiratoryRateInput, isValidRespiratoryRate, viewModel::onRespiratoryRateInputChange, rrHint, keyboardType = KeyboardType.Number)
        FormDivider()
        BasalValueRow("Disnea Borg:", dyspneaBorgInput, isValidDyspneaBorg, viewModel::onDyspneaBorgInputChange, borgHint, keyboardType = KeyboardType.Number)
        FormDivider()
        BasalValueRow("Dolor MMII Borg:", legPainBorgInput, isValidLegPainBorg, viewModel::onLegPainBorgInputChange, borgHint, keyboardType = KeyboardType.Number)

        Spacer(Modifier.height(8.dp))

        if (basalValuesStatusMessage.isNotBlank()) {
            Text(
                basalValuesStatusMessage,
                color = if (areBasalsValid && (basalValuesStatusMessage.contains("válidos", ignoreCase = true) || basalValuesStatusMessage.contains("completos", ignoreCase = true) || basalValuesStatusMessage.contains("correctos", ignoreCase = true))) SuccessGreenColor else MaterialTheme.colorScheme.error,
                fontWeight = FontWeight.Medium, fontSize = 17.sp, textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp)
            )
        }
    }
}

@Composable
fun BasalValueRow(
    label: String,
    value: String,
    isValid: Boolean,
    onValueChange: (String) -> Unit,
    placeholder: String? = null,
    keyboardType: KeyboardType = KeyboardType.Number
) {
    val showError = !isValid && value.isNotBlank()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .padding(vertical = 3.dp)
            .fillMaxWidth()
    ) {
        Text(label,
            Modifier
                .weight(0.45f)
                .padding(end = 4.dp),
            fontSize = 18.sp,
            fontWeight = FontWeight.Normal
        )
        OutlinedTextField(
            value,
            onValueChange,
            Modifier.weight(0.55f),
            singleLine = true,
            isError = showError,
            placeholder = placeholder?.let { hintText ->
                {
                    // Envolver el Text del placeholder en un Box para centrarlo
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center // Centra el contenido del Box (el Text)
                    ) {
                        Text(
                            text = hintText, // El texto del placeholder/hint
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center // Asegura que el Text en sí mismo también intente centrar
                        )
                    }
                }
            },
            shape = RoundedCornerShape(8.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = if (showError) MaterialTheme.colorScheme.error else DarkerBlueHighlight,
                unfocusedBorderColor = if (showError) MaterialTheme.colorScheme.error.copy(alpha = 0.7f) else LightBluePrimary.copy(alpha = 0.7f),
                cursorColor = DarkerBlueHighlight,
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                errorBorderColor = MaterialTheme.colorScheme.error
            ),
            textStyle = LocalTextStyle.current.copy(
                fontSize = 15.sp,
                textAlign = TextAlign.Center
            ),
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType)
        )
    }
}

@Composable
fun ActionsTopSection(viewModel: PreparationViewModel) {
    val canStartTestEnabled by viewModel.canStartTest.collectAsStateWithLifecycle()

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Acciones de prueba", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, fontSize = 20.sp, modifier = Modifier.padding(bottom = 8.dp))
        Button(
            onClick = { viewModel.onStartTestClicked() },
            enabled = canStartTestEnabled,
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 80.dp)
                .padding(vertical = 8.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = ButtonActionColor, contentColor = TextOnSecondary,
                disabledContainerColor = ButtonActionColor.copy(alpha = 0.4f),
                disabledContentColor = TextOnSecondary.copy(alpha = 0.6f)
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("Comenzar prueba 6MWT", textAlign = TextAlign.Center, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun DevicePlacementSection(viewModel: PreparationViewModel) {
    val isDevicePlaced by viewModel.isDevicePlaced.collectAsState()
    val devicePlacementLocation by viewModel.devicePlacementLocation.collectAsState()

    Column {
        Text("Dispositivo", style = MaterialTheme.typography.titleMedium, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier
            .padding(bottom = 8.dp)
            .align(Alignment.CenterHorizontally))
        DevicePlacedSwitch(isDevicePlaced, { viewModel.onDevicePlacedToggle(it)})
        AnimatedVisibility(visible = isDevicePlaced) {
            Column {
                Text("Ubicación:", fontSize = 17.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 10.dp, bottom = 4.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PlacementCheckbox("Dedo", devicePlacementLocation == DevicePlacementLocation.FINGER, { viewModel.onDevicePlacementLocationSelected(DevicePlacementLocation.FINGER) }, Modifier.weight(1f))
                    PlacementCheckbox("Oreja", devicePlacementLocation == DevicePlacementLocation.EAR, { viewModel.onDevicePlacementLocationSelected(DevicePlacementLocation.EAR) }, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
fun DevicePlacedSwitch(isPlaced: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier
        .fillMaxWidth()
        .padding(vertical = 1.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text("¿Dispositivo colocado?", fontSize = 17.sp, fontWeight = FontWeight.Medium, modifier = Modifier
            .weight(1f)
            .padding(end = 8.dp))
        Switch(
            checked = isPlaced, onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.primary,
                checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
            thumbContent = if (isPlaced) { { Icon(Icons.Filled.Check, "Colocado", Modifier.size(SwitchDefaults.IconSize), tint = MaterialTheme.colorScheme.onPrimary) } } else null
        )
    }
}

@Composable
fun PlacementCheckbox(text: String, isSelected: Boolean, onSelected: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier
        .clickable(onClick = onSelected)
        .padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(
            checked = isSelected, onCheckedChange = { onSelected() },
            colors = CheckboxDefaults.colors(
                checkedColor = MaterialTheme.colorScheme.primary,
                uncheckedColor = MaterialTheme.colorScheme.onSurfaceVariant,
                checkmarkColor = MaterialTheme.colorScheme.onPrimary
            )
        )
        Text(text, fontSize = 17.sp, modifier = Modifier.padding(start = 6.dp))
    }
}

@Composable
fun CustomTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    readOnly: Boolean = false,
    singleLine: Boolean = true,
    isError: Boolean = false
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, fontSize = 12.sp) },
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 52.dp),
        keyboardOptions = keyboardOptions,
        readOnly = readOnly,
        singleLine = singleLine,
        isError = isError,
        shape = RoundedCornerShape(8.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
            disabledContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.3f),
            focusedBorderColor = if (isError) MaterialTheme.colorScheme.error else DarkerBlueHighlight,
            unfocusedBorderColor = if (isError) MaterialTheme.colorScheme.error.copy(alpha = 0.7f) else LightBluePrimary.copy(alpha = 0.6f),
            disabledBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
            cursorColor = DarkerBlueHighlight, errorBorderColor = MaterialTheme.colorScheme.error,
        ),
        textStyle = LocalTextStyle.current.copy(
            fontSize = 17.sp,
            textAlign = TextAlign.Center
        )

    )
}

@Composable
fun CheckboxRow(text: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier
        .fillMaxWidth()
        .clickable { onCheckedChange(!checked) }
        .padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(
            checked = checked, onCheckedChange = onCheckedChange,
            colors = CheckboxDefaults.colors(
                checkedColor = MaterialTheme.colorScheme.primary,
                uncheckedColor = MaterialTheme.colorScheme.onSurfaceVariant,
                checkmarkColor = MaterialTheme.colorScheme.onPrimary
            )
        )
        Text(text, fontSize = 17.sp, modifier = Modifier.padding(start = 6.dp))
    }
}

@Composable
fun FormDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(modifier.padding(vertical = 3.dp), thickness = 1.dp, color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
}
