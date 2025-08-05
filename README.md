# Aplicación Android para la automatización de la 6MWT

**Análisis y diseño de una aplicación basada en un dispositivo vestible para automatizar la prueba de la marcha de seis minutos en pacientes con patología respiratoria**

Trabajo de Fin de Grado (TFG) de Nicolás Gabriel Díez Guillán para el Grado en Ingeniería Biomédica de la Universidade de Vigo.

---

## 📜 Descripción del proyecto

Este repositorio contiene el código fuente de una aplicación nativa para tablets Android, diseñada para automatizar y optimizar la realización de la **Prueba de la Marcha de Seis Minutos (6MWT)** en un entorno clínico.

La aplicación busca solucionar las limitaciones del procedimiento manual, como la alta carga de trabajo del personal sanitario, la falta de un registro continuo de datos y los posibles errores de anotación.

### ✨ Funcionalidades principales

*   **Gestión de pacientes:** creación, búsqueda y selección de pacientes.
*   **Monitorización en tiempo real:** conexión vía **Bluetooth Low Energy (BLE)** con un pulsioxímetro (BERRY BM1000B) para la visualización continua de SpO₂ y Frecuencia Cardíaca.
*   **Automatización de la prueba:** cronómetro de 6 minutos, registro de paradas y vueltas.
*   **Visualización de datos:** gráficas en tiempo real que muestran la evolución de los parámetros fisiológicos.
*   **Alertas de seguridad y tendencias:** indicadores visuales que alertan al profesional si los valores salen de los umbrales de seguridad configurables.
*   **Resultados y persistencia:** almacenamiento de todos los datos de la prueba en una base de datos local (Room).
*   **Generación de informes:** creación de un **informe completo en formato PDF** con todos los datos, tablas y gráficas de la prueba, listo para ser consultado o compartido.
*   **Consulta de historial:** pantalla para revisar todas las pruebas realizadas a un paciente.

---

## 🛠️ Tecnologías utilizadas

Este proyecto fue desarrollado utilizando un stack tecnológico moderno basado en las recomendaciones de Google para el desarrollo de aplicaciones Android:

*   **Lenguaje:** [Kotlin](https://kotlinlang.org/)
*   **UI Toolkit:** [Jetpack Compose](https://developer.android.com/jetpack/compose)
*   **Arquitectura:** MVVM (Model-View-ViewModel) y UDF (Unidirectional Data Flow)
*   **Asincronía:** [Kotlin Coroutines](https://kotlinlang.org/docs/coroutines-overview.html) & Flow
*   **Inyección de dependencias:** [Hilt](https://developer.android.com/training/dependency-injection/hilt-android)
*   **Persistencia:**
    *   [Room](https://developer.android.com/training/data-storage/room) para la base de datos.
    *   [DataStore](https://developer.android.com/topic/libraries/architecture/datastore) para las preferencias.
*   **Navegación:** [Navigation Compose](https://developer.android.com/jetpack/compose/navigation)
*   **Comunicación:** API nativa de Android para [Bluetooth Low Energy (BLE)](https://developer.android.com/guide/topics/connectivity/bluetooth/ble)
*   **Gráficas:** [MPAndroidChart](https://github.com/PhilJay/MPAndroidChart)
*   **PDF:** API nativa de Android (`PdfDocument` y `Canvas`)

---

## 📸 Capturas de Pantalla



---

## 👤 Autor

*   **Nicolás Gabriel Díez Guillán**
