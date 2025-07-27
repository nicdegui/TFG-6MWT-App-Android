package com.example.app6mwt.ui

import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.* // Importar todos los necesarios
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.app6mwt.ui.theme.*
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.KeyboardType
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.app6mwt.data.model.Paciente
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.mikephil.charting.BuildConfig

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PatientManagementScreen(
    viewModel: PatientManagementViewModel = hiltViewModel(),
    onNavigateToPreparation: (patientId: String, patientName: String, patientHasHistory: Boolean) -> Unit,
    onNavigateToHistory: (patientId: String) -> Unit,
    onExitApp: () -> Unit
) {
    val pacientesConEstado by viewModel.pacientesConEstadoHistorial.collectAsStateWithLifecycle()
    val currentSelectedPatientInfo by viewModel.selectedPatientInfo.collectAsStateWithLifecycle()

    LaunchedEffect(key1 = viewModel) {
        viewModel.navigateToEvent.collect { event ->
            when (event) {
                is NavigationEvent.ToPreparationScreen -> {
                    onNavigateToPreparation(
                        event.patientId,
                        event.patientName,
                        event.patientHasHistory
                    )
                }
                is NavigationEvent.ToHistoryScreen -> {
                    onNavigateToHistory(event.patientId)
                }
                // is NavigationEvent.ToSettingsScreen -> onNavigateToSettings()
                is NavigationEvent.ExitApp -> onExitApp()
            }
        }
    }
    val showExitAppDialog = viewModel.showExitAppConfirmationDialog

    BackHandler(enabled = true) {
        viewModel.requestExitApp()
    }

    Scaffold(
        topBar = {
            AppTopBar(title = "GESTIÓN DE PACIENTES")
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .padding(16.dp)
        ) {
            PatientSearchBar(
                query = viewModel.searchQuery,
                onQueryChanged = viewModel::onSearchQueryChanged,
                onSearch = viewModel::performSearch,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalAlignment = Alignment.Top
            ) {
                AllPatientsTable(
                    pacientes = pacientesConEstado,
                    onPatientClick = viewModel::onPatientSelectedFromList,
                    selectedPatientId = currentSelectedPatientInfo?.paciente?.id,
                    modifier = Modifier
                        .weight(0.6f)
                        .fillMaxHeight()
                )

                Spacer(modifier = Modifier.width(16.dp))

                RightPanel(
                    selectedPatientInfo = currentSelectedPatientInfo,
                    onViewHistoryClick = viewModel::onViewHistoryClicked,
                    onEditPatientClick = viewModel::onStartEditPatientName,
                    onDeletePatientClick = viewModel::requestDeleteSelectedPatient,
                    modifier = Modifier
                        .weight(0.4f)
                        .fillMaxHeight(),
                    onPrepareTestClicked = viewModel::onPrepareTestClicked
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                ExtendedFloatingActionButton(
                    onClick = viewModel::onOpenAddPatientDialog,
                    containerColor = ButtonActionColor,
                    contentColor = Color.White,
                    icon = { Icon(Icons.Filled.Add, "Añadir paciente") },
                    text = { Text("Nuevo paciente", fontSize = 18.sp) },
                    modifier = Modifier.padding(end = 8.dp)
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(horizontal = 4.dp)
                ) {
                    InfoBox(
                        title = "Información:",
                        content = viewModel.infoMessage,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                IconButton(
                    onClick = viewModel::onOpenSettingsDialog,
                    modifier = Modifier.padding(start = 4.dp)
                ) {
                    Icon(
                        Icons.Filled.Settings,
                        contentDescription = "Ajustes",
                        modifier = Modifier.size(32.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }

    // --- Diálogos ---
    if (viewModel.showAddPatientDialog) {
        AddPatientDialog(
            patientName = viewModel.newPatientNameInput,
            onPatientNameChange = viewModel::onNewPatientNameChange,
            onConfirm = viewModel::onConfirmAddPatient,
            onDismiss = viewModel::onCloseAddPatientDialog
        )
    }

    if (viewModel.isEditingPatientName && currentSelectedPatientInfo != null) {
        EditPatientNameDialog(
            currentName = viewModel.editingPatientNameValue,
            onNameChange = viewModel::onEditingPatientNameChange,
            onConfirm = viewModel::onConfirmEditPatientName,
            onDismiss = viewModel::onCancelEditPatientName,
            onCancel = viewModel::onCancelEditPatientName
        )
    }

    // --- NUEVO: Diálogo de confirmación de eliminación ---
    if (viewModel.showDeletePatientConfirmationDialog && currentSelectedPatientInfo != null) {
        DeletePatientConfirmationDialog(
            patientName = currentSelectedPatientInfo!!.paciente.nombre,
            patientId = currentSelectedPatientInfo!!.paciente.id,
            onConfirm = viewModel::confirmDeletePatient,
            onCancel = viewModel::cancelDeletePatient,
            onDismiss = viewModel::cancelDeletePatient
        )
    }

    if (showExitAppDialog) {
        ConfirmationDialog(
            title = "Confirmar Salida",
            text = "¿Está seguro de que desea salir de la aplicación?",
            onConfirm = viewModel::confirmExitApp,
            onDismiss = viewModel::cancelExitApp,
            confirmButtonText = "Salir",
            dismissButtonText = "Cancelar"
        )
    }

    if (viewModel.showSettingsDialog) {
        SettingsDialog(
            currentDialogState = viewModel.alarmThresholdsDialogState, // Cambiado el nombre del parámetro
            onDialogStateChange = viewModel::onSettingsDialogStateChanged, // Cambiado el nombre del parámetro
            onSave = viewModel::saveSettings,
            onDismiss = viewModel::onCloseSettingsDialog,
            appVersion = BuildConfig.VERSION_NAME
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTopBar(title: String) {
    CenterAlignedTopAppBar(
        title = {
            Text(
                text = title,
                fontSize = 25.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        },
        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
            containerColor =  MaterialTheme.colorScheme.primary,
            titleContentColor = TextOnSecondary
        )
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PatientSearchBar(
    query: String,
    onQueryChanged: (String) -> Unit,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current

    Row(
        modifier = modifier
            .height(56.dp)
            .background(ElementBackgroundColor, RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChanged,
            placeholder = { Text("Buscar por ID de paciente...") },
            singleLine = true,
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(8.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = DarkerBlueHighlight,
                unfocusedBorderColor = LightBluePrimary.copy(alpha = 0.7f),
                cursorColor = DarkerBlueHighlight,
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                disabledContainerColor = Color.Transparent
            ),
            keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(
                onSearch = {
                    onSearch()
                    focusManager.clearFocus()
                }
            )
        )
        IconButton(onClick = {
            onSearch()
            focusManager.clearFocus()
        }) {
            Icon(Icons.Filled.Search, contentDescription = "Buscar", tint = DarkerBlueHighlight)
        }
    }
}

@Composable
fun AllPatientsTable(
    pacientes: List<PacienteConHistorialReal>, // CAMBIO DE TIPO
    onPatientClick: (PacienteConHistorialReal) -> Unit, // CAMBIO DE TIPO
    selectedPatientId: String?, // Para saber cuál resaltar
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(ElementBackgroundColor, RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        Text(
            text = "Pacientes registrados",
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 10.dp),
            color = MaterialTheme.colorScheme.onSurface
        )
        if (pacientes.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No hay pacientes registrados en el sistema.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(pacientes, key = { pacienteInfoItem -> pacienteInfoItem.paciente.id }) { pacienteInfo ->
                    PatientRow(
                        paciente = pacienteInfo.paciente,
                        isSelected = pacienteInfo.paciente.id == selectedPatientId,
                        onPatientClick = { onPatientClick(pacienteInfo) }
                    )
                }
            }
        }
    }
}

@Composable
fun PatientRow(
    paciente: Paciente,
    isSelected: Boolean,
    onPatientClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onPatientClick() }
            .background(
                if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                RoundedCornerShape(8.dp)
            )
            .padding(vertical = 10.dp, horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(paciente.nombre, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(8.dp))
        Text("ID: ${paciente.id}", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
    }
}

@Composable
fun RightPanel(
    selectedPatientInfo: PacienteConHistorialReal?,
    onViewHistoryClick: () -> Unit,
    onEditPatientClick: () -> Unit,
    onDeletePatientClick: () -> Unit,
    modifier: Modifier = Modifier,
    onPrepareTestClicked: () -> Unit
) {
    val isPatientActuallySelected = selectedPatientInfo != null
    val canViewHistory = selectedPatientInfo?.tieneHistorialReal == true

    Column(
        modifier = modifier
            .fillMaxHeight()
            .padding(start = 8.dp, end = 8.dp, top = 0.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ActionButton(
            text = "Preparar prueba",
            onClick = onPrepareTestClicked,
            enabled = isPatientActuallySelected
        )
        Spacer(Modifier.height(5.dp))
        ActionButton(
            text = "Ver historial",
            onClick = onViewHistoryClick,
            enabled = canViewHistory
        )
        Spacer(Modifier.height(5.dp))
        ActionButton(
            text = "Editar nombre",
            onClick = onEditPatientClick, // Esto ahora abre el diálogo
            enabled = isPatientActuallySelected
        )
        Spacer(Modifier.height(5.dp)) // NUEVO: Espacio antes del botón de eliminar

        // --- NUEVO: Botón Eliminar Paciente ---
        ActionButton(
            text = "Eliminar paciente",
            onClick = onDeletePatientClick,
            enabled = isPatientActuallySelected,
            // Podrías considerar un color de fondo diferente para este botón
            // colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer)
        )

        // --- MODIFICADO: EditPatientNameSection se ha movido a un diálogo ---
        // Ya no se muestra aquí

        Spacer(modifier = Modifier.weight(1f)) // Empuja los InfoBox hacia abajo

        InfoBox(
            title = "Historial registrado:",
            content = when {
                !isPatientActuallySelected -> "---"
                selectedPatientInfo.tieneHistorialReal == true -> "Sí"
                else -> "No"
            },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(5.dp)) // Aumentado ligeramente el espacio

        InfoBox(
            title = "ID paciente seleccionado:",
            content = selectedPatientInfo?.paciente?.id ?: "---",
            modifier = Modifier.fillMaxWidth()
        )
    }
}


// --- NUEVO: Diálogo para Editar Nombre ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditPatientNameDialog(
    currentName: String,
    onNameChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit, // Para cerrar el diálogo al tocar fuera o con botón de atrás
    onCancel: () -> Unit   // Para el botón explícito de "Cancelar"
) {
    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh) // Color de fondo del diálogo
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .width(IntrinsicSize.Max), // Para que el diálogo no sea excesivamente ancho
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "Editar nombre del paciente",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                OutlinedTextField(
                    value = currentName,
                    onValueChange = onNameChange,
                    label = { Text("Nuevo nombre del paciente", fontSize = 16.sp) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                    shape = RoundedCornerShape(8.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = DarkerBlueHighlight,
                        unfocusedBorderColor = LightBluePrimary.copy(alpha = 0.7f),
                        cursorColor = DarkerBlueHighlight,
                        focusedContainerColor = Color.Transparent, // Fondo del campo de texto
                        unfocusedContainerColor = Color.Transparent
                    ),
                    keyboardOptions = KeyboardOptions.Default.copy(
                        capitalization = KeyboardCapitalization.Words,
                        imeAction = ImeAction.Done // Aún puede ser Done, pero no confirmará
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = { focusManager.clearFocus() } // --- MODIFICADO: Solo limpiar foco ---
                    )
                )
                LaunchedEffect(Unit) { // Para solicitar foco cuando aparece el diálogo
                    focusRequester.requestFocus()
                }

                Spacer(modifier = Modifier.height(24.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End // Botones a la derecha
                ) {
                    TextButton(
                        onClick = onCancel, // Usar onCancel para la acción del botón
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Cancelar", fontSize = 16.sp)
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = onConfirm,
                        enabled = currentName.isNotBlank(), // Habilitar solo si hay texto
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ButtonActionColor,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Filled.Check, contentDescription = "Confirmar Edición", modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Confirmar", fontSize = 16.sp)
                    }
                }
            }
        }
    }
}

// --- Diálogo para Añadir Paciente (con corrección en KeyboardActions) ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddPatientDialog(
    patientName: String,
    onPatientNameChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .width(IntrinsicSize.Max),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "Registrar nuevo paciente",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                OutlinedTextField(
                    value = patientName,
                    onValueChange = onPatientNameChange,
                    label = { Text("Nombre completo del paciente", fontSize = 16.sp) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                    shape = RoundedCornerShape(8.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = DarkerBlueHighlight,
                        unfocusedBorderColor = LightBluePrimary.copy(alpha = 0.7f),
                        cursorColor = DarkerBlueHighlight,
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                    ),
                    keyboardOptions = KeyboardOptions.Default.copy(
                        capitalization = KeyboardCapitalization.Words,
                        imeAction = ImeAction.Done // Aún puede ser Done, pero no confirmará
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = { focusManager.clearFocus() } // --- MODIFICADO: Solo limpiar foco ---
                    )
                )
                LaunchedEffect(Unit) {
                    focusRequester.requestFocus()
                }

                Spacer(modifier = Modifier.height(24.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = onDismiss, // Aquí onDismiss es la acción de cancelar/cerrar
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Cancelar", fontSize = 16.sp)
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = onConfirm,
                        enabled = patientName.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ButtonActionColor,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = "Registrar paciente", modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Registrar", fontSize = 16.sp)
                    }
                }
            }
        }
    }
}

// --- NUEVO: Diálogo de Confirmación de Eliminación ---
@Composable
fun DeletePatientConfirmationDialog(
    patientName: String,
    patientId: String,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,  // Para el botón de cancelar
    onDismiss: () -> Unit // Para cerrar al tocar fuera o botón atrás
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Confirmar eliminación",
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp
            )
        },
        text = {
            Text(
                text = "¿Está seguro de que quiere eliminar al paciente '$patientName' (ID: $patientId)? Esta acción no se puede deshacer.",
                fontSize = 16.sp
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error, // Color rojo para acción destructiva
                    contentColor = MaterialTheme.colorScheme.onError
                )
            ) {
                Text("Eliminar")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onCancel // Usar onCancel para la acción del botón
            ) {
                Text("Cancelar")
            }
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh, // Color de fondo del diálogo
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurfaceVariant
    )
}


@Composable
fun ActionButton(
    modifier: Modifier = Modifier,
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    colors: ButtonColors = ButtonDefaults.buttonColors( // Permitir pasar colores personalizados
        containerColor = ButtonActionColor,
        contentColor = Color.White,
        disabledContainerColor = ButtonActionColor.copy(alpha = 0.5f),
        disabledContentColor = Color.White.copy(alpha = 0.7f)
    )
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        colors = colors,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp), // Usar heightIn para permitir que crezca si el texto es muy largo, pero tener un mínimo
        shape = RoundedCornerShape(12.dp)
    ) {
        Text(text, fontSize = 18.sp, textAlign = TextAlign.Center) // Ajustado el tamaño de fuente ligeramente
    }
}

@Composable
fun InfoBox(title: String, content: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(ElementBackgroundColor, RoundedCornerShape(12.dp))
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.Start
    ) {
        Text(
            title,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(6.dp)) // Aumentado spacer
        Text(
            content,
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            minLines = 1,
            lineHeight = 20.sp
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsDialog(
    currentDialogState: AllSettingsDialogState,
    onDialogStateChange: (AllSettingsDialogState) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
    appVersion: String = "1.0.3",
    appDescription: String = "Aplicación para la gestión de pacientes y el registro de la Prueba de Marcha de Seis Minutos (6MWT)."
) {
    val keyboardController = LocalSoftwareKeyboardController.current

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
            modifier = Modifier.heightIn(max = (LocalConfiguration.current.screenHeightDp * 0.9).dp)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
            ) {
                Text(
                    "Ajustes e Información de la aplicación",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier
                        .padding(bottom = 16.dp)
                        .align(Alignment.CenterHorizontally)
                )

                Column(modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())) {
                    // --- Umbrales de Alarma ---
                    Text(
                        "Umbrales de Alarma (Durante la prueba)",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(bottom = 8.dp, top = 8.dp)
                    )

                    SettingSectionTitle("SpO2 (%) - Alarma")
                    SettingsInputField(
                        label = "SpO2 Crítica (X ≤ SpO2 Crítica)",
                        value = currentDialogState.spo2Critical,
                        onValueChange = { newValue ->
                            if (newValue.length <= 3 && newValue.all { it.isDigit() }) {
                                onDialogStateChange(currentDialogState.copy(spo2Critical = newValue))
                            }
                        },
                    )
                    SettingsInputField(
                        label = "SpO2 Alerta (SpO2 Crítica < X < SpO2 Alerta)",
                        value = currentDialogState.spo2Warning,
                        onValueChange = { newValue ->
                            if (newValue.length <= 3 && newValue.all { it.isDigit() }) {
                                onDialogStateChange(currentDialogState.copy(spo2Warning = newValue))
                            }
                        },
                    )
                    SettingHelpText("SpO2 Crítica < SpO2 Alerta < SpO2 Normal")


                    SettingSectionTitle("Frecuencia Cardíaca (lpm) - Alarma", Modifier.padding(top = 16.dp))
                    SettingsInputField(
                        label = "FC Crítica Baja ( X < FC Crítica Baja)",
                        value = currentDialogState.hrCriticalLow,
                        onValueChange = { newValue ->
                            if (newValue.length <= 3 && newValue.all { it.isDigit() }) {
                                onDialogStateChange(currentDialogState.copy(hrCriticalLow = newValue))
                            }
                        },
                    )
                    SettingsInputField(
                        label = "FC Alerta Baja (Crítica Baja ≤ X < Normal)",
                        value = currentDialogState.hrWarningLow,
                        onValueChange = { newValue ->
                            if (newValue.length <= 3 && newValue.all { it.isDigit() }) {
                                onDialogStateChange(currentDialogState.copy(hrWarningLow = newValue))
                            }
                        },
                    )
                    SettingsInputField(
                        label = "FC Alerta Alta (Normal < X ≤ Crítica Alta)",
                        value = currentDialogState.hrWarningHigh,
                        onValueChange = { newValue ->
                            if (newValue.length <= 3 && newValue.all { it.isDigit() }) {
                                onDialogStateChange(currentDialogState.copy(hrWarningHigh = newValue))
                            }
                        },
                    )
                    SettingsInputField(
                        label = "FC Crítica Alta ( X > Crítica Alta)",
                        value = currentDialogState.hrCriticalHigh,
                        onValueChange = { newValue ->
                            if (newValue.length <= 3 && newValue.all { it.isDigit() }) {
                                onDialogStateChange(currentDialogState.copy(hrCriticalHigh = newValue))
                            }
                        },
                    )
                    SettingHelpText("Crítica Baja < Alerta Baja < Normal < Alerta Alta < Crítica Alta")

                    Divider(modifier = Modifier.padding(vertical = 20.dp))

                    Text(
                        "Rangos de Entrada Válidos (Basal/Post-Prueba)",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    SettingSectionTitle("SpO2 (%) - Input")
                    SettingsRangeInputFields(
                        minLabel = "Mín. SpO2", minValue = currentDialogState.spo2InputMin,
                        onMinValueChange = { newValue ->
                            if (newValue.length <= 3 && newValue.all { it.isDigit() }) {
                                onDialogStateChange(currentDialogState.copy(spo2InputMin = newValue))
                            }
                        },
                        maxLabel = "Máx. SpO2", maxValue = currentDialogState.spo2InputMax,
                        onMaxValueChange = { newValue ->
                            if (newValue.length <= 3 && newValue.all { it.isDigit() }) {
                                onDialogStateChange(currentDialogState.copy(spo2InputMax = newValue))
                            }
                        },
                    )

                    SettingSectionTitle("Frecuencia Cardíaca (lpm) - Input", Modifier.padding(top = 16.dp))
                    SettingsRangeInputFields(
                        minLabel = "Mín. FC", minValue = currentDialogState.hrInputMin,
                        onMinValueChange = { newValue ->
                            if (newValue.length <= 3 && newValue.all { it.isDigit() }) {
                                onDialogStateChange(currentDialogState.copy(hrInputMin = newValue))
                            }
                        },
                        maxLabel = "Máx. FC", maxValue = currentDialogState.hrInputMax,
                        onMaxValueChange = { newValue ->
                            if (newValue.length <= 3 && newValue.all { it.isDigit() }) {
                                onDialogStateChange(currentDialogState.copy(hrInputMax = newValue))
                            }
                        },
                    )

                    SettingSectionTitle("Presión Arterial (mmHg) - Input", Modifier.padding(top = 16.dp))
                    SettingsRangeInputFields(
                        minLabel = "Mín. Sistólica", minValue = currentDialogState.bpSystolicMin,
                        onMinValueChange = { newValue ->
                            if (newValue.length <= 3 && newValue.all { it.isDigit() }) {
                                onDialogStateChange(currentDialogState.copy(bpSystolicMin = newValue))
                            }
                        },
                        maxLabel = "Máx. Sistólica", maxValue = currentDialogState.bpSystolicMax,
                        onMaxValueChange = { newValue ->
                            if (newValue.length <= 3 && newValue.all { it.isDigit() }) {
                                onDialogStateChange(currentDialogState.copy(bpSystolicMax = newValue))
                            }
                        },
                    )
                    Spacer(Modifier.height(4.dp))
                    SettingsRangeInputFields(
                        minLabel = "Mín. Diastólica", minValue = currentDialogState.bpDiastolicMin,
                        onMinValueChange = { newValue ->
                            if (newValue.length <= 3 && newValue.all { it.isDigit() }) {
                                onDialogStateChange(currentDialogState.copy(bpDiastolicMin = newValue))
                            }
                        },
                        maxLabel = "Máx. Diastólica", maxValue = currentDialogState.bpDiastolicMax,
                        onMaxValueChange = { newValue ->
                            if (newValue.length <= 3 && newValue.all { it.isDigit() }) {
                                onDialogStateChange(currentDialogState.copy(bpDiastolicMax = newValue))
                            }
                        },
                    )

                    SettingSectionTitle("Frecuencia Respiratoria (rpm) - Input", Modifier.padding(top = 16.dp))
                    SettingsRangeInputFields(
                        minLabel = "Mín. FR", minValue = currentDialogState.rrMin,
                        onMinValueChange = { newValue ->
                            if (newValue.length <= 2 && newValue.all { it.isDigit() }) { // FR usualmente 2 dígitos
                                onDialogStateChange(currentDialogState.copy(rrMin = newValue))
                            }
                        },
                        maxLabel = "Máx. FR", maxValue = currentDialogState.rrMax,
                        onMaxValueChange = { newValue ->
                            if (newValue.length <= 2 && newValue.all { it.isDigit() }) {
                                onDialogStateChange(currentDialogState.copy(rrMax = newValue))
                            }
                        },
                    )

                    SettingSectionTitle("Escala de Borg (0-10) - Input", Modifier.padding(top = 16.dp))
                    SettingsRangeInputFields(
                        minLabel = "Mín. Borg", minValue = currentDialogState.borgMin,
                        onMinValueChange = { newValue ->
                            if (newValue.length <= 2 && newValue.all { it.isDigit() }) { // Borg max 10
                                onDialogStateChange(currentDialogState.copy(borgMin = newValue))
                            }
                        },
                        maxLabel = "Máx. Borg", maxValue = currentDialogState.borgMax,
                        onMaxValueChange = { newValue ->
                            if (newValue.length <= 2 && newValue.all { it.isDigit() }) {
                                onDialogStateChange(currentDialogState.copy(borgMax = newValue))
                            }
                        },
                    )

                    currentDialogState.validationError?.let { errorMsg ->
                        Text(
                            errorMsg,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                        )
                    }

                    Divider(modifier = Modifier.padding(vertical = 20.dp))

                    Text(
                        "Acerca de esta Aplicación",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Text(appDescription, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom = 4.dp))
                    Text("Versión: $appVersion", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(bottom = 16.dp))
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp), // Espacio sobre los botones
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = {
                        keyboardController?.hide() // Ocultar teclado al cancelar
                        onDismiss()
                    }) { Text("Cancelar") }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = {
                        keyboardController?.hide() // Ocultar teclado al guardar
                        onSave()
                    } ) { Text("Guardar") }
                }
            }
        }
    }
}

// --- Componentes auxiliares para el SettingsDialog ---
@Composable
fun SettingSectionTitle(title: String, modifier: Modifier = Modifier) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        modifier = modifier.padding(bottom = 8.dp)
    )
}

@Composable
fun SettingHelpText(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(bottom = 12.dp, start = 4.dp) // Añadido start padding
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsInputField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Number,
    singleLine: Boolean = true
) {
    val localKeyboardController = LocalSoftwareKeyboardController.current // OBTENEMOS UNO LOCALMENTE
    Log.d("SettingsInputField", "[$label] Composable RECOMPPOSED. LocalKeyboardController: $localKeyboardController")

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, fontSize = 15.sp) }, // Tamaño de fuente un poco más pequeño para label
        keyboardOptions = KeyboardOptions(
            keyboardType = keyboardType,
            imeAction = ImeAction.Done
        ),
        keyboardActions = KeyboardActions(
            onDone = {
                Log.d("SettingsInputField", "[$label] onDone ACTION. Attempting to hide with LocalController: $localKeyboardController")
                localKeyboardController?.hide()
                Log.d("SettingsInputField", "[$label] localKeyboardController.hide() CALLED.")
            }
        ),
        singleLine = singleLine,
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp), // Espacio estándar debajo de cada campo
        shape = RoundedCornerShape(8.dp), // Coherencia con otros OutlinedTextField
        colors = OutlinedTextFieldDefaults.colors(
            // Colores estándar para estos campos
            focusedBorderColor = DarkerBlueHighlight,
            unfocusedBorderColor = LightBluePrimary.copy(alpha = 0.7f),
            cursorColor = DarkerBlueHighlight,
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
        )
    )
}

@Composable
fun SettingsRangeInputFields(
    minLabel: String,
    minValue: String,
    onMinValueChange: (String) -> Unit,
    maxLabel: String,
    maxValue: String,
    onMaxValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp) // Espacio entre los dos campos
    ) {
        SettingsInputField(
            label = minLabel,
            value = minValue,
            onValueChange = onMinValueChange,
            modifier = Modifier.weight(1f),
        )
        SettingsInputField(
            label = maxLabel,
            value = maxValue,
            onValueChange = onMaxValueChange,
            modifier = Modifier.weight(1f),
        )
    }
}
