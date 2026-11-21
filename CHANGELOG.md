# Registro de Cambios (Changelog)

Todas las novedades, correcciones y mejoras notables de **Lunvoryx Player** se documentan en este archivo.
El formato se basa en [Keep a Changelog](https://keepachangelog.com/es-ES/1.0.0/) y este proyecto sigue [Semantic Versioning](https://semver.org/lang/es/).

---

## [1.0.0] - 2026-11-21

### Añadido
- **Nueva identidad de marca:** Nombre oficial Lunvoryx Player (`tv.lunvoryx.player`) con isotipo 3D y banners oficiales para Android TV.
- **Integración Nativa del Logotipo:** Eliminación del recuadro negro en la barra lateral; extracción con transparencia real (*unmultiplied alpha*) y montaje directo sobre vidrio translúcido continuo (114 × 76 dp).
- **Sistema de Curvaturas Continuas tvOS ($G^2$ Squircles):**
  - Adopción estricta de la escala de radios de Apple TV: 22 dp en barra lateral, 18 dp en paneles y columnas, 12 dp en tarjetas, pósters y botones normales, 13 dp en campos de búsqueda y 14 dp en selectores activos.
- **Física de Materiales y Trazos Especulares Calibrados:**
  - Trazo cenital de reposo calibrado con la muestra visual de referencia (`#8D98B8` superior, `#A2A7BA` lateral, `#637293` base en inputs; `#2F354B` superior en tarjetas).
  - Bisel óptico interior de profundidad de 20 dp (`0x14FFFFFF` $\to$ `0x00FFFFFF`).
  - Anillo de foco Apple TV en plata-nácar (`0xFFFFFFFF` $\to$ `0xC4D2E0`) con halo ambiental difuso de sombra.
- **Servidor local para importación móvil vía QR (`RemoteServer.java`):**
  - Servidor HTTP multi-hilo en puerto local 8080.
  - Emparejamiento por token criptográfico temporal (`SecureRandom`) incrustado en el QR.
  - Interfaz web móvil responsive con diseño glassmorphism, pestañas para Xtream Codes, M3U URL y Archivo local.
  - Botón de retorno remoto hacia la pantalla de TV.
  - Protección de sobrecarga (límite de 25 MB) y apagado por inactividad tras 15 minutos.
- **Sistema de Actualizaciones Seguras OTA (`UpdateManager.java`):**
  - Comprobación automática y manual contra GitHub Releases oficiales mediante HTTPS.
  - Verificación criptográfica estricta de certificados de firma del APK descargado contra la app instalada.
  - Protección anti-downgrade y verificación del paquete legítimo `tv.lunvoryx.player`.
  - Diálogo de aviso al inicio con opciones: *Actualizar*, *No recordar en 24h* y *No actualizar*.
- **Pantalla de Ajustes Reestructurada:**
  - Tarjetas agrupadas en *General* y *Reproducción*.
  - Switches animados modernos `ModernSwitch` diseñados para control remoto.
  - Selector dinámico de idioma (Español / Inglés).
  - Configuración de API Key para TMDB con validación en vivo.
- **Navegación Cinematográfica en 3 Columnas:**
  - Sección TV En Vivo con categorías esenciales forzadas (*Todas*, *Favoritos*, *Seguir viendo*), barra de progreso EPG azul celeste y badge pulsante "● EN DIRECTO".
  - Secciones Películas y Series con categorías forzadas, grilla de pósters escalables y panel de información técnica (año, resolución 4K/HD, sinopsis y elenco).
  - Sección Buscar con selector superior, historial de búsquedas recientes interactivo y ficha previa.
- **Automatización CI/CD:** Flujo de trabajo en GitHub Actions para compilación, lint y 46 pruebas automatizadas en cada push/PR.

### Seguridad
- Permiso `REQUEST_INSTALL_PACKAGES` declarado formalmente en el manifiesto para Android 8.0+.
- Eliminación de endpoints inseguros en el servidor local; vinculación exclusiva a transferencias autorizadas por token.
- Verificación exhaustiva contra XXE (XML External Entities) y bombas de descompresión en guías XMLTV.
- Exclusión total de keystores, contraseñas, tokens y dumps de emulador del repositorio Git.
