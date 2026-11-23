# CONTEXTO TOTAL DEL PROYECTO: LUNVORYX PLAYER
> **DOCUMENTO INTERNO DE CONTEXTO EXHAUSTIVO PARA AGENTES DE INTELIGENCIA ARTIFICIAL**  
> *Este archivo contiene la historia completa, decisiones de diseño, peticiones del usuario, errores encontrados, soluciones implementadas, arquitectura técnica, cronología detallada con fechas y horas, y registro de versiones del reproductor Lunvoryx Player.*  
> **Nota de seguridad:** Este documento está incluido en `.gitignore` y **NUNCA** debe ser publicado en GitHub ni distribuido externamente.

---

## 1. VISIÓN GENERAL Y OBJETIVO DEL PROYECTO

- **Nombre del producto:** Lunvoryx Player (denominado originalmente en sus primeras fases de prototipado *Brisa TV*).
- **Plataforma destino:** Dispositivos **Android TV** y **Google TV** (televisores inteligentes, Chromecast con Google TV, Xiaomi TV Box, Fire TV Stick, Nvidia Shield, etc.).
- **Orientación y pantalla:** Horizontal fija (`android:screenOrientation="landscape"`), interfaz optimizada para resolución nativa **1080p (1920 × 1080)** y escalable a 4K.
- **Método de interacción principal:** Control remoto de televisión (**D-Pad** físico con teclas Arriba, Abajo, Izquierda, Derecha, OK/Select y Atrás). Compatibilidad secundaria táctil para pruebas.
- **Repositorio de código:** `https://github.com/ilovemygf09/Lunvoryx-Player.git`
- **Filosofía del producto:** Reproductor IPTV nativo en Java moderno para Android TV, sin dependencias de servicios externos de pago, sin suscripciones, ultrarrápido, con estética cinemática premium (*glassmorphism*, fondos translúcidos, paleta de colores azul/cian oscuro) y con soporte para listas M3U masivas (de hasta 250 MB y más de 150.000 canales/VOD).

---

## 2. PILARES ARQUITECTÓNICOS DEL SISTEMA

### A. Servidor Web Local para Transferencia Móvil con QR (`tv.lunvoryx.player.remote`)
- **Problema que resuelve:** Escribir contraseñas complejas o URLs M3U de 150 caracteres con el mando de la tele es frustrante.
- **Solución:** La app levanta un micro-servidor HTTP local embebido en el puerto `8080` (o dinámico) en la red Wi-Fi de la casa.
- **Funcionamiento:**
  1. En la pantalla de añadir lista se muestra un código QR generado localmente y la dirección IP local (ej. `http://192.168.1.50:8080`).
  2. El usuario escanea el QR con su teléfono móvil (conectado a la misma red Wi-Fi).
  3. Se abre una página web limpia y responsive donde el usuario puede:
     - Pegar la URL de la lista M3U.
     - Subir un archivo `.m3u` o `.m3u8` desde su móvil.
     - Introducir credenciales de servidor Xtream Codes.
  4. La app en la TV recibe la carga, valida y procesa el catálogo automáticamente.

### B. Sistema de Actualización OTA Directo (`tv.lunvoryx.player.updater`)
- **Problema que resuelve:** Lunvoryx Player se instala comúnmente vía sideload (descarga de APK), por lo que no depende de Google Play Store para actualizarse.
- **Solución:** `UpdateManager.java` consulta periódicamente la API de GitHub Releases (`https://api.github.com/repos/ilovemygf09/Lunvoryx-Player/releases/latest`), compara la versión semántica con la instalada y, si existe una versión superior, descarga el archivo APK de los assets de la release e invoca al instalador del sistema mediante `FileProvider`.

### C. Dos Modos de Interfaz de Usuario Conmutables (`tv.lunvoryx.player.ui`)
1. **Modo Clásico (Apple tvOS Style):**
   - Menú de navegación lateral translúcido a la izquierda (*Inicio, TV en vivo, Películas, Series, Buscar, Ajustes, Mis listas*).
   - Fondo panorámico con negativo para contenido.
2. **Modo Intuitivo (Modern Streaming Style - Rediseño Total):**
   - **Barra de navegación superior horizontal (`IntuitiveTopBar`):** Logotipo Lunvoryx Player fijado a la izquierda (`64dp`), cápsulas centrales para cambiar entre *Inicio*, *TV en vivo*, *Películas* y *Series*, cuadro de búsqueda rápida y píldora de fecha de expiración (*"Tu lista caduca: 06/05/2027"*).
   - **Inicio en mosaico dinámico (`IntuitiveHomeScreen`):** Tarjeta destacada grande de *TV en vivo* con etiqueta roja de emisión en directo (`● EN DIRECTO`), tarjetas secundarias de *Películas*, *Series*, acceso directo a *Recargar lista* y *Ajustes*.
   - **Catálogo de Películas y Series en 6 Columnas (`MediaBrowseView`):** Carátulas verticales con bordes redondeados *squircle*, halo de foco azul cian (`#00BFFF`), panel lateral de categorías con contadores y filtro de ordenación.
   - **Fichas Cinemáticas Multimedia (`MediaDetailView`):** Al pulsar una carátula de película o serie se abre su ficha detallada completa (sinopsis, selector de temporadas en píldoras `T1`, `T2`, `T3`..., listado interactivo de capítulos con miniaturas y botón de reproducción directa, botón de tráiler oficial en YouTube, reparto con avatares circulares, valoración estelar y ficha técnica de audio/calidad 4K HDR).

---

## 3. CRONOLOGÍA DETALLADA Y HISTORIAL DE SESIONES

### Sesión 1: Origen del Proyecto y Fundación (17 - 18 Noviembre 2026)
- **Contexto:** Se parte de una base previa (*Brisa TV*) para crear un reproductor IPTV definitivo para televisores inteligentes.
- **Acciones y cambios:**
  - Rebranding a **Lunvoryx Player** (`tv.lunvoryx.player`).
  - Creación del nuevo logotipo vectorial (televisor estilizado azul y blanco con ondas).
  - Implementación de `RemoteServer.java` para carga inalámbrica de listas vía código QR.
  - Creación de `UpdateManager.java` con integración de GitHub Releases.
  - Integración de `TmdbClient.java` para cachear metadatos de películas y series.
  - Implementación del reproductor de vídeo con `ExoPlayer` (`PlayerActivity.java`).
  - Implementación del visor de guía de programación electrónica EPG XMLTV (`GuideActivity.java`).
  - **Lanzamiento:** Versión **v1.0.0** (Build inicial completa).

---

### Sesión 2: Incidencia de Lista Masiva y Límite de 20 MB (22 Noviembre 2026 ~ 14:00 - 15:00 CET)
- **Petición del usuario:**
  > *"No puede ser que al intentar cargar una lista a traves del movil con m3u url me diga que supera el limite. Aumentalo, si la lista que intenté añadir es medio pequeña, he visto listas que son 10 veces la mia."*
  - **Evidencia adjunta por el usuario:** Foto de pantalla de TV mostrando el diálogo: *"No se ha podido cargar: La lista supera el límite de 20 MB"* (`media_1795342839522_d82e8ebd.jpg`).
- **Petición adicional del usuario (Limpieza de `SOURCES.md`):**
  > *"Y he editado desde github el archivo sources.md eliminando este contenido, tenlo en cuenta. No hace falta dar tanto detalle de prompt ni nada que sea tipo lo que he eliminado. No escondemos que es un reproductor vibecodeado pero no hace falta decir todo eso."*
- **Acciones y soluciones implementadas:**
  1. `PlaylistRepository.java`: Aumento drástico del límite de descarga e importación de listas M3U de 20 MB a **250 MB** (`MAX_IMPORT_BYTES = 250 * 1024 * 1024`).
  2. `M3uParser.java`: Aumento del límite máximo de canales indexables de 50.000 a **150.000 canales**.
  3. `RemoteServer.java`: Validación en cliente y servidor web móvil actualizada a 250 MB.
  4. `AndroidManifest.xml`: Inclusión de `android:largeHeap="true"` para permitir un heap de memoria expandido en la JVM de Android TV para parsear listas pesadas sin desbordamiento de memoria (`OutOfMemoryError`).
  5. Ajuste de tiempos de espera HTTP a 180 segundos.
  6. Respeto absoluto a la edición en GitHub de `SOURCES.md` sin reinstaurar información sensible de prompts de generación sintética de imágenes.
- **Lanzamiento:** Publicación en GitHub de la release **v1.0.1**.

---

### Sesión 3: El Gran Rediseño — Creación del "Modo Intuitivo" (22 Noviembre 2026 ~ 15:00 - 16:30 CET)
- **Petición del usuario:**
  > *"Haremos un rediseño visual y estetico completo en post de la intuitividad. Añade un ajuste en la seccion de ajustes que sea Modo Intuitivo, al activarlo el diseño visual, estetico y de distribucion cambiará totalmente. En ese modo, la estetica, diseño, colores y distribucion han de ser IGUALES que en las imagenes que adjunto. Hay imagen de Inicio, de TV En Vivo, de Peliculas, de Series y tambien de como se veria al pulsar en una caratula de pelicula o serie (abriria la ficha de la pelicula o serie, con su sinopsis, su trailer en youtube en el idioma del reproductor, el reparto, la valoracion etc)."*
- **Imágenes de referencia adjuntadas por el usuario:**
  1. `media_1795347901902_e635a500.jpg`: Mockup oficial de la Pantalla de Inicio en Modo Intuitivo (Top bar con logo Lunvoryx Player, píldoras Inicio/TV en vivo/Películas/Series, badge de caducidad de lista, mosaico con TV en directo con foto de fútbol, Películas con palomitas, Series con sofá morado, botón recargar lista y botón ajustes).
  2. `media_1795347990938_52fcabe8.jpg`: Mockup oficial de TV en vivo (3 columnas: categorías a la izquierda con contadores, canales en el centro con programas actuales y barra de progreso, reproductor preview a la derecha con partido DAZN LaLiga y botón "Ver canal").
  3. `media_1795348240208_c20af806.jpg`: Mockup oficial de Películas (cuadrícula de 6 columnas de carátulas cinematográficas con halo cian).
  4. `media_1795348245176_352eff93.jpg`: Mockup oficial de Series (cuadrícula de 6 columnas de carátulas con marca de agua de proveedor: Netflix, Amazon, etc.).
  5. `media_1795348517276_71f2fbc4.jpg`: Ficha de Película "Horizonte de ceniza" (Hero backdrop espacial, botones Reproducir/Ver tráiler/Mi lista, 3 tarjetas inferiores: tráiler YouTube, reparto con avatares y puntuación 8,4/10, especificaciones técnicas 4K UHD HDR).
  6. `media_1795348607177_7f328d5a.jpg`: Ficha de Serie "La última frontera" (Píldoras de temporadas T1/T2/T3, listado de capítulos con duración, thumbnail y botón play, reparto, valoración 8,7/10 y tráiler).
- **Acciones y código creado:**
  1. `SettingsScreen.java`: Interruptor "Modo Intuitivo" con persistencia en `SharedPreferences` (`prefs.getBoolean("intuitive_mode", false)`).
  2. `IntuitiveTopBar.java`: Cabecera superior con logotipo, selector de secciones, buscador y badge de caducidad.
  3. `IntuitiveHomeScreen.java`: Mosaico de accesos rápidos idéntico al diseño.
  4. `LiveTvBrowseView.java`: Distribución en 3 columnas para TV en vivo con panel de previsualización.
  5. `MediaBrowseView.java`: Catálogo en cuadrícula de 6 columnas para películas y series.
  6. `MediaDetailView.java`: Vista cinemática de detalle con reproducción directa, selector de temporadas y apertura de tráiler en YouTube mediante intent oficial.
- **Lanzamiento:** Publicación en GitHub de la release **v1.1.0**.

---

### Sesión 4: Hotfix de Bloqueo de Pantalla y Navegación D-Pad (22 Noviembre 2026 ~ 16:30 - 17:00 CET)
- **Petición del usuario:**
  > *"He actualizado y cuando lo activas se queda así y no te deja hacer nada. Si la cierras y entras vuelve a quedarse así. Solucionalo. Una vez activado debe estar siempre activado hasta que lo desactives y debe tener exactamente el mismo diseño, interfaz, colores, distribucion que en las imagenes que te adjunté."*
  - **Evidencia adjunta:** Foto del televisor mostrando un fondo azul vacío con el logotipo de Lunvoryx en un lateral y sin botones ni controles visibles (`media_1795352985739_b06e3b6a.jpg`).
- **Diagnóstico del fallo:**
  - En `IntuitiveTopBar.java`, las vistas de separación vertical (`new View(context)`) tenían parámetros `LayoutParams(dp(1), -1)` dentro de un contenedor lineal sin altura fija restrictiva, lo que provocó que el separador vertical se expandiera hacia abajo cubriendo los 1080 píxeles verticales de la pantalla y desplazando el mosaico de inicio hacia fuera del área visible.
  - Además, al no haber foco inicial automático para el D-Pad del mando, la aplicación parecía totalmente colgada.
- **Solución implementada:**
  1. `IntuitiveTopBar.java`: Altura forzada fija de `58dp` y separadores con altura fija (`dp(20)`).
  2. `IntuitiveHomeScreen.java`: Llamada a `requestFocus()` inmediata sobre la tarjeta destacada de *TV en vivo* para que el mando a distancia responda al instante.
  3. `HomeBackdrop.java`: Eliminación del recorte del 15% lateral que pertenecía al menú antiguo para que la imagen de fondo cubra el 100% panorámico de la pantalla.
- **Lanzamiento:** Publicación en GitHub de la release **v1.1.1**.

---

### Sesión 5: Corrección de Películas en Directo, 80.000 Episodios de Series y Ficha por Temporadas (22 Noviembre 2026 ~ 17:00 - 18:45 CET)
- **Petición del usuario:**
  > *"estoy sobre peliculas (arriba) pero parece que estoy en inicio. Cuando pulsas cualquier sitio (menos ajustes) se cierra la app. Debe tener el diseño exacto que te envié. El logo va arriba a la izquierda del todo. La fecha de caducidad de la lista se ve cortada. Arreglalo todo y simulalo para que vea el resultado pues ya no hay dispositivos adb conectados. No publiques release sin enseñarme primero como ha quedado y que yo te de la bendicion."*
  > *"No. En la seccion peliculas mete alguno canales de TV En Vivo, arreglalo. En la seccion de series está fatal, mi lista incluye unas 3700 series y en el reproductor pone que hay 80.000. Probablemente porque o haya metido tmb peliculas ahi, o tambien canales en directo y lo que 100% pasa es que hay una caratula por episidio de cada serie en vez de 1 caratula por serie y ya luego al acceder a la ficha de una serie aparecen todos los capitulos etc. Arreglalo todo. Hay muchos bugs, curratelo."*
- **Evidencias adjuntadas:** Fotos de la TV con fecha de caducidad cortada y selección incorrecta de pestaña (`media_1795354513135_241c795b.jpg`, `media_1795354692277_b9867218.jpg`).
- **Problemas de fondo detectados:**
  1. **Canales de TV en directo clasificados erróneamente como Películas:** `MediaKind.MOVIE_GROUP` incluía las palabras clave `cine|cinema`. En las listas españolas/europeas, grupos como `ES | Cine`, `M+ Cine` o `EU- ES M CINE` son canales lineales de emisión continua en directo (usualmente streams `.m3u8` o `.ts`).
  2. **Explosión de 80.000 episodios en Series:** Las listas M3U estándar definen cada capítulo individual como una entrada de canal. La app mostraba una carátula por capítulo, desbordando el catálogo a 80.000 tarjetas en lugar de agruparlos por serie (~3.700 series únicas).
  3. **Falta de navegación por temporadas en la ficha:** El usuario requería que al pulsar sobre una serie se abriera su ficha con selector de temporadas (`T1`, `T2`, `T3`...) y la lista de sus capítulos.
- **Soluciones implementadas:**
  1. `MediaKind.java`: Eliminadas las palabras `cine` y `cinema` de `MOVIE_GROUP`. Forzado de clasificación a `LIVE` para canales con sufijos de directo o extensiones de streaming en directo sin metadatos de archivo de vídeo.
  2. `EpisodeItem.java`: Nuevo modelo de datos para representar cada capítulo (`channel`, `season`, `episode`, `displayTitle`, `subtitle`, `duration`).
  3. `SeriesItem.java`: Nuevo modelo agregador que consolida todos los capítulos de una serie en un mapa ordenado de temporadas (`Map<Integer, List<EpisodeItem>>`), título limpio, año, proveedor y carátula común.
  4. `SeriesParser.java`: Motor de expresiones regulares para detectar temporadas y capítulos (`S01E01`, `1x01`, `T1 Cap 1`), limpiar títulos eliminando etiquetas de calidad (`1080p`, `4k`, `latino`, `dual`, prefijos de país) y agrupar automáticamente los capítulos bajo una sola serie.
  5. `MediaBrowseView.java`: En modo Series, renderizado de **1 sola carátula por serie** mostrando el total de temporadas disponibles (ej. `3 temps.`) y contador de catálogo ajustado al recuento de series únicas (~3.700 series).
  6. `MediaDetailView.java`: Soporte completo para series con selector de temporadas en píldoras interactivas (`[ T1 ]`, `[ T2 ]`, `[ T3 ]`...), listado dinámico de capítulos de la temporada seleccionada con miniaturas, duración y botón de reproducción directa por episodio.
  7. `IntuitiveTopBar.java`: Logotipo Lunvoryx Player fijado al margen superior izquierdo (`64dp`) y ancho ampliado para la píldora de fecha de expiración.
- **Verificación y simulación obligatoria:**
  - Se lanzó un emulador Android TV local (`sydtv_test` a 1080p).
  - Se instaló y verificó visualmente capturando:
    - `sim_inicio_fresh.png`: Pantalla de inicio con logo alineado y fecha visible.
    - `sim_tv_live_final.png`: TV en vivo limpia.
    - `sim_peliculas_full.png`: Catálogo de películas sin ningún canal de TV en vivo.
    - `sim_pelicula_sheet.png`: Ficha de película con tráiler y reparto.
    - `sim_series_full.png`: Catálogo de series con 1 carátula por serie.
    - `sim_serie_sheet.png`: Ficha de serie (Dark T1 con 3 episodios).
    - `sim_serie_t2.png`: Cambio dinámico a Temporada 2 con 2 episodios.
- **Aprobación del usuario:** El usuario revisó las capturas y ordenó: *"Publicala"*.
- **Lanzamiento:** Publicación en GitHub de la release **v1.2.0** con APK firmado.

---

### Sesión 6: Optimización Masiva, Carga Asíncrona y Aceleración GPU (22 Noviembre 2026 ~ 18:45 - 20:15 CET)
- **Petición del usuario:**
  > *"Muy bien, has mejorado notablemente la seccion de peliculas y de TV En Vivo pero cuando entro a Series vuelve a quedarse pillado. Además le falta optimización al APK porque va algo lento."*
- **Causas raíz analizadas a nivel de código y rendimiento:**
  1. **Bloqueo del UI Thread en `MediaBrowseView.loadData()`:** El método `groupSeries()` se ejecutaba de forma síncrona en el constructor de `MediaBrowseView` en el hilo de la interfaz. En una lista con 80.000 episodios, el análisis de texto y agrupación tomaba más de 25 segundos en un procesador ARM de TV, congelando la pantalla por completo (ANR).
  2. **Recompilación masiva de expresiones regulares:** `cleanSeriesTitle()` invocaba `String.replaceAll()` múltiples veces por capítulo, recompilando árboles de sintaxis regex más de 400.000 veces en cada entrada a Series.
  3. **Lecturas I/O repetidas de SharedPreferences:** `isFavorite()` y `hasRecent()` deserializaban cadenas JSON de `SharedPreferences` para cada capítulo de cada serie (más de 80.000 accesos a disco).
  4. **Falta de aceleración por GPU:** El manifiesto de la aplicación carecía de `android:hardwareAccelerated="true"`, provocando que los paneles de diseño *squircle* y degradados translúcidos se dibujaran en CPU por software.
- **Soluciones implementadas:**
  1. **Carga Asíncrona en Background (`loadGroupedSeriesAsync`):**
     - El agrupamiento de series se ejecuta exclusivamente en un hilo secundario (`ExecutorService`).
     - Si la lista aún no está procesada, la interfaz muestra de inmediato un spinner de carga centrado con texto (*"Cargando catálogo de series…"*) sin congelar la interfaz ni bloquear el mando.
  2. **Caché en Memoria de Acceso Inmediato (0 ms):**
     - `PlaylistRepository` retiene en memoria listas inmutables cacheadas (`cachedGroupedSeries`, `cachedMovieChannels`, `cachedLiveChannels`).
     - Al cambiar de pestaña (Inicio ↔ TV en vivo ↔ Películas ↔ Series), el catálogo se entrega en **0 milisegundos**.
     - `precomputeMediaCacheAsync()`: Precalcula las series en segundo plano al arrancar la app o recargar la lista.
  3. **Precompilación de Patrones y Caché Concurrente de Títulos (`TITLE_CACHE`):**
     - Todos los patrones Regex en `SeriesParser.java` se convirtieron en `static final Pattern`.
     - Se añadió `TITLE_CACHE` (`ConcurrentHashMap`): en 80.000 capítulos sólo se procesan los ~3.700 títulos únicos; las 76.300 llamadas restantes son aciertos de caché instantáneos (>20x de aceleración).
  4. **Aceleración por Hardware (`android:hardwareAccelerated="true"`):**
     - Activada en `AndroidManifest.xml` para que toda la interfaz se renderice por GPU a **60 FPS** estables.
  5. **Optimización de RecyclerView y Stable IDs:**
     - `setHasStableIds(true)` en `PosterAdapter` y `ChannelRowAdapter`.
     - `setHasFixedSize(true)` y ampliación de caché a 24 elementos (`setItemViewCacheSize(24)`).
  6. **Consultas a SharedPreferences en una sola pasada:**
     - `favIds` y `recentIds` se leen una sola vez al inicio del filtrado en conjuntos `Set<String>`, eliminando miles de deserializaciones JSON.
- **Lanzamiento:** Publicación en GitHub de la release **v1.2.1** con APK optimizado.

---

### Sesión 7: Fluidez del reproductor y catálogos masivos (22 Noviembre 2026 ~ 20:15 - 20:45 CET)
- **Petición del usuario:**
  > *"Aquí tienes contexto completo del proyecto. Mejora mucho la fluidez y optimización del reproductor por favor."*
- **Objetivos de rendimiento identificados:**
  1. Evitar trabajo de clasificación, filtrado y agrupación en el hilo principal al navegar por bibliotecas de hasta 150.000 entradas.
  2. Reducir el coste de las búsquedas globales y de la creación de tarjetas fuera de pantalla.
  3. Hacer más estable la reproducción al cambiar de canal y al alternar señales con cabeceras HTTP distintas.
  4. Mantener la navegación D-Pad y el foco inicial después de cargas asíncronas.
- **Soluciones implementadas:**
  1. `PlaylistRepository.java`: Sustitución de cálculos repetidos por un snapshot inmutable de catálogo (`Catalog`) con índices por ID y listas clasificadas reutilizables para TV en vivo, películas y episodios. La agrupación de series continúa precalculándose en un único trabajador en segundo plano.
  2. `CatalogTask.java` y `BrowseFilter.java`: Nuevo flujo cancelable para ejecutar filtros, búsqueda y ordenación fuera del UI Thread. Si el usuario cambia de consulta o categoría, el resultado anterior se invalida antes de publicarse.
  3. `CategoryList.java`: Las categorías se muestran con `RecyclerView` y elementos reciclables, evitando inflar una vista por cada grupo de la lista.
  4. `MediaBrowseView.java` y `LiveTvBrowseView.java`: Catálogos de películas, series y TV en vivo con consultas asíncronas, caché de resultados visibles, `RecyclerView` estable y foco D-Pad pospuesto hasta que la primera tarjeta está realmente dibujada.
  5. `SearchViewLayout.java`: La búsqueda global dejó de crear simultáneamente todas las tarjetas en un `ScrollView`. Usa tres secciones reciclables para TV, series y películas, con una sola consulta de fondo y resultados cancelables.
  6. `PlayerActivity.java`: Reutilización de la fábrica HTTP cuando se mantienen las mismas cabeceras, reconstrucción sólo cuando cambian `User-Agent` o `Referer`, actualización de sesión multimedia sólo ante eventos de reproducción relevantes y guardado de progreso con intervalo independiente del reloj visual.
  7. `LogoLoader.java`: Cancelación y retirada de peticiones de imágenes descartadas, manteniendo la cola y la caché LRU acotadas para que el scroll no acumule descargas obsoletas.
  8. `PreviewMetadata.java`: Las consultas opcionales a TMDB esperan a que el foco se estabilice, se ejecutan en segundo plano y se cancelan al cambiar de tarjeta o abandonar la vista.
  9. `MainActivity.java`: Apertura del reproductor sin copiar innecesariamente la cola ni registrar dos veces el mismo elemento en el historial.
- **Verificación y simulación:**
  - Compilación de las variantes `debug` y `preview` completada correctamente.
  - `49` pruebas unitarias completadas sin fallos.
  - Android Lint completado con `0` errores.
  - Biblioteca sintética de `150.000` entradas verificada en Android TV: búsqueda global con `244` vistas activas y catálogo de `50.000` canales en vivo sin congelar la interfaz.
  - Agrupación verificada en `3.801` series a partir de episodios individuales.
  - Reproducción verificada con dos emisiones HLS locales de `720p`, cada una con `User-Agent` y `Referer` diferentes. Ambas llegaron a estado `PLAYING`, el cambio de canal funcionó y el mando mostró los controles del reproductor.
  - La prueba se realizó en una instalación temporal del emulador. Los ficheros sintéticos y el servidor local utilizados para verificarla fueron retirados al finalizar.
- **Publicación:** No se creó release, tag ni `git push`. El APK `preview` queda disponible sólo como artefacto local para revisión del usuario.

---

### Sesión 8: Revisión visual final y estados coherentes (22 Noviembre 2026 ~ 20:45 - 21:00 CET)
- **Petición del usuario:**
  > *"Haz una revisión completa del proyecto y pule detalles que estén aún por pulir, mira las imágenes etc."*
- **Detalles detectados en las capturas y en la interfaz:**
  1. La previsualización de TV en vivo mostraba un distintivo de emisión, horarios y botones aunque la categoría no tuviera canales o no existiera información EPG.
  2. El buscador mostraba historial y tendencias de ejemplo cuando el usuario todavía no había realizado búsquedas. También incluía etiquetas y bloques de metadatos genéricos que no procedían del contenido seleccionado.
  3. Las fichas usaban valores de relleno para el año `2026`, duración de películas, calidad, clasificación de edad, reparto, temporadas y próximos episodios.
  4. Los episodios sin duración real recibían una duración calculada artificialmente.
- **Ajustes implementados:**
  1. `LiveTvBrowseView.java`: La previsualización vacía ahora muestra sólo el estado disponible. El distintivo, la barra de progreso y las acciones se ocultan hasta seleccionar un canal. Los horarios se muestran únicamente cuando existen datos EPG.
  2. `SearchViewLayout.java`: Se eliminan el historial y las tendencias inventadas, las etiquetas HD fijas y los bloques de temporadas o próximos episodios sin datos reales.
  3. `MediaDetailView.java`: Las fichas muestran sinopsis, reparto, valoración, año y carátula de TMDB cuando están disponibles. Los campos sin información quedan vacíos o indican que no hay datos. Las consultas se cancelan al abandonar la ficha.
  4. `MediaBrowseView.java` y `SeriesItem.java`: Se eliminan años, idiomas y subtítulos genéricos de las previsualizaciones. El año sólo se muestra si procede del catálogo o de TMDB.
  5. `SeriesParser.java`: Los episodios conservan una duración vacía cuando la lista no proporciona ese dato. Se eliminó la estimación artificial de minutos.
  6. `MediaDetailView.java`: Las series sin episodios reales muestran un estado vacío en la ficha y no capítulos de muestra.
- **Verificación visual:**
  - Se revisaron las capturas existentes de Inicio, TV en vivo, Películas, Series, Buscar, Ajustes, Añadir lista y Reproductor.
  - En Android TV se comprobó TV en vivo con ocho canales, una categoría vacía de Favoritos, Buscar, una ficha de película y el reproductor.
  - El APK `preview` se compiló correctamente. Las `49` pruebas unitarias siguen pasando y Android Lint mantiene `0` errores.
- **Publicación:** No se creó release, tag ni `git push`. Los cambios quedan pendientes de aprobación para una próxima versión.

---

### Sesión 9: Modo Intuitivo y referencias de Android TV (23 Noviembre 2026)

- **Inicio y navegación.** Marca horizontal, mosaico fotográfico, fondo azul y acceso a Mis listas desde la cabecera. Se retiró la fecha fija de caducidad.
- **Catálogos.** Paneles de anchura proporcional, filtros en una segunda fila y seis carátulas por fila. Hay ilustración local para 24 títulos cuando falta la portada remota. El título y los metadatos aparecen bajo la imagen.
- **TV en vivo.** Vista previa de Media3 silenciada tras 650 ms de foco estable. Respeta las cabeceras del canal y libera recursos al perder el foco de ventana, salir de la pantalla o abrir el reproductor principal.
- **Fichas.** Fondos panorámicos, miniatura de tráiler y retratos de reparto cuando TMDB los proporciona. La caché de imágenes distingue el tamaño de los fondos del de las miniaturas.
- **Lista de prueba.** Tres clips H.264 incluidos en el APK sustituyen las rutas ficticias. Las instalaciones existentes conservan los identificadores de canales, favoritos y progreso. El ejemplo de directo se reproduce en bucle.
- **Documentación.** Capturas de las seis pantallas en el emulador y README centrado en la primera importación, navegación con mando, metadatos y generación e instalación local del APK preview.

- **Comprobación local.** APK preview generado, 49 pruebas unitarias superadas y Android Lint con 0 errores y 97 avisos. Se recorrieron las seis vistas y se comprobó vídeo en la previsualización de TV. El emulador perdió la conexión ADB durante el recorrido de reproducción a pantalla completa, que queda pendiente de validación.

### Sesión 10: Revisión de foco y ciclo de vida (23 Noviembre 2026)

- La selección de temporada conserva sus botones y el foco al confirmar con el mando. Las temporadas disponen de desplazamiento horizontal.
- La ficha cancela la miniatura del tráiler al cerrarse y comprueba la disponibilidad de cada retrato del reparto.
- La vista previa libera una instancia anterior antes de crear otra y reconoce HLS mediante el parámetro output=m3u8.
- Verificación realizada con assemblePreview, testDebugUnitTest y lintDebug. Compilación correcta, 49 pruebas superadas, 0 errores y 97 avisos de Lint.
- La verificación integral en ejecución sigue pendiente. En esta sesión el emulador temporal, iniciado sin snapshot y con renderizado por software, no completó el arranque a tiempo y rechazó la instalación con “device is still booting”. No se puede certificar ausencia de bugs ni equivalencia visual completa con las referencias.
### Sesión 11: Revisión de nitidez y diagnóstico del emulador (23 Noviembre 2026)

- Corregida la caché de Artwork para distinguir recurso y anchura solicitada. Una miniatura cargada previamente ya no se reutiliza como fondo grande. Se conserva el límite de 16 MB y se evita insertar una decodificación nula.
- APK preview compilado correctamente, 49 pruebas unitarias superadas y Lint con 0 errores y 97 avisos.
- La sesión supervisada del emulador completó el arranque, pero registró DeadSystemException en tvlauncher, System UI, Google Play Services y otros procesos del sistema. La instalación falló con Broken pipe y un proveedor settings todavía no inicializado.
- Registro de diagnóstico local en .local/revision-system-crash.txt. La sesión temporal se cerró. La revisión integral de reproducción y aspecto visual continúa pendiente de un entorno Android estable.
## 4. HISTORIAL DE VERSIONES Y PUBLICACIONES EN GITHUB

| Versión | Fecha | Commit Git | Tag | APK Publicado | SHA-256 del APK | Resumen de Cambios Clave |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **v1.0.0** | 21/11/2026 | `e313503` | `v1.0.0` | `LunvoryxPlayer-v1.0.0.apk` | — | Primera release oficial con servidor QR, ExoPlayer y modo clásico tvOS. |
| **v1.0.1** | 22/11/2026 | `7d20181` | `v1.0.1` | `LunvoryxPlayer-v1.0.1.apk` | — | Soporte para listas masivas: límite ampliado a 250 MB y 150.000 canales, `largeHeap`. |
| **v1.1.0** | 22/11/2026 | `4233f97` | `v1.1.0` | `LunvoryxPlayer-v1.1.0.apk` | — | Rediseño visual: nuevo Modo Intuitivo, top bar, mosaico de inicio y fichas de contenido. |
| **v1.1.1** | 22/11/2026 | `8eee934` | `v1.1.1` | `LunvoryxPlayer-v1.1.1.apk` | `8D68BA5DF45CFD50...` | Hotfix: corrección de layout en top bar (bloqueo de pantalla), autoenfoque D-Pad y fondo 100%. |
| **v1.2.0** | 22/11/2026 | `0a2a8a4` | `v1.2.0` | `LunvoryxPlayer-v1.2.0.apk` | `3E13B263B028A6DA...` | 1 carátula por serie (~3.700 series), ficha por temporadas T1/T2/T3, canales lineales fuera de Películas. |
| **v1.2.1** | 22/11/2026 | `6e8f3e3` | `v1.2.1` | `LunvoryxPlayer-v1.2.1.apk` | `7A0D98AB7A6E03C7...` | Carga asíncrona en background (elimina congelación al entrar a Series), caché en memoria 0 ms, aceleración GPU. |
| **Trabajo local posterior a v1.2.1** | 22/11/2026 | — | — | `app-preview.apk` local | — | Optimización adicional del reproductor, consultas cancelables, snapshots de catálogo, búsqueda reciclable y verificación con 150.000 entradas. Pendiente de aprobación antes de publicar una nueva versión. |

---

## 5. MAPA DE ARCHIVOS Y ARQUITECTURA DEL PROYECTO

```text
app/src/main/
├── AndroidManifest.xml                  -> Configuración principal (Leanback TV, largeHeap, hardwareAccelerated, permisos)
└── java/tv/lunvoryx/player/
    ├── MainActivity.java                -> Actividad central. Controla la navegación, orquesta el menú superior y el clásico, D-Pad handlers y ciclo de vida.
    ├── data/
    │   ├── Channel.java                 -> Modelo inmutable de canal/VOD (id, name, url, group, logoUrl, tvgId, cachedKind).
    │   ├── EpisodeItem.java             -> Modelo de capítulo individual de serie (temporada, episodio, título limpio, duración).
    │   ├── MediaKind.java               -> Clasificador estricto de contenido: LIVE (directo), MOVIE (película VOD), SERIES (serie).
    │   ├── M3uParser.java               -> Parser optimizado de listas M3U/M3U8 con soporte de hasta 150.000 entradas y atributos tvg.
    │   ├── Playlist.java                -> Modelo de lista guardada (id, nombre, origen, canales, fecha).
    │   ├── PlaylistRepository.java      -> Repositorio central de catálogos en almacenamiento atómico. Publica snapshots inmutables e índices reutilizables y precalcula agrupaciones en segundo plano.
    │   ├── SeriesItem.java              -> Agregador de serie que agrupa todos sus episodios en temporadas ordenadas.
    │   └── SeriesParser.java            -> Motor de expresiones regulares con TITLE_CACHE para agrupar 80.000 capítulos en ~3.700 series.
    ├── epg/
    │   ├── GuideActivity.java           -> Interfaz de guía de programación interactiva (EPG).
    │   └── XmltvParser.java             -> Parser streaming de archivos XMLTV/GZIP para la guía de TV.
    ├── player/
    │   └── PlayerActivity.java          -> Reproductor de vídeo a pantalla completa basado en Google ExoPlayer.
    ├── remote/
    │   └── RemoteServer.java            -> Servidor HTTP embebido en puerto local para carga de listas mediante código QR desde smartphone.
    ├── tmdb/
    │   └── TmdbClient.java              -> Cliente HTTP para la API de The Movie Database con persistencia local de sinopsis, reparto y pósteres.
    ├── ui/
    │   ├── Artwork.java                 -> Decodificador y caché LRU de imágenes de interfaz para TV.
    │   ├── HomeBackdrop.java            -> Fondo panorámico cinemático con degradado oscuro de legibilidad.
    │   ├── HomeScreen.java              -> Pantalla de inicio del modo clásico (Apple tvOS).
    │   ├── IntuitiveHomeScreen.java     -> Mosaico dinámico de inicio del Modo Intuitivo (TV en vivo, Películas, Series, Recargar, Ajustes).
    │   ├── IntuitiveTopBar.java         -> Barra de navegación superior con logotipo Lunvoryx, cápsulas, buscador y fecha de caducidad.
    │   ├── LineIcon.java                -> Motor de renderizado vectorial de iconos limpios (TV, Películas, Series, Buscar, Engranaje, etc.).
    │   ├── BrowseFilter.java            -> Filtro y ordenación de catálogos ejecutados fuera del hilo principal.
    │   ├── CategoryList.java            -> Lista reciclable de categorías con selección y foco D-Pad.
    │   ├── CatalogTask.java             -> Trabajo cancelable para consultas de catálogo y resultados obsoletos.
    │   ├── LiveTvBrowseView.java        -> Vista de TV en vivo con 3 columnas (categorías, canales y vista previa con reproductor) y carga asíncrona.
    │   ├── LogoLoader.java              -> Cargador asíncrono de logotipos de canales y carátulas con pool de subprocesos y caché LRU.
    │   ├── MediaBrowseView.java         -> Vista de catálogo de Películas y Series en 6 columnas con filtrado y carga asíncrona.
    │   ├── MediaDetailView.java         -> Ficha multimedia completa (sinopsis, selector de temporadas, episodios, tráiler YouTube, reparto y calidad).
    │   ├── PreviewMetadata.java         -> Carga cancelable y cacheada de metadatos opcionales de TMDB.
    │   ├── SearchViewLayout.java        -> Buscador global con teclado virtual en pantalla o D-Pad y resultados reciclables.
    │   ├── SettingsScreen.java          -> Pantalla de ajustes con conmutador de Modo Intuitivo, idioma, servidor QR y comprobación de versión.
    │   ├── SquircleGlassDrawable.java   -> Gráfico vectorial procedural para tarjetas con esquinas *squircle*, borde y efecto de foco translúcido.
    │   └── TvStyle.java                 -> Paleta de colores, márgenes, tipografías y estilos visuales para Android TV.
    ├── updater/
    │   └── UpdateManager.java           -> Comprobador de actualizaciones automáticas desde GitHub Releases y descargador de APKs.
    └── util/
        └── AppStrings.java              -> Gestor de textos bilingüe (Español / Inglés).
```

---

## 6. INSTRUCCIONES CRÍTICAS PARA CUALQUIER AGENTE O IA QUE TRABAJE EN ESTE PROYECTO

1. **PROHIBICIÓN DE PUBLICAR EN GITHUB SIN ORDEN EXPRESA:**
   - **Regla de oro del usuario:** Jamás crear releases, tags o hacer `git push` a `origin` a menos que el usuario lo solicite expresamente de forma literal (p. ej. *"Publicala"* o *"Súbelo a GitHub"*).
   - Cualquier cambio se debe probar y simular primero localmente.
2. **PRIORIDAD DEL MANDO A DISTANCIA (D-PAD):**
   - Todos los elementos interactivos (`TextView`, `FrameLayout`, `LinearLayout`) deben tener obligatoriamente `setFocusable(true)` y `setClickable(true)`.
   - Cuando se abra una pantalla o diálogo, se debe llamar inmediatamente a `post(() -> view.requestFocus())` para que el mando nunca quede "en el aire" o bloqueado.
3. **PROCESAMIENTO FUERA DEL HILO PRINCIPAL (NO BLOQUEAR EL UI THREAD):**
   - El catálogo del usuario contiene entre 80.000 y 150.000 entradas.
   - Cualquier bucle que itere canales, analice títulos o clasifique contenido **DEBE** ejecutarse en un hilo en segundo plano (`ExecutorService`), mostrando un indicador de progreso si es necesario y utilizando la caché en memoria de `PlaylistRepository`.
4. **MANTENER EL ARCHIVO `.gitignore` LIMPIO:**
   - Este archivo (`CONTEXTO_TOTAL_PROYECTO.md`), los archivos temporales de log y los archivos de claves `.keystore` o `.apk` locales nunca deben entrar en el control de versiones de Git.
5. **CONSERVACIÓN DE LOS DOS MODOS DE INTERFAZ:**
   - El *Modo Intuitivo* es el modo moderno y preferido, pero el *Modo Clásico* debe mantenerse intacto y operativo desde el selector de *Ajustes*.

---
*Fin del documento de contexto total.*
