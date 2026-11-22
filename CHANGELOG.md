# Registro de Cambios (Changelog)

Todas las novedades, correcciones y mejoras notables de **Lunvoryx Player** se documentan en este archivo.  
El formato se basa en [Keep a Changelog](https://keepachangelog.com/es-ES/1.0.0/) y este proyecto sigue [Semantic Versioning](https://semver.org/lang/es/).

---

## [1.0.1] - 2026-11-22

### 🚀 Optimización de Importación y Soporte de Listas Masivas
- **Límite de listas ampliado a 250 MB:** Aumento drástico del umbral máximo de descarga e importación de listas M3U / M3U8 de 20 MB a **250 MB** en `PlaylistRepository.java`, permitiendo cargar catálogos completos con decenas de miles de películas y series VOD.
- **Capacidad ampliada a 150.000 canales:** `M3uParser.java` eleva el límite de elementos indexables de 50.000 a **150.000 canales/entradas**.
- **Servidor Web Móvil (QR) optimizado:** `RemoteServer.java` incrementa el límite de subida directa a 250 MB y actualiza la validación en el cliente web móvil.
- **Memoria de alto rendimiento (`largeHeap`):** Activación de `android:largeHeap="true"` en `AndroidManifest.xml` para garantizar suficiente memoria heap en la máquina virtual de Android TV durante el procesamiento de listas de gran tamaño.
- **Tiempos de espera ampliados:** Incremento de los timeouts de red y descarga a 180 segundos para prevenir desconexiones en conexiones Wi-Fi con listas pesadas.
- **Guía EPG optimizada:** Aumento del límite de descarga de guías XMLTV a 64 MB (180 MB descomprimido).

## [1.0.0] - 2026-11-21

Lanzamiento oficial inicial de Lunvoryx Player para Android TV, Google TV y Fire OS.

### ✨ Diseño y Experiencia de Usuario (Apple tvOS)
- **Identidad de Marca Oficial:** Nombre `tv.lunvoryx.player` con isotipo 3D y banners nativos para el lanzador de Android TV.
- **Integración Transparente del Logotipo:** Eliminación de recuadros o contenedores negros superpuestos en la barra lateral; extracción con transparencia real (*unmultiplied alpha*) y montaje directo sobre vidrio translúcido continuo (114 × 76 dp).
- **Sistema de Curvaturas Continuas ($G^2$ Squircles):**
  - Implementación de la escala de radios de Apple TV: 22 dp en barra lateral, 18 dp en paneles de contenido y columnas de navegación, 12 dp en pósters, cuadrículas y botones normales, 18 dp / 13 dp en campos de búsqueda y 14 dp en el selector activo del menú lateral.
- **Física de Materiales y Trazos Especulares:**
  - Trazo cenital de reposo calibrado con luz especular física superior (`#8D98B8` arriba, `#A2A7BA` lateral, `#637293` base).
  - Bisel óptico interior de profundidad de 20 dp (`0x14FFFFFF` $\to$ `0x00FFFFFF`).
  - Anillo de foco Apple TV en plata nácar (`0xFFFFFFFF` $\to$ `0xC4D2E0`) con halo difuso de sombra suave (`0x44FFFFFF`).
- **Navegación en 3 Columnas Optimizada para Mando (D-Pad):**
  - **TV En Vivo:** Categorías esenciales forzadas (*Todas*, *Favoritos*, *Seguir viendo*), lista de canales con barra de progreso EPG azul celeste en tiempo real y panel de vista previa con badge pulsante «● EN DIRECTO».
  - **Películas y Series:** Ficha cinematográfica con año, resolución 4K/HD, sinopsis y elenco.
  - **Buscar:** Barra squircle continua de 18 dp con historial interactivo, filtros superiores y panel de temporadas.
  - **Ajustes:** Tarjetas agrupadas con interruptores animados modernos `ModernSwitch` y selector dinámico de idioma (Español / Inglés).

### 📱 Importación Inalámbrica y Servidor Local (QR)
- **Servidor HTTP Local Empotrado (`RemoteServer.java`):** Escucha en puerto 8080 para recibir listas desde teléfonos móviles conectados a la misma red Wi-Fi sin depender de servicios en la nube.
- **Interfaz Web Móvil Responsive:** Página web con diseño glassmorphism, indicador de estado «● Reproductor detectado», conmutador de pestañas (*Xtream Codes*, *M3U URL*, *Archivo M3U*) y botón remoto para volver a la lista en TV.
- **Emparejamiento Criptográfico Seguro:** Tokens temporales de 128 bits generados con `SecureRandom`, límite máximo de carga de 25 MB y apagado automático por inactividad tras 15 minutos.

### ⚡ Motor Multimedia y EPG
- **Reproducción Nativa con AndroidX Media3 (ExoPlayer 1.11.1):** Soporte completo para flujos HLS (`.m3u8`), MPEG-DASH (`.mpd`) y transporte MPEG-TS (`.ts`).
- **Punto de Pausa Persistente:** Memorización automática de posición y progreso para contenidos bajo demanda (VOD).
- **Guía Electrónica de Programación (XMLTV):** Parseo de archivos directos o comprimidos (`.xml.gz`) con indexación cronológica.

### 🛡️ Seguridad y Actualizaciones Seguras (OTA)
- **Actualizador Criptográfico (`UpdateManager.java`):** Comprobación contra GitHub Releases oficiales (`https://api.github.com/repos/ilovemygf09/Lunvoryx-Player/releases/latest`).
- **Validación de Firma Digital:** Extracción y cotejo de certificados de firma del APK descargado contra la aplicación instalada antes de solicitar la instalación.
- **Protección Anti-Downgrade:** Bloqueo contra versiones inferiores o paquetes no legítimos.
- **Defensas contra Vulnerabilidades:** Protección contra inyecciones XXE y bombas XML en guías EPG, exclusión de claves en `.gitignore` y almacenamiento privado (`MODE_PRIVATE`).

### ⚙️ Arquitectura y Pruebas
- Suite completa de **46 pruebas unitarias JUnit** (100% aprobadas) cubriendo parseo M3U, clasificación de contenido, indexación EPG, servidor local y actualizador OTA.
- Flujo de integración continua (.github/workflows/build.yml) con Java 17, lint estático y empaquetado optimizado R8.
