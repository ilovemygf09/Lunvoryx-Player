# Informe de Limpieza y Optimización del Proyecto (Project Cleanup Report)
## Lunvoryx Player

**Fecha:** 18 de Noviembre de 2026  
**Ámbito:** Estructura de directorios, recursos gráficos, configuración Gradle, dependencias y scripts de prueba.

---

## 1. Inventario y Clasificación de Archivos

Tras el análisis estático y dinámico del espacio de trabajo, se clasificó la totalidad de los archivos en las siguientes categorías:

### A. Necesarios para Compilar y Ejecutar
* `app/build.gradle`: Configuración del módulo Android, SDKs, firmas y dependencias.
* `build.gradle`: Configuración raíz del plugin Android Gradle.
* `settings.gradle`: Inclusión del módulo `:app` y repositorios oficiales (Google Maven y Maven Central).
* `gradle.properties`: Parámetros de optimización de la JVM y compatibilidad de AndroidX.
* `gradlew`, `gradlew.bat`, `gradle/wrapper/*`: Wrapper oficial de Gradle 9.6.0.
* `app/src/main/AndroidManifest.xml`: Declaración de componentes, permisos y FileProvider.
* `app/proguard-rules.pro`: Reglas de optimización y ofuscación R8.
* `app/src/main/java/tv/lunvoryx/player/**/*`: Paquetes de código fuente Java (`data`, `epg`, `player`, `remote`, `tmdb`, `ui`, `updater`).
* `app/src/main/res/**/*`: Recursos gráficos oficiales de Lunvoryx Player, estilos y XMLs de configuración de seguridad.

### B. Necesarios para Desarrollo y Mantenimiento
* `app/src/test/java/tv/lunvoryx/player/**/*`: Batería de pruebas unitarias automatizadas (M3U, XMLTV, EPG, MediaKind, RemoteServer, UpdateManager).
* `gradle.properties.example`: Plantilla de configuración de firma de producción y variables de compilación.
* `.github/workflows/build.yml`: Pipeline de CI/CD para compilación y verificación automática en GitHub Actions.
* `.github/ISSUE_TEMPLATE/*` y `pull_request_template.md`: Plantillas para reporte de incidencias y colaboración.

### C. Necesarios para Documentación y Publicación
* `README.md`: Documentación principal de presentación, características, compilación y uso.
* `CHANGELOG.md`: Historial estructurado de versiones bajo formato *Keep a Changelog*.
* `CONTRIBUTING.md`: Guía de desarrollo y pautas de contribución.
* `SECURITY.md`: Política de seguridad y canal de comunicación responsable de vulnerabilidades.
* `SECURITY_AUDIT.md`: Informe completo de auditoría de seguridad y mitigaciones.
* `GUIA_GITHUB.md`: Manual paso a paso para la publicación del repositorio y gestión de GitHub Releases.
* `SOURCES.md`: Atribución legal de procedencia de fuentes de prueba abiertas (Blender CC BY 3.0) y fondos sintéticos.
* `VALIDATION.md`: Registro de verificación técnica y pruebas ejecutadas.
* `INFORME_COMPLETO.md`: Histórico de desarrollo y evolución de hitos del proyecto.

### D. Generados Automáticamente o Reconstruibles
* `build/` y `app/build/`: Directorios de artefactos intermedios generados por Gradle (desechables con `gradle clean`).
* `.gradle/`: Caché local de demonios y resolución de dependencias de Gradle.
* `.local/`: Logs de depuración local de emulador (`emulator.log`, `ui.xml`).

### E. Obsoletos, Desvinculados o Inutilizados
* `app/src/test/java/tv/lunvoryx/player/ui/`: Directorio vacío resultante de la retirada de un test de JVM incompatible con stubs de Android Canvas.
* `artifacts/*.xml` y `artifacts/*.png`: Más de 50 capturas de pantalla intermedias y jerarquías UI dumps generadas en pruebas previas de emulador.
* Menciones heredadas del antiguo nombre del reproductor: Se sustituyeron en todos los scripts de prueba (`scripts/adb-test-helpers.ps1`) y recursos para garantizar 0 menciones en la totalidad del proyecto.

### F. Sensibles, Privados o Peligrosos para Publicar
* `local.properties`: Contiene la ruta absoluta del SDK de Android en la máquina del desarrollador (`sdk.dir=...`). **Bloqueado en `.gitignore`**.
* Almacenes de claves y contraseñas de firma (`*.jks`, `*.keystore`): **Bloqueados en `.gitignore`**.
* Archivos `.env` o credenciales personales: **Bloqueados en `.gitignore`**.

---

## 2. Acciones de Limpieza y Correcciones Aplicadas

1. **Blindaje de `.gitignore`:**
   - Se añadió la exclusión de `artifacts/` para evitar que más de 45 MB de capturas temporales de pantalla y volcados XML se incorporen al árbol de Git.
   - Se añadieron patrones de seguridad para bloquear cualquier archivo de clave privada o almacén criptográfico (`*.jks`, `*.keystore`, `*.p12`, `*.pem`, `*.key`, `*.pk8`).
   - Se agregaron filtros para archivos locales del sistema operativo (`.DS_Store`, `Thumbs.db`, `desktop.ini`).

2. **Supresión de Advertencias AGP en `gradle.properties`:**
   - Se añadió `android.sync.suppressAgpWarnings=UNSUPPORTED_PROJECT_OPTION_USE` para silenciar la advertencia de desuso de `android.builtInKotlin=false`, asegurando compilaciones limpias en Android Gradle Plugin 9.4+.

3. **Configuración de Firma Segura en `app/build.gradle`:**
   - Se configuró la firma para la variante `release` mediante variables de entorno del sistema (`RELEASE_STORE_FILE`, `RELEASE_STORE_PASSWORD`, etc.) o propiedades de Gradle desacopladas. De este modo, el repositorio público no contiene rutas absolutas ni contraseñas grabadas en código.

4. **Saneamiento de Scripts de Prueba:**
   - Se renombraron y actualizaron las variables en `scripts/adb-test-helpers.ps1` sustituyendo cualquier identificador heredado por prefijos limpios `Lunvoryx`.
   - Se verificó que `scripts/fixture-server.cjs` solo escuche en la interfaz de bucle invertido (`127.0.0.1:8765`) y utilice únicamente medios de prueba abiertos (Blender / Mux / Shaka Player).

---

## 3. Elementos Conservados por Precaución

* **Archivos PNG generados en `app/src/main/res/drawable-nodpi/`:**
  - `hero_palma.png`, `category_live.png`, `category_movies.png`, `category_series.png`, `brand_header.png`, `app_logo.png`, `ic_launcher.png`, `tv_banner.png`.
  - Se conservan todos íntegros: son los activos de marca de alta definición y texturas ambientales utilizados activamente por las pantallas principales, el banner de Leanback y el icono del lanzador en Android TV.
* **`app/proguard-rules.pro`:**
  - Aunque Media3 y AndroidX proveen reglas de consumidor automáticas, se conserva el archivo como punto de extensión documentado para futuras dependencias.
* **`scripts/fixture-server.cjs` y `scripts/adb-test-helpers.ps1`:**
  - Se conservan en el árbol de desarrollo para permitir a cualquier ingeniero ejecutar pruebas de integración y capturas automatizadas con emuladores de Android TV.
