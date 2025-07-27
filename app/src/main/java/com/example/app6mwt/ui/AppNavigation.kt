package com.example.app6mwt.ui

import android.net.Uri // Necesario para codificar/decodificar JSON
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.google.gson.Gson

object AppDestinations {
    const val PATIENT_MANAGEMENT_ROUTE = "patientManagement"

    // Argumentos comunes
    const val PATIENT_ID_ARG = "patientId"
    const val PATIENT_NAME_ARG = "patientName"
    const val PREPARATION_DATA_ARG = "preparationData" // JSON de TestPreparationData
    const val TEST_FINAL_DATA_ARG = "testFinalData"     // JSON de TestExecutionSummaryData
    const val PATIENT_HAS_HISTORY_ARG = "patientHasHistory"

    // Rutas para PreparationScreen
    const val PREPARATION_SCREEN_BASE_ROUTE = "preparationScreen"
    const val PREPARATION_SCREEN_ROUTE = "$PREPARATION_SCREEN_BASE_ROUTE/{$PATIENT_ID_ARG}/{$PATIENT_NAME_ARG}/{$PATIENT_HAS_HISTORY_ARG}"

    // Rutas para TestExecutionScreen
    const val TEST_EXECUTION_BASE_ROUTE = "testExecution"
    const val TEST_EXECUTION_ROUTE = "$TEST_EXECUTION_BASE_ROUTE/{$PREPARATION_DATA_ARG}"

    // Rutas para TestResultsScreen
    const val TEST_RESULTS_BASE_ROUTE = "testResults"
    const val TEST_RESULTS_ROUTE = "$TEST_RESULTS_BASE_ROUTE/{$PATIENT_ID_ARG}?$TEST_FINAL_DATA_ARG={$TEST_FINAL_DATA_ARG}"

    // Rutas para HistoryScreen
    const val HISTORY_SCREEN_BASE_ROUTE = "testHistoryScreen"
    const val HISTORY_SCREEN_ROUTE = "$HISTORY_SCREEN_BASE_ROUTE/{$PATIENT_ID_ARG}"
}

@Composable
fun AppNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    startDestination: String = AppDestinations.PATIENT_MANAGEMENT_ROUTE,
    onExitApp: () -> Unit
) {
    val gson = Gson()

    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier
    ) {
        // --- Destino para PatientManagementScreen ---
        composable(route = AppDestinations.PATIENT_MANAGEMENT_ROUTE) {
            PatientManagementScreen(
                onNavigateToPreparation = { patientId, patientName, patientHasHistory ->
                    val encodedPatientName = Uri.encode(patientName)
                    val route = AppDestinations.PREPARATION_SCREEN_ROUTE
                        .replace("{${AppDestinations.PATIENT_ID_ARG}}", patientId)
                        .replace("{${AppDestinations.PATIENT_NAME_ARG}}", encodedPatientName)
                        .replace("{${AppDestinations.PATIENT_HAS_HISTORY_ARG}}", patientHasHistory.toString())
                    navController.navigate(route)
                },
                onNavigateToHistory = { patientId -> // patientName ya no es necesario para esta ruta
                    // Navega directamente al TestHistoryScreen del paciente
                    val route = AppDestinations.HISTORY_SCREEN_ROUTE
                        .replace("{${AppDestinations.PATIENT_ID_ARG}}", patientId)
                    navController.navigate(route)
                },
                onExitApp = onExitApp
            )
        }

        // --- Destino para PreparationScreen ---
        composable(
            route = AppDestinations.PREPARATION_SCREEN_ROUTE,
            arguments = listOf(
                navArgument(AppDestinations.PATIENT_ID_ARG) { type = NavType.StringType },
                navArgument(AppDestinations.PATIENT_NAME_ARG) { type = NavType.StringType },
                navArgument(AppDestinations.PATIENT_HAS_HISTORY_ARG) { type = NavType.BoolType }
            )
        ) { backStackEntry ->
            val patientId = backStackEntry.arguments?.getString(AppDestinations.PATIENT_ID_ARG)
            val patientFullNameFromNav = backStackEntry.arguments?.getString(AppDestinations.PATIENT_NAME_ARG)?.let { Uri.decode(it) }
            val patientHasHistory = backStackEntry.arguments?.getBoolean(AppDestinations.PATIENT_HAS_HISTORY_ARG) ?: false

            if (patientId != null && patientFullNameFromNav != null) {
                PreparationScreen(
                    patientIdFromNav = patientId,
                    patientNameFromNav = patientFullNameFromNav,
                    patientHasHistoryFromNav = patientHasHistory,
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToTestExecution = { preparationData ->
                        val preparationDataJson = gson.toJson(preparationData)
                        val encodedJson = Uri.encode(preparationDataJson)
                        val route = AppDestinations.TEST_EXECUTION_ROUTE
                            .replace("{${AppDestinations.PREPARATION_DATA_ARG}}", encodedJson)
                        navController.navigate(route)
                    }
                )
            } else {
                Text("Error: Faltan datos del paciente para la preparación.")
                LaunchedEffect(Unit) { navController.popBackStack() }
            }
        }

        // --- Destino para TestExecutionScreen ---
        composable(
            route = AppDestinations.TEST_EXECUTION_ROUTE,
            arguments = listOf(
                navArgument(AppDestinations.PREPARATION_DATA_ARG) { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val preparationDataJsonEncoded = backStackEntry.arguments?.getString(AppDestinations.PREPARATION_DATA_ARG)
            if (preparationDataJsonEncoded != null) {
                val preparationDataJson = Uri.decode(preparationDataJsonEncoded) // Asegúrate de decodificar
                val preparationData = gson.fromJson(preparationDataJson, TestPreparationData::class.java)
                TestExecutionScreen(
                    preparationData = preparationData,
                    onNavigateBackFromScreen = { navController.popBackStack() },
                    onNavigateToResults = { testExecutionSummaryData ->
                        val summaryDataJson = gson.toJson(testExecutionSummaryData)
                        val encodedSummaryDataJson = Uri.encode(summaryDataJson)
                        val route = AppDestinations.TEST_RESULTS_ROUTE
                            .replace("{${AppDestinations.PATIENT_ID_ARG}}", testExecutionSummaryData.patientId)
                            .replace("{${AppDestinations.TEST_FINAL_DATA_ARG}}", encodedSummaryDataJson)
                        navController.navigate(route)
                    }
                )
            } else {
                Text("Error: Faltan datos de preparación para la prueba.")
                LaunchedEffect(Unit) { navController.popBackStack() }
            }
        }

        // --- Destino para TestResultsScreen ---
        composable(
            route = AppDestinations.TEST_RESULTS_ROUTE,
            arguments = listOf(
                navArgument(AppDestinations.PATIENT_ID_ARG) { type = NavType.StringType },
                navArgument(AppDestinations.TEST_FINAL_DATA_ARG) {
                    type = NavType.StringType
                    nullable = true // Como estaba antes
                }
            )
        ) { backStackEntry ->
            // El patientId es obligatorio en la ruta base.
            // El TEST_FINAL_DATA_ARG es opcional (después del '?').
            val patientIdFromRoute = backStackEntry.arguments?.getString(AppDestinations.PATIENT_ID_ARG)

            if (patientIdFromRoute != null) {
                // El TestResultsViewModel ahora toma el patientId y testFinalData del SavedStateHandle
                val resultsViewModel: TestResultsViewModel = hiltViewModel()
                TestResultsScreen(
                    viewModel = resultsViewModel,
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToPatientManagement = {
                        navController.navigate(AppDestinations.PATIENT_MANAGEMENT_ROUTE) {
                            popUpTo(AppDestinations.PATIENT_MANAGEMENT_ROUTE) { inclusive = true }
                            launchSingleTop = true
                        }
                    }
                )
            } else {
                Text("Error: Falta ID del paciente para mostrar resultados.")
                LaunchedEffect(Unit) { navController.popBackStack() }
            }
        }

        // --- NUEVO Destino para TestHistoryScreen ---
        composable(
            route = AppDestinations.HISTORY_SCREEN_ROUTE,
            arguments = listOf(
                navArgument(AppDestinations.PATIENT_ID_ARG) { type = NavType.StringType }
            )
        ) { /* No necesitas backStackEntry aquí si el ViewModel usa SavedStateHandle */
            // El patientId se pasa automáticamente al SavedStateHandle del TestHistoryViewModel
            // gracias a hiltViewModel() y la coincidencia de nombres en la ruta.
            TestHistoryScreen(
                onNavigateBack = { navController.popBackStack() }
                // El TestHistoryViewModel se inyectará automáticamente con hiltViewModel()
                // dentro de TestHistoryScreen.
            )
        }
    }
}
