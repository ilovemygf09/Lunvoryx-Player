# Política de Seguridad (Security Policy)

La seguridad de **Lunvoryx Player** y la privacidad de los usuarios son principios fundacionales del proyecto.

---

## Versiones con Soporte Activo

Solo las versiones oficiales publicadas reciben parches de seguridad y actualizaciones de dependencias:

| Versión | Estado de Soporte | Canal de Actualización |
| :--- | :---: | :--- |
| **1.0.x** | ✅ **Soporte Activo** | [GitHub Releases Oficiales](https://github.com/ilovemygf09/Lunvoryx-Player/releases) |
| `< 1.0.0` | ❌ Sin soporte | Se recomienda migrar a la versión 1.0.0 o superior |

---

## Reporte Responsable de Vulnerabilidades

Si descubres una posible vulnerabilidad de seguridad o defecto de protección de datos:

1. **NO publiques la incidencia en un Issue público de GitHub** ni compartas pruebas de concepto que puedan comprometer la seguridad de otros usuarios.
2. Utiliza la herramienta oficial de GitHub **Security Advisory**:
   * Dirígete a la pestaña **Security** del repositorio $\to$ **Advisories** $\to$ **Report a vulnerability** (o mediante [este enlace directo](https://github.com/ilovemygf09/Lunvoryx-Player/security/advisories/new)).
3. Proporciona toda la información necesaria para reproducir el fallo:
   * Descripción clara del impacto potencial (CWE / OWASP MASVS).
   * Pasos detallados para reproducir el escenario.
   * Versión de Android / Fire OS y dispositivo utilizado para la prueba.
   * Sugerencia de parche o remediación técnica si dispones de ella.

### Compromiso de Respuesta
* **Reconocimiento inicial:** En menos de 48 horas laborables.
* **Evaluación y remediación:** Publicación de parche prioritario y nueva Release de seguridad para distribución OTA.

---

## Principios de Arquitectura Segura en Lunvoryx Player

### 1. Privacidad Total y Cero Telemetría
* La aplicación no integra SDKs de analítica, seguimiento publicitario ni telemetría oculta.
* Las listas de reproducción, URLs de canales, credenciales de proveedores Xtream y el historial de visualización residen exclusivamente en el almacenamiento local privado del dispositivo (`Context.MODE_PRIVATE`).

### 2. Emparejamiento Local Seguro vía QR
* El servidor HTTP empotrado para la importación desde teléfonos móviles (`RemoteServer.java`) genera un token criptográfico efímero de 128 bits mediante `java.security.SecureRandom`.
* Toda petición desde el navegador requiere validar dicho token mediante la cabecera `X-Session-Token` o parámetro autenticado.
* Protección contra agotamiento de memoria con límite máximo de carga de 25 MB y temporizador de apagado automático por inactividad tras 15 minutos.

### 3. Blindaje Criptográfico de Actualizaciones OTA
* El módulo `UpdateManager.java` consulta endpoints seguros `https://` y valida que el paquete descargado corresponda estrictamente a `tv.lunvoryx.player`.
* **Verificación de Certificado Digital:** Antes de invocar el instalador del sistema operativo, se extraen y cotejan criptográficamente los certificados de firma del APK descargado contra la app instalada en el televisor.
* **Protección Anti-Downgrade:** El actualizador rechaza cualquier archivo con código de versión inferior o manipulado.

### 4. Defensas de Parseo XMLTV (EPG)
* El motor de análisis de guías de programación (`XmltvParser.java`) deshabilita explícitamente entidades externas (XXE) y previene ataques de expansión recursiva (*Billion Laughs / XML Bombs*), validando tamaños máximos antes de indexar.
