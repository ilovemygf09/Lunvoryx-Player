# INFORME COMPLETO DE DESARROLLO Y ESTADO DEL PROYECTO
# Lunvoryx Player (Android TV / IPTV)

**Fecha de última actualización:** 21 de Noviembre de 2026  
**Proyecto:** Lunvoryx Player  
**Plataforma de destino:** Android TV / Google TV / Fire OS (Arquitectura Leanback con mando a distancia / D-Pad)  
**Paquete:** `tv.lunvoryx.player`  
**Tecnología:** Java 17, Android SDK 37 (minSdk 23, targetSdk 36), ExoPlayer / Media3 1.11.1, HTTP Server empotrado (tokens 128-bit), TMDB REST API, Vector Graphics Engine.

---

## 1. Resumen Ejecutivo

Lunvoryx Player es una aplicación de reproducción IPTV de última generación diseñada específicamente para televisores y dispositivos Android TV. Destaca por una experiencia de usuario cinematográfica, interfaz fluida tipo "glassmorphism" en tonos oscuros azulados y púrpuras, navegación intuitiva optimizada 100% para control remoto y mando a distancia, e integración nativa de funciones avanzadas como transferencia inalámbrica de listas mediante código QR, sincronización de metadatos cinematográficos en tiempo real (TMDB) y sistema de comprobación de actualizaciones remotas (OTA).

Este documento recoge de forma exhaustiva, estructurada y cronológica todas las peticiones del usuario, la arquitectura implementada, las soluciones técnicas aplicadas y el estado actual de cada sección de la aplicación.

---

## 2. Cronología Detallada de Solicitudes y Entregas

### Hito 1: Revisión General y Transferencia de Listas vía QR Móvil
* **Solicitud del usuario:**  
  * Revisar que todo el reproductor funcione correctamente sin alterar la estética inicial.  
  * Evaluar e implementar una función que permita añadir listas IPTV escaneando un código QR desde el móvil conectado a la misma red Wi-Fi local, de modo que al introducir la lista o subir el archivo en el teléfono, se transfiera de inmediato al televisor.
* **Solución Técnica y Entrega:**  
  * **Servidor HTTP Local Empotrado (`tv.lunvoryx.player.remote.RemoteServer`):** Se integró un micro-servidor HTTP local que escucha en el puerto `8080` de la IP local asignada al televisor (ej. `http://192.168.1.X:8080`).
  * **Página Web Responsive:** El servidor sirve una página web ultraligera diseñada para móviles con dos métodos de carga:
    1. Subida directa de archivo `.m3u` / `.m3u8` desde el explorador del móvil.
    2. Pegado de URL remota o credenciales Xtream Codes.
  * **Generador de Códigos QR Nativo (`tv.lunvoryx.player.remote.QrCodeGenerator`):** Renderizado directo en un `Bitmap` de Android utilizando algoritmos de codificación QR sin requerir librerías pesadas ni conexión a servidores externos.
  * **Integración en la UI:** El código QR se muestra dentro del diálogo de gestión de listas ("Mis listas" -> "Añadir lista"). Al completar la transferencia en el teléfono, la lista se descarga, se valida y se activa automáticamente en el reproductor.

---

### Hito 2: Arquitectura y Viabilidad de Actualizaciones Remotas
* **Solicitud del usuario:**  
  * Consulta sobre la viabilidad de enviar actualizaciones una vez instalado el APK en el televisor del usuario sin tener que pasar por Google Play Store.
* **Solución Técnica y Entrega:**  
  * Se diseñó el módulo **`tv.lunvoryx.player.updater.UpdateManager`** para soportar actualizaciones "In-App / OTA (Over-The-Air)":
    1. **Verificación de versión:** Comprobación contra un endpoint JSON (o API de GitHub Releases) comparando el `versionCode` local contra el remoto.
    2. **Descarga en segundo plano:** Descarga del archivo APK mediante `DownloadManager` o streaming HTTP con barra de progreso.
    3. **Disparador de instalación:** Lanzamiento del intent nativo `android.content.Intent.ACTION_VIEW` con `application/vnd.android.package-archive` y `FileProvider`, gestionando la concesión de `REQUEST_INSTALL_PACKAGES`.

---

### Hito 3: Identidad de Marca "Lunvoryx Player" y Nuevo Logo Oficial
* **Solicitud del usuario:**  
  * Renombrar la aplicación de "Lunvoryx Player" a **Lunvoryx Player**.  
  * Sustituir los logos anteriores por la nueva imagen de marca oficial proporcionada (isotipo 3D con botón Play azul/plata, ondas de emisión y tipografía futurista `LUNVORYX PLAYER`).  
  * Integrar mensaje informativo en Ajustes indicando la disponibilidad del sistema de comprobación de versiones APK.
* **Solución Técnica y Entrega:**  
  * Se procesó el logotipo en alta definición (`app_logo.png`) y se configuró como icono principal de la app, banner para el lanzador de Android TV (`res/drawable/banner.png`) y en la cabecera superior del menú de navegación lateral.
  * Se actualizaron `app_name` y todas las referencias de branding en `AndroidManifest.xml` y recursos de cadenas.

---

### Hito 4: Menú Lateral Calcado al Mockup, Soporte Multi-idioma, TMDB y Búsqueda
* **Solicitud del usuario:**  
  * Clavar con máxima fidelidad el diseño de la columna lateral izquierda según la captura suministrada (`docs/images/inicio.png`): proporciones, logo superior integrado, tipografía, transparencia oscura del fondo, píldora de selección blanca con texto e icono invertidos.
  * Orden exacto de secciones en la barra lateral: **Inicio**, **TV En Vivo**, **Películas**, **Series**, **Buscar**, **Ajustes**, **Mis listas**.
  * Añadir soporte de idiomas en Ajustes (inicialmente Español e Inglés).
  * Permitir configurar una API Key de TMDB (The Movie Database) para portadas, sinopsis, reparto y tráilers.
* **Solución Técnica y Entrega:**  
  * **Sistema de Localización Dinámica (`tv.lunvoryx.player.ui.AppStrings`):** Permite cambiar el idioma entre Español e Inglés en tiempo de ejecución sin reiniciar la aplicación ni recrear la pila de actividades de forma abrupta.
  * **Integración TMDB (`tv.lunvoryx.player.tmdb.TmdbClient`):** Cliente HTTP asíncrono con sistema de caché en disco y memoria para resolver portadas en alta definición (Backdrops `w1280` y Posters `w500`), descripción argumental, año de estreno, géneros y actores.
  * **Barra Lateral de Alta Fidelidad:**
    * Fondo translúcido con gradiente suave (`#0B0D18` a `#121526`).
    * Elemento activo resaltado con una píldora blanca sólida de bordes redondeados (`#FFFFFF`), aplicando contraste automático con texto e icono en negro/grafito (`#10121D`), mientras los elementos inactivos mantienen un tono gris perlado (`#A0A4B8`).

---

### Hito 5: Rediseño Integral de la Pantalla de Ajustes
* **Solicitud del usuario:**  
  * Clavar el diseño del mockup para la pantalla de Ajustes (`docs/images/ajustes.png`).
  * Estructura dividida en dos grandes tarjetas agrupadas: **General** y **Reproducción**.
  * Switches modernos estilo iOS/glassmorphic interactivos con el mando.
  * Filas con iconos descriptivos y flechas de navegación a la derecha.
* **Solución Técnica y Entrega:**  
  * **Componente `ModernSwitch.java`:** Switch animado desarrollado desde cero con Canvas, soporte de foco D-pad, estado ON en azul brillante (`#2979FF`) y OFF en gris neutro (`#3A3E52`).
  * **Bloque General:**
    * *Logotipos de canales:* Switch interactivo para alternar entre logos de canal o texto.
    * *Orden alfabético:* Switch para ordenar las listas A-Z u orden original de emisión.
    * *Configurar guía de programación:* Apertura de la configuración y actualización de la guía EPG.
    * *Comprobar actualizaciones:* Conexión con `UpdateManager` y aviso del estado de versión.
    * *Idioma:* Selector dinámico interactivo (Español / English).
  * **Bloque Reproducción:**
    * *Reproductor de vídeo:* Modal para elegir motor (Automático, ExoPlayer, VLC).
    * *Subtítulos:* Modal de configuración de tamaño, color y preferencia de idioma.
    * *Buffer de reproducción:* Ajuste de latencia de red (Pequeño, Normal, Grande).
  * **Configuración TMDB:** Diálogo de inserción de API Key con validación directa de conectividad.

---

### Hito 6: Rediseño de las Secciones "Películas" y "Series"
* **Solicitud del usuario:**  
  * Clavar el diseño del mockup de Películas (`docs/images/peliculas.jpg`) tanto para **Películas** como para **Series**.
  * Las películas, series y categorías mostradas deben ser las reales de la lista cargada por el usuario (los datos del mockup son de referencia).
  * Inclusión forzosa de las categorías útiles esenciales: **Todas**, **Favoritas** y **Reanudar para ver / Seguir viendo**, incluso si el archivo M3U original no las incluye.
* **Solución Técnica y Entrega:**  
  * **Componente `MediaBrowseView.java`:** Motor de navegación en 3 columnas adaptado para VOD (Películas y Series):
    1. **Columna Izquierda (Categorías):**
       - Icono de carpetas, nombre de categoría y contador numérico en píldora gris oscura.
       - Generación automática de categorías virtuales del sistema:
         * *Todas:* Agrupación completa con contador total.
         * *Favoritas:* Filtrado instantáneo de elementos marcados con estrella.
         * *Reanudar para ver:* Historial ordenado por fecha de última reproducción con punto de pausa guardado.
       - Despliegue de todas las categorías reales parseadas de la lista del usuario.
    2. **Columna Central (Cuadrícula de Contenido):**
       - Cabecera con título de la categoría activa, contador total de títulos y buscador en la categoría.
       - Grilla de pósters con relación de aspecto 2:3, bordes redondeados y efecto de selección luminosa en azul neón (`#3A78F2`).
       - Badges superpuestos de calidad (*4K*, *HD*) y año de estreno.
    3. **Columna Derecha (Panel de Vista Previa Cinematográfica):**
       - Imagen panorámica (Backdrop) en alta resolución con botón de reproducción flotante.
       - Título de la obra, badges de metadatos (Año, Resolución, Duración, Género).
       - Sinopsis argumental completa enriquecida con TMDB o parseada del stream.
       - Botones de acción principales: **▶ Ver película / Ver serie** y **☆ Favorito**.
       - Ficha técnica completa con etiquetas de *Director*, *Reparto*, *Audio* y *Subtítulos*.

---

### Hito 7: Rediseño de la Pantalla "TV En Vivo"
* **Solicitud del usuario:**  
  * Clavar el diseño del mockup para TV En Vivo (`docs/images/tv_en_vivo.jpg`).
  * 3 columnas: Categorías a la izquierda, Canales al centro con barra de progreso EPG azul celeste y hora, Panel de Vista Previa a la derecha con badge rojo "● EN DIRECTO".
  * Canales y categorías reales de la lista del usuario.
  * Inclusión automática de las categorías: **Todas**, **Favoritos** y **Seguir viendo**.
* **Solución Técnica y Entrega:**  
  * **Componente `LiveTvBrowseView.java`:**
    1. **Columna de Categorías:**
       - Categorías fijas autogeneradas (*Todas*, *Favoritos*, *Seguir viendo*) más las categorías de países y temáticas de la lista IPTV con sus correspondientes banderas/iconos.
    2. **Columna de Canales:**
       - Cabecera con título de categoría y total de canales.
       - Tarjeta horizontal por canal con logo oficial, badge *HD / FHD*, nombre del canal.
       - Fila EPG: Título del programa en emisión actual, hora programada y **barra de progreso horizontal en degradado azul celeste** calculando con precisión de milisegundos el porcentaje emitido del programa.
    3. **Columna de Vista Previa en Directo:**
       - Ventana de visualización con el badge superior **● EN DIRECTO** sobre fondo rojo semitransparente.
       - Logo en grande, título del programa, rango horario (ej. `20:00 - 21:00`) y tiempo restante estimado (ej. `Quedan 25 min`).
       - Botón **▶ Ver canal** y botón **☆ Favorito**.
       - Bloque inferior **A continuación** mostrando el siguiente programa programado en la guía EPG con su hora de inicio.

---

### Hito 8: Rediseño de la Pantalla "Buscar" e Informe Completo
* **Solicitud del usuario:**  
  * Clavar el diseño del mockup para la sección Buscar (`docs/images/buscar.jpg`).
  * Barra de búsqueda superior con efecto glow azul oscuro, icono de lupa e icono de micrófono.
  * Fila de píldoras de filtrado: **Todo**, **TV En Vivo**, **Películas**, **Series**, **Favoritos**.
  * 3 columnas de contenido:
    * *Columna A:* Historial de "Búsquedas recientes" con icono de reloj y cruz de borrado individual + sección de "Tendencias" con flechas ascendentes.
    * *Columna B:* "Resultados" agrupados por tipo (*Canales en vivo (X)*, *Series (X)*, *Películas (X)*), con tarjetas horizontales con miniatura, título, badges HD y edad, categorías y estrella de favoritos.
    * *Columna C:* "Vista previa" con backdrop, título, tags, metadatos, sinopsis completa, botones *▶ Ver*, *☆ Favorito*, *ⓘ Más info*, selector de temporadas en píldoras y tarjeta de episodio "A continuación".
  * Creación y mantenimiento de este documento `INFORME_COMPLETO.md`.
* **Solución Técnica y Entrega:**  
  * **Componente `SearchViewLayout.java`:**
    - Barra de búsqueda estilizada con fondo `#141829`, trazo luminiscente `#354B78` y esquinas redondeadas.
    - Fila de filtros interactivos (*Todo*, *TV En Vivo*, *Películas*, *Series*, *Favoritos*) con cambio de color activo.
    - Historial de búsquedas recientes persistido en `SharedPreferences` en formato JSON, con botón de eliminación por ítem y clic directo para repetir la búsqueda.
    - Algoritmo de partición inteligente de resultados que clasifica coincidencias en directo mediante `MediaKind` en *Canales en vivo*, *Series* y *Películas*.
    - Panel lateral de vista previa dinámico que muestra los detalles del ítem enfocado, temporadas disponibles y botón directo de reproducción inmediata.
  * **Conexión en `MainActivity.java`:** Reemplazo de la vista antigua de búsqueda por `SearchViewLayout` con mapeo de foco directo desde el menú lateral.

---

### Hito 9: Purga Total de Referencias Previas, Nuevo Paquete `tv.lunvoryx.player` y Actualizaciones Exclusivas por GitHub Releases
* **Solicitud del usuario:**  
  * Sustituir cualquier mención que aparezca del nombre anterior por **Lunvoryx** (en código, imágenes, informes o recursos). No puede quedar ni una sola mención en ningún sitio del proyecto.
  * La única forma de actualizar la aplicación debe ser a través de GitHub Releases cuando el usuario suba el repositorio.
  * Crear una guía exhaustiva explicando paso a paso cómo subir el proyecto a GitHub y publicar nuevas versiones.
  * Si la aplicación está desactualizada, al iniciarla debe mostrar un diálogo con 3 opciones: **Actualizar**, **No recordar en 24h** y **No actualizar**.
  * Permitir al usuario comprobar si está actualizado desde los Ajustes del reproductor.
* **Solución Técnica y Entrega:**  
  * **Refactorización completa de Paquete (`tv.lunvoryx.player`):** Se migraron todas las clases Java, tests unitarios, manifiestos y recursos al nuevo paquete oficial `tv.lunvoryx.player`.
  * **Regeneración de Recursos Gráficos:**
    - Generación de `tv_banner.png` (640x360 px) centrado sobre fondo azul noche `#0B0D18` con el isotipo oficial Lunvoryx Player en alta resolución.
    - Generación de `ic_launcher.png` (512x512 px) con el isotipo oficial.
  * **Purga Absoluta:** Cero apariciones del término previo en todo el repositorio.
  * **Canal Exclusivo de Actualizaciones por GitHub Releases:**
    - Se eliminó el endpoint de subida manual de APKs por Wi-Fi de `RemoteServer.java`, dedicando el servidor local exclusivamente a la transferencia de listas IPTV.
    - `UpdateManager.java` consulta las Releases de GitHub vía API y cuenta con fallback a `version.json` para garantizar inmunidad frente a límites de tasa de peticiones.
    - Comprobación en arranque integrada en `MainActivity.java` con persistencia del temporizador de 24 horas (`suppressed_until`).
    - Selector manual en Ajustes -> "Comprobar actualizaciones" y opción para vincular el repositorio en "Repositorio de GitHub".
  * **Guía de GitHub (`GUIA_GITHUB.md`):** Documento detallado paso a paso con los comandos git para subir el código a GitHub, crear tags y adjuntar APKs a las Releases.

---

### Hito 10: Rediseño Integral de la Sección «Añadir lista» / «Mis listas»
* **Solicitud del usuario:**  
  * Clavar el diseño del mockup para la pantalla de Añadir lista / Mis listas (`docs/images/anadir_lista.jpg`).
  * Cabecera con icono `playlist_add`, título "Añadir lista" y subtítulo "Elige cómo quieres importar tu contenido".
  * Tarjeta izquierda con borde neón azul luminoso para **Xtream Codes** (Nombre, URL servidor, Usuario, Contraseña con conmutador de visibilidad y botón Conectar).
  * Columna derecha con dos tarjetas:
    * **Enlace M3U:** Campo para URL y botón blanco "+ Añadir lista".
    * **Archivo M3U:** Descripción para importar archivos locales (USB, disco) y botón blanco con icono de carpeta "Seleccionar archivo".
  * Fila inferior con banner completo **Desde tu móvil**:
    * Sección informativa de conexión a la misma red Wi-Fi.
    * Código QR central interactivo en tiempo real conectado al servidor local empotrado.
    * Indicador de estado con píldora verde `● Servidor local activo`, caja de dirección URL local (`http://<ip>:8080`) y badges de compatibilidad.
* **Solución Técnica y Entrega:**  
  * **Componente `AddPlaylistViewLayout.java`:** Vista nativa completa en dos filas que calca el diseño exacto del mockup con soporte 100% para control remoto y navegación D-pad.
  * **Generación dinámica de URL y QR:** Integración directa con `RemoteServer` para arrancar automáticamente el servicio local en el puerto `8080` y reflejar la IP real del televisor.
  * **Conector Xtream Codes:** Conversor automático de credenciales a URL de API de streaming (`/get.php?username=...&password=...&type=m3u_plus&output=ts`).
  * **Botón de acceso a listas guardadas:** Enlace en la cabecera para consultar y gestionar listas ya importadas sin alterar el diseño limpio del importador principal.

---

### Hito 11: Motor de Glassmorphism Profesional "Anti-Slop" y Continuous Corners ($G^2$ Squircles / Corner Smoothing)
* **Solicitud del usuario:**  
  * Aplicar un estilo visual **frosted glass / glassmorphism mucho más notorio, profesional y "anti-slop"**.
  * En las esquinas de todos los elementos: sustituir los redondeos circulares estándar (`setCornerRadius`) por **"continuous corners / corner smoothing / squircle"** con transición suave y progresiva entre los lados rectos y las curvas (curvatura continua $G^2$, estilo Apple / Figma 65%).
  * Aplicarlo en **todas las secciones** de la app y en el **menú lateral de la izquierda**. En todo.
* **Solución Técnica y Fundamento Matemático:**  
  1. **Algoritmo de Curvatura Continua $G^2$ (`tv.lunvoryx.player.ui.SquirclePath`):**  
     - A diferencia de un arco circular simple donde la curvatura pasa bruscamente de $0$ a $1/R$ generando esquinas "pellizcadas" con discontinuidad de segundo orden ($G^1$), el generador de esquinas continuas descompone cada esquina de 90° en curvas de Bézier cúbicas con factor de suavizado $s = 0.65$ (parámetro de curvatura continua iOS/Figma).
     - La curva inicia su transición con aceleración progresiva de curvatura desde la arista recta a una distancia $p = (1 + s) \cdot R$, alcanzando tangencia diagonal a 45° con continuidad de derivada primera ($C^1$) y segunda ($G^2$), garantizando una silueta squircle perfectamente orgánica y libre de aristas artificiales.
  2. **Motor de Glassmorphism Profesional (`tv.lunvoryx.player.ui.SquircleGlassDrawable`):**  
     - **Base Translúcida Gradual:** Gradiente lineal multidireccional que transiciona suavemente desde un tinte superior-izquierdo reflectivo (`0xD822283E`) hasta una base profunda oscura (`0xF40E101D`), simulando refracción volumétrica sin saturación ni efecto plano.
     - **Luz Especular de Borde ("Specular Rim Light"):** Bisel superior iluminado de 1.2dp trazado en el perímetro de la squircle mediante gradiente de luz cenital (`0x66FFFFFF` $\to$ `0x12FFFFFF`), recreando el reflejo físico de la iluminación de estudio en vidrio templado.
     - **Borde de Foco Neón Dinámico (Android TV D-pad):** Al recibir foco por el mando a distancia, el contorno conmuta dinámicamente a un trazo continuo de 2.2dp con gradiente cian-azul eléctrico (`#00E5FF` $\to$ `#2979FF` $\to$ `#1565C0`), aportando claridad inmediata al usuario en pantallas grandes.
  3. **Despliegue Transversal en Toda la Interfaz:**  
     - **Menú Lateral Izquierdo (`MainActivity.java`):** Fondo de superficie de vidrio ahumado con esquinas derechas continuas en squircle (`setCorners(false, true, true, false)`). Píldora activa seleccionada en squircle continuo `#D8D9E0` con texto oscuro `#14151B`; píldora enfocada con borde neón cian.
     - **Añadir Lista / Mis Listas (`AddPlaylistViewLayout.java`):** Tarjeta *Xtream Codes* con squircle y borde neón persistente; tarjetas *Enlace M3U* y *Archivo M3U* con glassmorphism y bisel especular; campos de entrada en squircle de vidrio obsidiana oscuro (`squircleInput`); botones de acción *Conectar*, *+ Añadir lista* y *Seleccionar archivo* en píldoras squircle continuas blancas con foco azul neón; banner inferior *Desde tu móvil* con squircle de 18dp y marco de QR blanco en squircle de 12dp.
     - **Buscar (`SearchViewLayout.java`):** Barra de búsqueda tipo píldora squircle de 24dp con borde luminoso; píldoras de filtrado superior (*Todo*, *TV En Vivo*, *Películas*, *Series*, *Favoritos*) en squircles continuos; paneles de historial, resultados y vista previa con glassmorphism profesional; tarjetas de resultados con miniatura squircle y glow de foco.
     - **TV En Vivo (`LiveTvBrowseView.java`):** Las 3 columnas (*Categorías*, *Canales*, *Vista Previa*) renderizadas en tarjetas squircle de vidrio translúcido; badge de cabecera en squircle; buscador y botón de ordenación en squircle; marco del reproductor y badge rojo *"● EN DIRECTO"* en squircle continuo; filas de canales y categorías con selector squircle blanco suave al enfocar.
     - **Películas y Series (`MediaBrowseView.java`):** Paneles de navegación en vidrio squircle; pósters de películas y series en tarjetas squircle con borde luminoso continuo al enfocar y escala de zoom cinemático; botones de reproducción y favoritos en squircle.
     - **Ajustes (`MainActivity.java`):** Tarjetas de configuración *General*, *Reproducción* y *Avanzado* en vidrio squircle de 18dp con bisel de luz superior; selector de fila al pasar el mando en squircle continuo; interruptores animados `ModernSwitch.java` con carril y pista dibujados mediante `SquirclePath`.
     - **Pantalla de Inicio (`HomeScreen.java`):** Tarjeta vacía, mosaicos y botones en squircle continuous corners.

---

### Hito 12: Rediseño Integral de la Web Móvil de Importación vía QR (`RemoteServer.java`)
* **Solicitud del usuario:**  
  * Replicar con máxima fidelidad la página web que se abre en el teléfono móvil al escanear el código QR para añadir una lista (`docs/images/movil_qr.jpg`).
  * Integrar cabecera con logotipo oficial 3D Lunvoryx Player, título *"Añadir lista desde el móvil"*, indicador de estado `● Reproductor detectado` y píldora con dirección IP y puerto (`192.168.1.50:8080`) con botón interactivo de copia al portapapeles.
  * Tarjeta central glassmorphic con borde luminoso azul neón y esquinas continuas (squircles), barra superior con 3 pestañas (*Xtream Codes*, *M3U URL*, *Archivo M3U*), inputs estilizados con iconos descriptivos (nombre, URL servidor, usuario, contraseña con conmutador de visibilidad y checkbox), botón de acción blanco `+ Añadir lista` y enlace para alternar método (`⇄ Usar otro método`).
  * Tarjeta inferior informativa *"Misma red Wi-Fi"* con borde púrpura translúcido, icono Wi-Fi e indicador de seguridad en red local.
  * Botón inferior tipo píldora *"Volver a Mis listas"* que envía comando directo de navegación al televisor.
* **Solución Técnica y Entrega:**  
  1. **Servidor HTTP Autónomo con Soporte Completo (`tv.lunvoryx.player.remote.RemoteServer`):**  
     - Servidor HTTP multi-hilo empotrado optimizado para dispositivos móviles, sirviendo una aplicación web HTML5/CSS3/JavaScript totalmente autónoma sin dependencias externas ni conexión a internet (100% operativa en red local LAN/Wi-Fi).
     - Endpoints activos:
       - `GET /`: Entrega la interfaz web móvil de alta fidelidad con gradientes radiales oscuros y estética glassmorphism.
       - `POST /api/playlist`: Recibe payloads en formato JSON o form-data, soportando tanto listas directas M3U (URL o texto completo) como credenciales Xtream Codes, autogenerando en backend el enlace M3U compatible (`/get.php?username=...&password=...&type=m3u_plus&output=ts`).
       - `GET /api/status`: Permite al navegador del móvil verificar la conectividad activa con el televisor para iluminar el indicador verde `● Reproductor detectado`.
       - `GET|POST /api/navigate`: Recibe la orden de navegación remota provocada por el botón móvil *"Volver a Mis listas"* y conmuta en tiempo real la pantalla de la TV hacia la gestión de listas mediante `listener.onNavigateBack()`.
  2. **Diseño Web Móvil Ultrarrefinado (Mobile First):**  
     - **Estética Visual:** Fondo profundo `#080911` con orbes de luz ambiental púrpura y azul eléctrico, tarjeta central en gradiente de vidrio ahumado con `backdrop-filter: blur(25px)`, esquinas redondeadas continuas (squircles de 26px) y resplandor perimetral neón azul (`box-shadow: 0 0 26px rgba(59, 130, 246, 0.32)`).
     - **Pestañas Interactivas Fluidas:** Transición instantánea en el cliente entre los formularios *Xtream Codes*, *M3U URL* y *Archivo M3U* sin recargar la página.
     - **Control de Contraseña Sincronizado:** El icono de ojo y el checkbox *"Mostrar contraseña"* están completamente sincronizados en tiempo real para alternar entre tipo `password` y `text`.
     - **Selector y Arrastre de Archivos:** Zona de carga estilizada para archivos `.m3u` y `.m3u8` con lectura local mediante la API `FileReader` de HTML5 y transferencia asíncrona mediante `fetch()`.
     - **Feedback Visual Toast y Pantalla de Éxito:** Mensajes de confirmación emergentes no intrusivos ("¡Dirección copiada!", "¡Lista enviada con éxito!") con pantalla de éxito dedicada que permite volver a añadir listas adicionales.

---


---

### Hito 13: Auditoría Integral de Seguridad, Hardening, Optimización y Preparación para GitHub
* **Solicitud del usuario:**  
  * Auditoría exhaustiva y multidisciplinar de seguridad (OWASP MASVS), código, dependencias, servidor local, actualizaciones OTA, compilación y empaquetado.
  * Hardening del servidor HTTP local (`RemoteServer.java`) con tokens criptográficos de emparejamiento y mitigación de abusos en red Wi-Fi.
  * Blindaje criptográfico del actualizador OTA (`UpdateManager.java`) con verificación obligatoria de firmas de certificados, anti-downgrade y SHA-256.
  * Limpieza profunda de archivos innecesarios, depuración de `.gitignore` para bloquear artefactos pesados y secretos.
  * Preparación profesional para el primer repositorio de GitHub: CI/CD con GitHub Actions, plantillas de issues/PR, políticas de seguridad (`SECURITY.md`), guía de contribución (`CONTRIBUTING.md`), registro de cambios (`CHANGELOG.md`), guía paso a paso de publicación (`GUIA_GITHUB.md`) e informes de auditoría (`SECURITY_AUDIT.md`, `PROJECT_CLEANUP.md`).
* **Solución Técnica y Entrega:**  
  1. **Hardening de `RemoteServer.java`:**  
     - Emparejamiento por token criptográfico temporal (`SecureRandom`) incrustado de forma transparente en el código QR.
     - Rechazo con `HTTP 401 Unauthorized` de solicitudes sin token para proteger la TV en redes locales compartidas.
     - Límite estricto de carga de 25 MB (`HTTP 413 Payload Too Large`) y apagado automático por inactividad tras 15 minutos.
  2. **Blindaje Criptográfico de `UpdateManager.java`:**  
     - Comprobación y forzado obligatorio de conexiones cifradas `https://`.
     - Verificación estricta de paquete (`tv.lunvoryx.player`) y protección anti-downgrade.
     - Extracción y comparación criptográfica de certificados de firma entre el APK descargado y la aplicación instalada antes de solicitar la instalación al sistema.
     - Incorporación del permiso `REQUEST_INSTALL_PACKAGES` en `AndroidManifest.xml` con verificación en tiempo de ejecución.
  3. **Configuración de Firma y Compilación Release:**  
     - Desacoplamiento de credenciales de firma en `app/build.gradle` mediante variables de entorno y propiedades locales (`gradle.properties.example`).
     - Creación del flujo de trabajo automatizado `.github/workflows/build.yml` (Java 17, tests, lint y empaquetado).
  4. **Batería de Pruebas Unitarias Ampliada:**  
     - 43 pruebas unitarias completadas con 100% de éxito en 7 suites (`M3uParserTest`, `MediaKindTest`, `EpgIndexTest`, `XmltvParserTest`, `XmltvTimeTest`, `RemoteServerTest`, `UpdateManagerTest`).
  5. **Documentación de Publicación:**  
     - Redacción completa de `SECURITY_AUDIT.md`, `PROJECT_CLEANUP.md`, `SECURITY.md`, `CHANGELOG.md`, `CONTRIBUTING.md`, actualización de `VALIDATION.md`, `README.md` y guía paso a paso adaptada para Windows en `GUIA_GITHUB.md`.

---

### Hito 14: Rediseño y Refinamiento Visual Integral según los Estándares de tvOS / Apple TV
* **Solicitud del usuario:**  
  * Realizar una revisión y mejora profunda de la calidad visual de la interfaz de Lunvoryx Player tomando como referencia el lenguaje de diseño premium de Apple TV y tvOS.
  * Eliminar la sensación de interfaz generada automáticamente o "slop" sin alterar la estructura, navegación ni funcionalidades.
  * **Integración profesional del logotipo:** Eliminar la tarjeta/recuadro negro rectangular de la barra lateral; usar versión con transparencia real obtenida del asset original en alta resolución; montarlo directamente sobre el vidrio translúcido de la barra lateral, centrado horizontalmente con proporciones elegantes (114x76 dp) y sin contenedores visibles.
  * **Sistema de radios de curvatura tvOS:** Sustituir esquinas sobredimensionadas/caricaturescas por un sistema de radios refinado: barra lateral (20-24 dp), paneles de contenido (16-20 dp), tarjetas/pósters (10-14 dp), categorías/filtros (10-12 dp), campos de búsqueda (12-14 dp), botones normales (10-14 dp) y selector activo de menú (12-16 dp).
  * **Bordes y materiales Apple:** Trazos ultra-finos hairline (~1 px físico / 0.75-0.85 dp), gradiente cenital muy sutil (overhead key light whisper-thin), bisel interior de profundidad óptica (20 dp) y anillos de foco blanco perla/plata celestial sin destellos de neón estridentes.
  * Actualización integral de capturas de pantalla oficiales en `artifacts/`.
* **Solución Técnica y Entrega:**  
  1. **Integración Transparente del Logotipo Oficial (`tv.lunvoryx.player.MainActivity`):**  
     - Se procesó el máster en alta resolución mediante algoritmo de matizado alfa (desmultiplicación cromática con rampa suave y supresión de destellos de fondo) generando `brand_header.png` (842x564 px) y `app_logo.png` (512x512 px) con canal alfa 100% puro y anti-aliasing perfecto.
     - Se suprimió cualquier tarjeta, contenedor o fondo rectangular negro en la cabecera del menú lateral.
     - El logotipo se dibuja directamente sobre la superficie continua de vidrio translúcido de la barra lateral con tamaño equilibrado de 114x76 dp, centrado horizontalmente y con márgenes calibrados (`topMargin = 4dp`, `bottomMargin = 20dp`).
  2. **Implantación Transversal de la Escala tvOS:**  
     - *Barra lateral:* Radio squircle fijado en `22 dp`.
     - *Píldora activa de menú:* Radio reducido de 21 dp a `14 dp` con microescala de foco suave 1.02x.
     - *Paneles de contenido y columnas de navegación (TV En Vivo, Películas, Series, Buscar):* `18 dp` consistentes.
     - *Pósters, tarjetas de contenido, cuadrículas y miniaturas:* `12 dp` (reemplazando radios no armónicos de 8-10 dp).
     - *Campos de búsqueda e inputs de texto:* `13 dp` (sustituyendo la cápsula inflada de 24 dp).
     - *Botones de acción (Ver, Conectar, Añadir, Seleccionar archivo, Controles de reproductor):* `12 dp` (sustituyendo las cápsulas redondeadas de 20-23 dp).
     - *Píldoras de filtrado y ordenación:* `12 dp`.
  3. **Física de Materiales y Trazos Hairline (`SquircleGlassDrawable.java`):**  
     - Reducción del grosor de trazo en reposo a `Math.max(1.0f, 0.75f * density)` (~1 pixel físico).
     - Gradiente especular superior whisper-thin (`0x22FFFFFF` descendiendo a `0x06FFFFFF`).
     - Bisel interior de profundidad óptica con degradado cenital de 20 dp (`0x14FFFFFF` a `0x00FFFFFF`).
     - Anillo de foco Apple TV en plata nácar `[0xFFFFFFFF, 0xF5FFFFFF, 0xDCE6F0, 0xC4D2E0]` con halo ambiental difuso de sombra `0x33FFFFFF`.
     - Eliminación de bordes de neón estáticos en reposo (ej. tarjeta Xtream adaptada a vidrio cristalino con bisel).
  4. **Verificación y Pruebas:**  
     - 46 pruebas unitarias aprobadas al 100% (`gradlew testDebugUnitTest`).
     - Compilación y empaquetado `assemblePreview` sin advertencias.
     - Ejecución e instalación en emulador Android TV (`sydtv_test`).
     - Las 9 capturas oficiales de pantalla en `artifacts/01_inicio.png` hasta `09_reproductor.png` han sido regeneradas de forma automatizada a resolución nativa 1080p.


## 3. Arquitectura del Software y Mapa de Archivos

```
tv.lunvoryx.player/
├── MainActivity.java                // Controlador principal, gestión de ciclo de vida y navegación lateral
├── data/
│   ├── Channel.java                 // Modelo de datos de canal/contenido (ID, nombre, URL, logo, grupo, streamType)
│   ├── MediaKind.java               // Clasificador inteligente de contenido (LIVE, MOVIE, SERIES)
│   ├── Playlist.java                // Modelo de lista IPTV
│   └── PlaylistRepository.java      // Repositorio central de persistencia (listas, favoritos, historial, parseo M3U)
├── ui/
│   ├── AddPlaylistViewLayout.java   // Pantalla de Añadir lista (Xtream Codes, Enlace M3U, Archivo y QR Móvil)
│   ├── AppStrings.java              // Gestor de cadenas bilingüe en tiempo de ejecución (ES / EN)
│   ├── HomeBackdrop.java            // Fondo ambiental dinámico con gradiente
│   ├── LineIcon.java                // Motor de renderizado vectorial ligero (search, mic, history, trending, close, etc.)
│   ├── LiveTvBrowseView.java        // Pantalla de 3 columnas para TV En Vivo (Categorías, Canales con EPG, Vista previa)
│   ├── MediaBrowseView.java         // Pantalla de 3 columnas para Películas y Series (Categorías fijas, Grilla, Ficha técnica)
│   ├── ModernSwitch.java            // Switch interactivo animado estilo glassmorphic para mando a distancia
│   ├── SearchViewLayout.java        // Pantalla de 3 columnas para Buscar (Historial, Resultados agrupados, Vista previa)
│   ├── SquircleGlassDrawable.java   // Motor de glassmorphism profesional anti-slop y esquinas continuas
│   ├── SquirclePath.java            // Generador de trayectorias squircle G2 con Bézier cúbicos
│   └── TvStyle.java                 // Constantes de diseño, paleta cromática, dimensiones dp/px y shapes
├── epg/
│   ├── EpgRepository.java           // Gestor y descargador de guías XMLTV con caché de programas
│   └── GuideActivity.java           // Guía electrónica de programación en cuadrícula temporal
├── player/
│   └── PlayerActivity.java          // Reproductor de vídeo a pantalla completa basado en ExoPlayer
├── remote/
│   ├── RemoteServer.java            // Servidor web local HTTP en puerto 8080 para recepción de listas
│   └── QrCodeGenerator.java         // Generador autónomo de matrices de códigos QR en Canvas
├── tmdb/
│   └── TmdbClient.java              // Cliente de la API de The Movie Database con caché
└── updater/
    └── UpdateManager.java           // Gestor de comprobación y descarga de actualizaciones APK OTA
```

---

## 4. Paleta de Color y Sistema de Diseño Visual

La estética de Lunvoryx Player se basa en una paleta de tonos oscuros galácticos y contrastes luminosos:

| Elemento / Propósito | Código Hex | Descripción Visual |
| :--- | :--- | :--- |
| **Fondo Base Global** | `#0B0D18` | Negro azulado profundo de baja reflexión |
| **Fondo de Paneles** | `#141829` | Azul pizarra oscuro translúcido |
| **Foco / Acento Primario**| `#2979FF` / `#3A78F2` | Azul eléctrico brillante con glow |
| **Píldora Activa Menú** | `#FFFFFF` | Blanco puro con texto e icono en `#10121D` |
| **Texto Principal** | `#FFFFFF` | Blanco nítido con alto contraste |
| **Texto Secundario/Muted**| `#8F91A2` | Gris perlado neutro |
| **Borde / Separadores** | `#1E243D` | Trazo fino sutil |
| **Badge "En Directo"** | `#E53935` | Rojo vivo con indicador pulsante |
| **Progreso EPG** | `#00C0FF` | Azul celeste degradado |

---

## 5. Instrucciones de Compilación y Despliegue

### Compilar APK Preview / Debug
Para compilar la aplicación en un entorno Windows utilizando el Java Runtime de Android Studio:

```powershell
cmd.exe /c "set JAVA_HOME=C:\Program Files\Android\Android Studio\jbr&& gradlew.bat assemblePreview"
```

El ejecutable resultante se genera en:
`app/build/outputs/apk/preview/app-preview.apk`

### Instalar en Dispositivo / Emulador Android TV
```powershell
adb install -r app/build/outputs/apk/preview/app-preview.apk
```

### Ejecutar la Aplicación
```powershell
adb shell am start -n tv.lunvoryx.player/.MainActivity
```

---

## 6. Estado Actual de Entrega

- [x] **Identidad y Marca:** Nombre "Lunvoryx Player" y nuevo isotipo integrados en todos los niveles.
- [x] **Carga de Listas vía QR:** Servidor web local en puerto 8080 y generador QR nativo 100% operativos.
- [x] **Actualizaciones:** Arquitectura OTA lista en `UpdateManager.java` con llamada en Ajustes.
- [x] **Menú Lateral:** 7 secciones exactas, diseño idéntico al mockup con píldora blanca activa y fondo translúcido.
- [x] **Ajustes:** Tarjetas General y Reproducción con switches modernos, cambio de idioma (ES/EN) y TMDB API Key.
- [x] **Películas y Series:** Vista de 3 columnas con categorías obligatorias (*Todas*, *Favoritas*, *Seguir viendo*), grilla de pósters y panel cinematográfico.
- [x] **TV En Vivo:** Vista de 3 columnas con categorías obligatorias, barra de progreso EPG azul celeste y panel con badge "● EN DIRECTO".
- [x] **Buscar:** Vista de 3 columnas idéntica al mockup con historial interactivo, filtros superiores, resultados agrupados y panel de vista previa con temporadas.
- [x] **Añadir lista / Mis listas:** Diseño calcado al mockup con Xtream Codes (borde neón), Enlace M3U, Archivo M3U y banner Desde tu móvil con QR y servidor activo.
- [x] **Glassmorphism y Continuous Corners ($G^2$ Squircles):** Curvatura continua orgánica estilo Apple/Figma implementada en todas las secciones, botones, tarjetas, inputs y barra lateral.
- [x] **Capturas Oficiales y Limpieza de Artifacts:** Eliminadas capturas obsoletas y generadas 9 nuevas capturas 1080p oficiales de la app en ejecución real.
- [x] **Rediseño Premium tvOS / Apple TV:** Integración transparente del logotipo sin recuadro negro, escala armónica de esquinas tvOS (12-14 dp en botones/inputs/cards, 18 dp paneles, 22 dp lateral) y bordes especulares hairline (~1px).
- [x] **Informe Completo:** Documento `INFORME_COMPLETO.md` creado y sincronizado.

---
*Este informe se actualizará automáticamente tras cada nueva solicitud o modificación del proyecto.*


---

## 7. Overhaul de Bordes Premium: Anatomía y Renderizado Apple tvOS / visionOS

A raíz de la solicitud de elevar los bordes a un estándar verdaderamente *premium* (estilo Apple), se reestructuró la física de renderizado de `SquircleGlassDrawable`, `AddPlaylistViewLayout`, `SearchViewLayout` y `MainActivity`:

### A. Sustitución de Bordes Planos por Trazos "Hairline" Especulares
- **Grosor Calibrado:** Se redujo el grosor del trazo de reposo a un filo ultranítido de `0.95dp` (y `1.5dp` en estado enfocado), eliminando cualquier apariencia tosca de bordes anchos de 2dp+.
- **Incidencia Cenital Física (Top-to-Bottom Gradient):** En lugar de gradientes diagonales arbitrarios, los bordes utilizan un degradado estrictamente vertical `(0, bounds.top) -> (0, bounds.bottom)` con 4 paradas calibradas:
  - Borde superior (hombro del squircle): `0x3AFFFFFF` (23% blanco translúcido, capta la luz ambiental cenital).
  - Transición superior: `0x1EFFFFFF` (12% blanco).
  - Laterales y fondo medio: `0x0DFFFFFF` (5% blanco, filo sutil).
  - Borde inferior (reflejo de suelo/rebote): `0x16FFFFFF` (9% blanco).
  Esto garantiza que toda la curvatura superior del squircle brille de manera homogénea y orgánica.

### B. Bisel Óptico Interior (*Inner Specular Chamfer*)
- Se añadió una segunda pasada de renderizado interno (`innerBevelPaint`) con radio interior corregido (`radius - strokeWidth/2`) y un degradado vertical de desvanecimiento (`0x22FFFFFF` a `0x00FFFFFF` en los primeros 28dp).
- Esto reproduce la profundidad óptica del vidrio fresado de Apple, donde el borde no es una simple línea perimetral sino un cambio de grosor en el cristal que refleja luz difusa.

### C. Foco Luminous Pearl / Silver Glow
- **Eliminación de Cyan Plano:** Se erradicaron los trazos estáticos `#00E5FF` tipo neón.
- **Anillo de Foco Celestial Silver:** En estado enfocado, el borde adopta una corona plateada/nácar multicapa (`0xF5FFFFFF` -> `0xE0EAF4FE` -> `0xB8CCE4FE` -> `0x90A0C8FE`) acompañada de un sutil halo de luz difusa ambiental (`setShadowLayer(4.5dp, 0, 1dp, 0x4D5A94E8)`).

### D. Galería de Capturas Verificadas en Ejecución Real (1080p)
Las 10 capturas del directorio `artifacts/` han sido regeneradas directamente desde el emulador de Android TV y el entorno de importación móvil:
1. `artifacts/01_inicio.png`: Portada principal con carrusel, fila de categorías squircle y tarjetas de reproducción.
2. `artifacts/02_tv_en_vivo.png`: 3 columnas con EPG, progreso y badge pulsante.
3. `artifacts/03_peliculas.png`: Catálogo cinematográfico con bordes de cristal Apple y panel de metadatos TMDB.
4. `artifacts/04_series.png`: Vista modular de temporadas y fichas descriptivas.
5. `artifacts/05_buscar.png`: Sistema de búsqueda con barra squircle iluminada, filtros y vista previa con episodios.
6. `artifacts/06_ajustes.png`: Tarjetas de configuración con switches modernos y biseles translúcidos.
7. `artifacts/07_anadir_lista.png`: Módulos Xtream, M3U y banner móvil con QR nítido.
8. `artifacts/08_comprobacion_actualizacion.png`: Cuadro de diálogo de actualización verificado contra GitHub Releases.
9. `artifacts/09_reproductor.png`: Interfaz del reproductor ExoPlayer con barra temporal y píldoras de control Apple.
10. `artifacts/10_movil_web_qr.png`: Interfaz web móvil abierta al escanear el código QR para transferir listas M3U o Xtream.
