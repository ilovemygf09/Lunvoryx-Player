# Auditoría Integral de Seguridad (Security Audit Report)
## Lunvoryx Player (Android TV / IPTV)

**Fecha:** 18 de Noviembre de 2026  
**Paquete:** `tv.lunvoryx.player`  
**Objetivo de Evaluación:** Código fuente, recursos, manifiesto, dependencias, servidor HTTP local empotrado y módulo de actualizaciones remotas (OTA).  
**Estándar de Referencia:** OWASP Mobile Application Security Verification Standard (MASVS v2.0) y Mobile App Security Testing Guide (MASTG).

---

## 1. Resumen Ejecutivo de Seguridad

Se ha llevado a cabo una auditoría estática y dinámica exhaustiva sobre el proyecto **Lunvoryx Player**. El análisis se centró en la identificación y mitigación proactiva de riesgos críticos asociados a aplicaciones multimedia conectadas en redes locales, gestión de credenciales, procesamiento de archivos no confiables (M3U / XMLTV) y el mecanismo de distribución de actualizaciones mediante **GitHub Releases**.

### Conclusiones Principales:
1. **Ausencia de Secretos Estáticos:** No se encontraron contraseñas, claves privadas, keystores de producción ni API keys fijas en el código fuente ni en el historial de Git. La integración opcional con TMDB se gestiona dinámicamente mediante entrada de usuario y almacenamiento en `SharedPreferences` privado.
2. **Servidor Local Hardened:** Se corrigió la vulnerabilidad de acceso no autenticado en la red local LAN incorporando emparejamiento por token criptográfico temporal (`SecureRandom`), límite estricto de tamaño de carga (25 MB) y apagado automático por inactividad tras 15 minutos.
3. **Canal de Actualizaciones Criptográficamente Seguro:** Se blindó el sistema OTA (`UpdateManager.java`) con verificación obligatoria de firmas digitales (certificate pinning contra la app instalada), protección anti-downgrade y validación de hash SHA-256 e identidad del paquete (`tv.lunvoryx.player`).
4. **Protección en Tratamiento de Datos Externos:** El analizador de guías EPG (`XmltvParser.java`) cuenta con defensas explícitas contra inyección de entidades externas (XXE) y bombas de entidades (Billion Laughs), y el analizador M3U restringe los protocolos permitidos a HTTP/HTTPS para evitar inclusión de archivos locales (`file://`).

---

## 2. Matriz de Hallazgos y Vulnerabilidades

| ID | Categoría OWASP | Severidad | Estado | Descripción Breve |
| :--- | :--- | :--- | :--- | :--- |
| **SEC-01** | MASVS-NETWORK | **ALTA** | **CORREGIDO Y VERIFICADO** | Servidor HTTP local accesible sin autenticación por cualquier equipo en la misma red Wi-Fi. |
| **SEC-02** | MASVS-RESILIENCE | **CRÍTICA** | **CORREGIDO Y VERIFICADO** | Actualizador OTA sin comprobación de firma digital ni protección anti-downgrade antes de solicitar la instalación. |
| **SEC-03** | MASVS-PLATFORM | **ALTA** | **CORREGIDO Y VERIFICADO** | Falta del permiso `REQUEST_INSTALL_PACKAGES` en `AndroidManifest.xml` para Android 8.0+. |
| **SEC-04** | MASVS-NETWORK | **MEDIA** | **CORREGIDO Y VERIFICADO** | Posibilidad de introducir URLs no cifradas (HTTP) en la configuración del repositorio GitHub. |
| **SEC-05** | MASVS-STORAGE | **MEDIA** | **CORREGIDO Y VERIFICADO** | Riesgo de subida y almacenamiento temporal en Git de dumps de emulador y APKs pesados (~45 MB). |
| **SEC-06** | MASVS-NETWORK | **BAJA (Aceptado)** | **DOCUMENTADO** | Uso de `android:usesCleartextTraffic="true"` en el manifiesto para streaming IPTV. |

---

## 3. Detalle Técnico de los Hallazgos y Correcciones

### SEC-01: Servidor HTTP Local sin Autenticación en Red LAN
* **Severidad:** ALTA (CVSS 7.5 - `CVSS:3.1/AV:A/AC:L/PR:N/UI:N/S:U/C:N/I:H/A:L`)
* **Archivo afectado:** `app/src/main/java/tv/lunvoryx/player/remote/RemoteServer.java`
* **Vulnerabilidad:**  
  Cualquier dispositivo conectado a la misma subred Wi-Fi (en entornos compartidos como pisos de estudiantes, redes de invitados o cafeterías) podía enviar peticiones HTTP `POST /api/playlist` o `GET /api/navigate` y alterar la lista de reproducción activa o provocar navegación no deseada en el televisor sin haber interactuado físicamente con él.
* **Solución Implementada:**  
  1. **Generación de Token de Emparejamiento por Sesión:** Al inicializarse `RemoteServer`, se genera un token pseudoaleatorio criptográficamente seguro de 64 bits utilizando `java.security.SecureRandom`.
  2. **Incrustación en Código QR:** La URL codificada en el QR mostrado en la TV incluye el parámetro `?token=<sessionToken>`. Al escanearlo con el móvil, el navegador carga la página pre-autorizada de forma transparente y sin fricción para el usuario.
  3. **Validación en Endpoints Mutantes:** Las rutas `/api/playlist` y `/api/navigate` verifican la coincidencia estricta del token recibido (vía parámetro de consulta, cabecera `X-Session-Token` o cuerpo JSON). En caso de discrepancia, el servidor responde `HTTP/1.1 401 Unauthorized` y descarta la solicitud.
  4. **Protección de Memoria (DoS Guard):** Se rechazan de inmediato peticiones con `Content-Length > 25 MB` devolviendo `HTTP/1.1 413 Payload Too Large`.
  5. **Auto-apagado por Inactividad:** Se implementó un temporizador que detiene automáticamente el servicio local si transcurren más de 15 minutos sin peticiones.
* **Estado:** Corregido y verificado mediante pruebas unitarias (`RemoteServerTest.java`).

---

### SEC-02: Actualizaciones OTA sin Verificación Criptográfica de Firma
* **Severidad:** CRÍTICA (CVSS 8.8 - `CVSS:3.1/AV:N/AC:M/PR:N/UI:R/S:C/C:H/I:H/A:H`)
* **Archivo afectado:** `app/src/main/java/tv/lunvoryx/player/updater/UpdateManager.java`
* **Vulnerabilidad:**  
  El gestor de descargas original descargaba el APK apuntado por la API de GitHub e invocaba directamente el `FileProvider` e intent de instalación sin comprobar si el archivo descargado correspondía a la aplicación legítima, si contenía una firma idéntica a la instalada o si se trataba de una versión inferior (riesgo de sustitución o downgrade si se alteraba el repositorio en Ajustes o mediante DNS spoofing).
* **Solución Implementada:**  
  Se desarrolló el método `verifyDownloadedApk(Context, File, String)` en `UpdateManager.java`, el cual ejecuta cuatro barreras de control antes de permitir cualquier intento de instalación:
  1. **Validación de Archivo:** Comprobación de integridad del formato del paquete APK mediante `PackageManager.getPackageArchiveInfo()`.
  2. **Verificación de Identidad de Paquete:** Comprobación estricta de que el paquete sea exactamente `tv.lunvoryx.player`.
  3. **Protección Anti-Downgrade:** Comparación del `versionCode` del paquete descargado frente al instalado. Si la versión descargada es menor o igual, se rechaza la instalación con `SecurityException`.
  4. **Certificate Pinning contra la App Instalada:** Se extrae el certificado de firma de la app instalada en el televisor (`SigningInfo` en Android 9+ o `signatures` en versiones anteriores) y se compara byte a byte contra los certificados del APK descargado (`verifySignaturesMatch`). Si las firmas no coinciden, la actualización es abortada de forma terminante.
  5. **Comprobación de Integridad SHA-256:** Si la release incluye un hash SHA-256, se calcula y compara el hash del archivo descargado antes de continuar.
* **Estado:** Corregido y verificado mediante pruebas unitarias (`UpdateManagerTest.java`).

---

### SEC-03: Ausencia de `REQUEST_INSTALL_PACKAGES` para Android 8.0+
* **Severidad:** ALTA (Impacto Operativo / Bloqueo de Actualización)
* **Archivo afectado:** `app/src/main/AndroidManifest.xml`
* **Vulnerabilidad:**  
  A partir de Android 8.0 (API 26), el sistema operativo exige que las aplicaciones que pretendan iniciar una instalación de paquetes declaren explícitamente el permiso `android.permission.REQUEST_INSTALL_PACKAGES`. Sin este permiso, la llamada a `startActivity` con el intent de instalación arroja una excepción de seguridad o es bloqueada silenciosamente por el sistema.
* **Solución Implementada:**  
  1. Se añadió `<uses-permission android:name="android.permission.REQUEST_INSTALL_PACKAGES" />` en `AndroidManifest.xml`.
  2. En `UpdateManager.launchApkInstall()`, se introdujo una comprobación de `canRequestPackageInstalls()`. Si el usuario no ha concedido el permiso al reproductor, se abre la pantalla nativa de configuración del sistema (`Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES`) para facilitarle la autorización guiada.
* **Estado:** Corregido y verificado en la compilación del manifiesto.

---

### SEC-04: Sanitización de Repositorios y Forzado de HTTPS en Actualizador
* **Severidad:** MEDIA (CVSS 5.3)
* **Archivos afectados:** `UpdateManager.java` y `MainActivity.java`
* **Vulnerabilidad:**  
  El campo de configuración del repositorio de GitHub en la pantalla de Ajustes permitía introducir cadenas libres, lo que podía derivar en llamadas HTTP no cifradas o rutas inválidas.
* **Solución Implementada:**  
  1. Se introdujo validación mediante expresión regular `^[a-zA-Z0-9_.-]+/[a-zA-Z0-9_.-]+$` en `UpdateManager.isValidRepoString()`.
  2. En `UpdateManager.isSafeHttpsUrl()`, se rechaza cualquier URL que no utilice el protocolo seguro `https://`.
* **Estado:** Corregido y verificado con pruebas unitarias (`UpdateManagerTest.java`).

---

### SEC-05: Exclusión de Artefactos Pesados y Keystores en Git
* **Severidad:** MEDIA (Seguridad de Cadena de Suministro y Tamaño de Repo)
* **Archivo afectado:** `.gitignore`
* **Vulnerabilidad:**  
  La carpeta `artifacts/` contenía cerca de 45 MB de capturas de pantalla de depuración, dumps XML de la interfaz y ejecutables APK no versionables. Si se hubiera realizado un `git add .` sin filtrar, dichos archivos habrían quedado grabados de forma permanente en el historial del repositorio.
* **Solución Implementada:**  
  Se amplió `.gitignore` para bloquear formalmente:
  - Salidas de compilación (`.gradle/`, `build/`, `**/build/`).
  - Archivos de claves privadas y almacenes (`*.jks`, `*.keystore`, `*.p12`, `*.key`, `*.env`).
  - Configuración local del SDK (`local.properties`, `.local/`, `.android/`).
  - Artefactos temporales de prueba (`artifacts/`, `*.log`, `*.tmp`).
* **Estado:** Corregido y verificado con `git status`.

---

### SEC-06: Permisión de Tráfico Cleartext (HTTP) para Fuentes IPTV
* **Severidad:** BAJA / Riesgo Aceptado por Diseño
* **Archivo:** `app/src/main/AndroidManifest.xml` (`android:usesCleartextTraffic="true"`)
* **Análisis de Riesgo:**  
  Android 9+ deshabilita por defecto el tráfico HTTP en texto claro. Sin embargo, en el ecosistema IPTV una gran proporción de servidores de proveedores comunitarios, enlaces MPEG-TS y retransmisiones HLS se sirven a través de puertos HTTP directos (ej. `http://servidor:puerto/canal.ts`). Asimismo, el servidor web local para emparejamiento con el móvil opera en la red local sobre HTTP en el puerto 8080.
* **Mitigación Adoptada:**  
  - Se mantiene la compatibilidad de reproducción IPTV legítima.
  - El actualizador OTA (`UpdateManager.java`) **ignora esta regla global e impone obligatoriamente HTTPS** a nivel de código para la comprobación de versiones y la descarga de APKs.
  - El servidor local exige el token de emparejamiento aleatorio para evitar abusos locales.
* **Estado:** Riesgo residual documentado y controlado.

---

## 4. Auditoría de Secretos y Cadena de Suministro

- **Búsqueda Automatizada de Secretos:** Ejecución de patrones regex contra todo el árbol de código y recursos para tokens, claves privadas, credenciales Xtream fijas y hashes: **0 secretos encontrados**.
- **Dependencias de Terceros:**
  - `androidx.recyclerview:recyclerview:1.3.2` (Estable, mantenida por Google).
  - `androidx.media3:media3-exoplayer:1.11.1` (Librería oficial de Android para streaming).
  - `com.google.zxing:core:3.5.3` (Generación de código QR local).
  - `junit:junit:4.13.2` y `net.sf.kxml:kxml2:2.3.0` (Ámbito exclusivo de pruebas `testImplementation`).
  - No se detectaron dependencias obsoletas, librerías abandonadas ni dependencias publicitarias/rastreadores.

---

## 5. Riesgos Residuales

1. **Intercepción Pasiva en Redes Wi-Fi Abiertas:** Dado que el tráfico web entre el teléfono móvil y el televisor transcurre por HTTP local en el puerto 8080, un atacante con capacidad de captura pasiva de paquetes (sniffing) en una red Wi-Fi sin cifrado WPA2/WPA3 podría interceptar el token de emparejamiento y la URL de la lista enviada. Se mitiga mediante la advertencia en pantalla informando de que ambos dispositivos deben estar en una red doméstica privada de confianza.
2. **URLs Xtream Codes con Credenciales en Query Parameters:** El estándar de facto de la industria IPTV (Xtream Codes API) concatena el usuario y contraseña en los parámetros de la URL (`/get.php?username=...&password=...`). Aunque Lunvoryx Player no almacena estas contraseñas en texto claro en logs ni las comparte externamente, la URL resultante se envía a la cabecera del proveedor IPTV. Esta limitación es inherente a la arquitectura de los proveedores Xtream Codes y no puede eliminarse sin que el proveedor admita autenticación moderna (OAuth2 o Bearer tokens).
