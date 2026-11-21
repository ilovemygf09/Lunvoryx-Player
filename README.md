# Lunvoryx Player

Reproductor IPTV nativo de alto rendimiento para **Android TV, Google TV y Fire OS**, diseñado específicamente para control remoto con mando a distancia (D-Pad). Organiza y reproduce tus listas M3U y credenciales Xtream Codes mediante AndroidX Media3 (ExoPlayer), con una interfaz cinematográfica en tonos oscuros galácticos, estética *glassmorphism* y esquinas continuas (*$G^2$ Squircles*).

---

## Características Principales

- **Navegación Cinematográfica Optimizada para Mando:**
  - Menú lateral izquierdo con selector activo de alto contraste.
  - Vistas divididas en 3 columnas para **TV En Vivo**, **Películas**, **Series** y **Buscar**.
  - Categorías esenciales garantizadas (*Todas*, *Favoritos*, *Seguir viendo / Reanudar*) independientemente del origen de la lista.
- **Importación Multifuncional y Carga Remota vía QR:**
  - **Desde tu móvil por Wi-Fi:** Escanea el código QR en la pantalla de la TV y añade listas M3U o credenciales Xtream Codes desde el navegador de tu teléfono local mediante un micro-servidor HTTP local empotrado y protegido por token criptográfico temporal.
  - **Xtream Codes API:** Soporte nativo para usuario, contraseña y URL de servidor con autoconversión de streams TS/HLS.
  - **Enlace M3U / M3U8:** Descarga e importación por URL directa con soporte para cabeceras HTTP personalizadas (`User-Agent`, `Referer`).
  - **Archivo Local:** Selección de archivos `.m3u` o `.m3u8` desde almacenamiento interno o USB.
- **Metadatos y Carátulas Cinematográficas (TMDB):**
  - Integración opcional con The Movie Database (TMDB) para descargar pósters en alta definición, sinopsis, año de estreno, géneros y reparto de actores.
- **Reproducción Avanzada con AndroidX Media3:**
  - Compatibilidad completa con HLS (`.m3u8`), MPEG-DASH (`.mpd`) y flujos de transporte progresivos MPEG-TS (`.ts`).
  - Pistas dinámicas de audio multicanal, subtítulos embebidos y selección de bitrate/resolución.
  - Memorización de posición y progreso para contenido bajo demanda (VOD).
- **Guía Electrónica de Programación (EPG / XMLTV):**
  - Descarga e indexación de guías XMLTV (archivos directos o comprimidos `.xml.gz`).
  - Visualización «Ahora / En Directo» y «A continuación» con barra de progreso temporal en tiempo real.
- **Actualizaciones Remotas Seguras (OTA via GitHub Releases):**
  - Comprobación automática o manual de nuevas versiones publicadas en GitHub Releases.
  - Verificación criptográfica estricta de la firma digital del APK descargado contra la aplicación instalada antes de solicitar la instalación, protegiendo al usuario contra sustituciones maliciosas o ataques de degradación (*anti-downgrade*).

---

## Requisitos y Entorno de Compilación

| Parámetro | Valor |
| :--- | :--- |
| **Lenguaje** | Java 17 |
| **Android Gradle Plugin** | 9.4.1 |
| **Gradle Wrapper** | 9.6.0 |
| **Compile SDK** | 37 (Android 15+) |
| **Target SDK** | 36 |
| **Min SDK** | 23 (Android 6.0 Marshmallow o superior) |
| **Arquitectura de UI** | Android Leanback / Vector Canvas nativo sin dependencias de frameworks pesados |

---

## Compilación en Local

1. Asegúrate de tener configurada la variable `JAVA_HOME` apuntando a tu JDK 17 o superior.
2. Crea el archivo `local.properties` indicando la ruta de tu SDK de Android (ejemplo en Windows: `sdk.dir=C:/Users/TU_USUARIO/AppData/Local/Android/Sdk`).
3. Abre PowerShell en la raíz del proyecto y ejecuta:

```powershell
# Ejecutar la suite completa de pruebas unitarias (46 tests)
.\gradlew.bat testDebugUnitTest

# Análisis estático con Android Lint
.\gradlew.bat lintPreview

# Compilar APK optimizado de vista previa (código ofuscado y recursos reducidos)
.\gradlew.bat assemblePreview

# Compilar APK oficial Release (requiere configurar credenciales en gradle.properties)
.\gradlew.bat assembleRelease
```

El APK de vista previa se genera en:  
`app/build/outputs/apk/preview/app-preview.apk`

---

## Instalación en Android TV

Puedes instalar el APK en un televisor o emulador con depuración ADB habilitada:

```powershell
adb install -r artifacts/LunvoryxPlayer-1.0.0-preview.apk
```

O iniciar la aplicación directamente:

```powershell
adb shell am start -n tv.lunvoryx.player/.MainActivity
```

---

## Controles durante la Reproducción

| Mando a Distancia (D-Pad) | Acción |
| :--- | :--- |
| **OK / Centro / Info** | Muestra u oculta la barra de controles |
| **Arriba / Abajo** *(controles ocultos)* | Cambia al canal anterior o siguiente |
| **Izquierda / Derecha** *(controles ocultos)* | Retroceso de 10 s / Avance de 30 s (en VOD / Películas) |
| **Flechas de navegación** *(controles visibles)* | Mueve el foco entre las opciones de audio, subtítulos y calidad |
| **Atrás (Back)** | Cierra los controles; si ya están ocultos, vuelve a la biblioteca |

---

## Seguridad y Privacidad

- **Sin Telemetría:** Lunvoryx Player no recopila datos de navegación, telemetría ni hábitos de consumo.
- **Almacenamiento Local Aislado:** Todas las listas, favoritos y claves se guardan en el almacenamiento privado de la app (`Context.MODE_PRIVATE`).
- **Verificación Criptográfica:** Consulta nuestro informe completo en [SECURITY_AUDIT.md](SECURITY_AUDIT.md).
- **Política de Vulnerabilidades:** Consulta [SECURITY.md](SECURITY.md).

---

## Publicación y Contribución

- **Guía paso a paso para GitHub y Releases:** Consulta [GUIA_GITHUB.md](GUIA_GITHUB.md).
- **Registro de cambios por versión:** Consulta [CHANGELOG.md](CHANGELOG.md).
- **Pautas para colaboradores y Pull Requests:** Consulta [CONTRIBUTING.md](CONTRIBUTING.md).
- **Procedencia de recursos y medios libres:** Consulta [SOURCES.md](SOURCES.md).
- **Informe de pruebas y validación:** Consulta [VALIDATION.md](VALIDATION.md).
