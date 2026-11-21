# Política de Seguridad (Security Policy)

## Versiones con soporte

| Versión | Con soporte de parches de seguridad |
| :--- | :--- |
| 1.0.x | Sí |
| < 1.0.0 | No |

## Reporte responsable de vulnerabilidades

La seguridad de Lunvoryx Player y la privacidad de los usuarios son una prioridad absoluta.

Si descubres una vulnerabilidad de seguridad en este proyecto:

1. **NO abras una incidencia (issue) pública** con los detalles técnicos de la vulnerabilidad ni con exploits funcionales.
2. Utiliza la función nativa de GitHub **Security Advisory** en la pestaña **Security** del repositorio (opción *Report a vulnerability*), o ponte en contacto en privado con el mantenedor del proyecto.
3. Incluye:
   - Descripción detallada del hallazgo.
   - Pasos reproducibles para verificar el problema.
   - Dispositivos o versiones de Android afectadas.
   - Evaluación de impacto (OWASP MASVS / CWE).
   - Posible mitigación o parche sugerido, si dispones de él.

## Principios de seguridad aplicados en Lunvoryx Player

- **Sin analíticas invasivas ni telemetría oculta:** La aplicación funciona directamente en el televisor sin enviar tu catálogo, listas o hábitos a servidores de terceros.
- **Emparejamiento QR seguro:** El servidor HTTP local para añadir listas desde el móvil implementa tokens criptográficos de emparejamiento aleatorio generados por sesión (`SecureRandom`), límites de tamaño de carga (25 MB) y apagado automático por inactividad.
- **Protección criptográfica OTA:** Las actualizaciones descargadas desde GitHub Releases verifican la identidad del paquete (`tv.lunvoryx.player`), versiones no degradables (anti-downgrade) y la firma digital estricta con el certificado de la aplicación instalada antes de solicitar la instalación al sistema operativo.
- **Sin secretos en el código:** Las claves de servicios opcionales (como TMDB) se configuran por el usuario directamente en la interfaz del televisor y se almacenan en almacenamiento privado protegido.
