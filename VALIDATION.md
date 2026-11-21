# Validación de Lunvoryx Player 1.0.0

Registro de comprobaciones realizadas durante la auditoría integral y preparación para GitHub, actualizado el 18 de noviembre de 2026.

## APK Entregado y Auditado

| Dato | Resultado |
| :--- | :--- |
| **Archivo** | `artifacts/LunvoryxPlayer-1.0.0-preview.apk` |
| **Tamaño** | 7.963.046 bytes (~7.96 MB) |
| **Variante** | `preview` (código ofuscado y recursos optimizados con R8) |
| **Depuración (`debuggable`)** | Desactivada (`false`) |
| **Firma Digital** | Verificada con `apksigner`: esquemas v1 (JAR) y v2 (APK Signature) válidos |
| **Suma SHA-256** | `3DE83BF789912848BD2993D6A85F4A4A55C2BB45AD634D849F31264C050B7B78` |
| **Package Name** | `tv.lunvoryx.player` |
| **Target SDK / Min SDK** | SDK 36 (Android 14) / Min SDK 23 (Android 6.0) |

---

## Comprobaciones Automáticas (Unit Tests)

**46 pruebas unitarias completadas, 0 fallos, 0 errores y 0 omitidas.**

| Suite de Pruebas | Archivo de Test | Pruebas | Resultado |
| :--- | :--- | :---: | :---: |
| **Lectura y Parseo M3U** | `M3uParserTest.java` | 15 | Superadas (100%) |
| **Clasificación de Contenido** | `MediaKindTest.java` | 5 | Superadas (100%) |
| **Índice EPG «Ahora / Después»** | `EpgIndexTest.java` | 5 | Superadas (100%) |
| **Lectura XMLTV y Defensas XXE** | `XmltvParserTest.java` | 7 | Superadas (100%) |
| **Fechas y Zonas Horarias XMLTV**| `XmltvTimeTest.java` | 5 | Superadas (100%) |
| **Servidor Remoto y Tokens** | `RemoteServerTest.java` | 2 | Superadas (100%) |
| **Actualizador Seguro OTA** | `UpdateManagerTest.java` | 7 | Superadas (100%) |

Comando de verificación ejecutado:
```powershell
cmd.exe /c "set JAVA_HOME=C:\Program Files\Android\Android Studio\jbr&& gradlew.bat testDebugUnitTest"
```

---

## Análisis Estático (Android Lint)

**`lintPreview`: 0 errores bloqueantes.**  
El informe confirma que no existen defectos críticos en la variante de distribución.

Comando de verificación:
```powershell
cmd.exe /c "set JAVA_HOME=C:\Program Files\Android\Android Studio\jbr&& gradlew.bat lintPreview"
```

---

## Verificación de Manifiesto y Componentes (`apkanalyzer`)

Ejecutado con `apkanalyzer manifest print`:
- **Permisos efectivos:** `INTERNET`, `ACCESS_NETWORK_STATE`, `REQUEST_INSTALL_PACKAGES`, `WAKE_LOCK`.
- **Componentes expuestos:** Únicamente `MainActivity` (con filtros `MAIN`, `LAUNCHER` y `LEANBACK_LAUNCHER`).
- **Componentes privados:** `PlayerActivity` (`exported="false"`), `GuideActivity` (`exported="false"`), `FileProvider` (`exported="false"`, `tv.lunvoryx.player.provider`).
- **Seguridad de datos:** `allowBackup="false"`, `dataExtractionRules` activadas.
