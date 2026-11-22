# Registro de Cambios (Changelog)

Todas las novedades, correcciones y mejoras notables de **Lunvoryx Player** se documentan en este archivo.  
El formato se basa en [Keep a Changelog](https://keepachangelog.com/es-ES/1.0.0/) y este proyecto sigue [Semantic Versioning](https://semver.org/lang/es/).

---

## [1.2.1] - 2026-11-22

### ⚡ Optimización Integral y Carga Asíncrona sin Bloqueo de UI
- **Carga Asíncrona en Background en Series (`loadGroupedSeriesAsync`):** El procesamiento de series ahora se ejecuta íntegramente fuera del hilo de interfaz (UI Thread) en un hilo de trabajo dedicado con indicador de progreso animado (`ProgressBar`), eliminando congelaciones, bloqueos y diálogos de ANR al acceder a listas con decenas de miles de episodios.
- **Caché en Memoria de Alta Velocidad (`PlaylistRepository`):**
  - Instantáneas en memoria para canales en vivo, películas, series y series agrupadas (`peekGroupedSeries`).
  - Precomputación en segundo plano al importar o cargar la lista (`precomputeMediaCacheAsync`), haciendo que los cambios de pestaña sean **inmediatos (0 ms)**.
- **Aceleración por Hardware (`android:hardwareAccelerated="true"`):** Renderizado acelerado por GPU para todas las pantallas y animaciones, eliminando la lentitud y proporcionando 60 FPS estables en Android TV.
- **Optimización de Expresiones Regulares y Parser de Series (`SeriesParser`):**
  - Precompilación de todos los patrones de expresiones regulares y supresión de `String.replaceAll` con compilación repetitiva.
  - Caché concurrente de títulos (`TITLE_CACHE`): reduce de 80.000 operaciones de limpieza de texto a ~3.700 llamadas únicas (más de 20x de aceleración).
- **Optimización de Recycler Pools y Stable IDs:**
  - `setHasStableIds(true)` en `PosterAdapter` y `ChannelRowAdapter` para reutilización inmediata de vistas.
  - Tamaño de caché aumentado (`setItemViewCacheSize(24)`) y pool de reciclaje ampliado para navegación fluida con D-Pad.
- **Eliminación de lecturas redundantes de SharedPreferences:**
  - `isFavorite` y `hasRecent` ahora evalúan conjuntos `Set<String>` en memoria en lugar de deserializar JSON y SharedPreferences repetidamente para cada elemento.

---

## [1.2.0] - 2026-11-22

### 🎬 Agrupación de Series por Temporadas y Ficha Interactiva de Capítulos
- **1 Carátula por Serie (`SeriesParser` y `SeriesItem`):** Fin a la explosión de decenas de miles de carátulas en listas grandes. Todos los capítulos de una serie (`S01E01`, `1x01`, `T1 Cap. 1`, etc.) se indexan y consolidan automáticamente bajo una única carátula con título limpio, año y contador de temporadas (`X temps.`).
- **Contador real de series únicas:** En listas masivas con ~80.000 episodios, el catálogo muestra con precisión el número de series reales (~3.700 series).
- **Ficha de Serie Dinámica por Temporadas (`MediaDetailView`):**
  - Selector interactivo de temporadas en cápsulas (`T1`, `T2`, `T3`...).
  - Listado de episodios por temporada con miniatura, número, título de capítulo, duración y botón `(▶)` de reproducción directa del stream específico.
  - Botón de tráiler oficial en YouTube, reparto con avatares, valoración estelar y especificaciones de calidad 4K HDR.

### 📺 Clasificación Estricta de TV en Vivo vs. Películas
- **Eliminación de canales lineales en Películas:** Canales con temáticas de cine (p. ej. `M+ Cine`, `EU- ES M CINE`, `ES | Cine`) o flujos lineales (`.m3u8`, `.ts`) con indicadores de directo se clasifican exclusivamente como **TV en vivo** y nunca contaminan el catálogo VOD de Películas.
- **Detección refinada en `MediaKind`:** Reglas avanzadas para separar canales 24/7 y emisiones deportivas de películas y series VOD auténticas.

### 🎨 Ajustes de Diseño y Navegación
- **Alineación exacta del logotipo:** El logo de Lunvoryx Player se fija en el margen superior izquierdo exacto de la pantalla (`64 dp`).
- **Píldora de caducidad completa:** Ajuste de anchura y padding para visualizar completamente *"Tu lista caduca: DD/MM/AAAA"* sin cortes.
- **Acción de recarga de catálogo:** El botón *Recargar lista* del mosaico de inicio fuerza la sincronización y actualización inmediata de la lista activa.

---

## [1.1.1] - 2026-11-22

### 🐛 Correcciones en Modo Intuitivo (Layout y Navegación D-Pad)
- **Corrección de restricciones en barra superior (`IntuitiveTopBar`):** Asignación explícita de `LayoutParams` en los separadores visuales y altura fija de cabecera (`58 dp`). Se elimina el fallo por el cual los divisores se expandían verticalmente empujando la cuadrícula de inicio fuera de pantalla.
- **Foco inicial inmediato para mando a distancia (D-Pad):** Autoenfoque garantizado en la tarjeta destacada de *TV en vivo* al abrir el Inicio y en la primera fila de *Ajustes*, resolviendo la falta de respuesta del mando a distancia.
- **Adaptación panorámica del fondo (`HomeBackdrop`):** En Modo Intuitivo el fondo ahora se proyecta al 100% del ancho de pantalla sin el recorte lateral del 15% reservado para la barra de navegación clásica.
- **Persistencia garantizada de la interfaz:** La selección del Modo Intuitivo permanece fijada entre reinicios de la aplicación hasta que se desactive deliberadamente en Ajustes.
- **Indicador dinámico de versión:** El pie de pantalla del inicio intuitivo refleja automáticamente la versión real instalada (`v1.1.1`).

---

## [1.1.0] - 2026-11-22

### ✨ Modo Intuitivo (Rediseño Estético y Visual Completo)
- **Interruptor de Modo Intuitivo en Ajustes:** Nueva opción en la sección General que transforma totalmente la distribución, estética, colores e interactividad del reproductor.
- **Barra de Navegación Superior (`IntuitiveTopBar`):** Cabecera horizontal completa con logotipo integrado, selector de secciones en cápsulas con acento azul (`#007AFF`) para *Inicio*, *TV en vivo*, *Películas* y *Series*, barra de búsqueda rápida y distintivo de estado/caducidad de lista (*"Tu lista caduca: 06/05/2027"*).
- **Mosaico Dinámico de Inicio (`IntuitiveHomeScreen`):** Tarjeta panorámica destacada de *TV en vivo* con distintivo `● EN DIRECTO` y fondo de estadio deportivo; columna central con *Películas* y acceso directo a *Recargar lista*; columna derecha con *Series* y acceso directo a *Ajustes*.
- **Catálogo de Películas y Series en 6 Columnas (`MediaBrowseView`):** Cuadrícula expandida a 6 columnas con carátulas verticales, halo de foco cyan brillante (`#00BFFF`), búsqueda instantánea y ordenación por categoría.
- **Fichas Cinemáticas de Contenido (`MediaDetailView`):** Al seleccionar cualquier carátula de película o serie se abre una ficha completa con:
  - Fondo hero cinemático con degradado oscuro de legibilidad y botón de retorno `(←)`.
  - Título, año, duración o temporadas, género e insignias de calidad *4K* y edad *16+*.
  - Sinopsis argumental en alta definición.
  - Botones interactivos de acción: *Reproducir*, *Ver tráiler* (con icono de YouTube) y *+ Mi lista* (favoritos).
  - **Integración de Tráiler en YouTube:** Apertura automática del tráiler oficial en YouTube en el idioma del reproductor (español por defecto) o búsqueda inteligente mediante intent nativo.
  - **Reparto y Valoración:** Retratos circulares de actores con puntuación por estrellas (`8,4 / 10` y `8,7 / 10`).
  - **Selector de Temporadas y Episodios:** Navegación por temporadas (`T1`, `T2`, `T3`) y lista deslizable de episodios con miniatura, descripción, duración y botón de reproducción directa por capítulo.
  - **Ficha Técnica Detallada:** Información de director, audio multicanal, subtítulos y especificaciones de calidad (*4K UHD · HDR*).
- **Compatibilidad Total:** El modo clásico con menú lateral estilo Apple tvOS se mantiene intacto y conmutable en cualquier momento desde Ajustes.

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
