# Fuentes y procedencia de recursos

Registro de los recursos incorporados a Lunvoryx Player el 16 de noviembre de 2026. Los vídeos de prueba se reproducen desde servidores externos; sus archivos no se incluyen en el APK. La lista se crea únicamente cuando se selecciona **Lista de prueba**.

## Vídeos de prueba

| Obra | Autoría y licencia | Fuente de reproducción |
| --- | --- | --- |
| Big Buck Bunny, 2008 | © 2008 Blender Foundation. [Proyecto y atribución](https://peach.blender.org/about/). [CC BY 3.0](https://creativecommons.org/licenses/by/3.0/). | [HLS de prueba alojado por Mux](https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8) |
| Sintel, 2010 | © Blender Foundation. [Proyecto Durian y atribución](https://durian.blender.org/sharing/). [CC BY 3.0](https://creativecommons.org/licenses/by/3.0/). | [MPEG-DASH del catálogo Shaka Player](https://storage.googleapis.com/shaka-demo-assets/sintel/dash.mpd) |
| Tears of Steel, 2012 | Blender Foundation / Blender Institute. [Proyecto Mango y licencia](https://mango.blender.org/about/). [CC BY 3.0](https://creativecommons.org/licenses/by/3.0/). | [HLS de prueba alojado por Mux](https://test-streams.mux.dev/tos_ismc/main.m3u8) |

Los enlaces HLS proceden del [catálogo de pruebas de Mux](https://test-streams.mux.dev/). La fuente DASH y las imágenes siguientes se referencian en el [catálogo oficial de demostraciones de Shaka Player](https://github.com/shaka-project/shaka-player/blob/main/demo/common/assets.js). Lunvoryx Player no modifica los vídeos ni recorta sus créditos. Las condiciones de los proyectos Blender exigen conservar los créditos completos cuando se muestra la obra completa.

Las miniaturas se solicitan a sus URL originales y se adaptan al tamaño de la tarjeta al mostrarse:

- [Big Buck Bunny](https://storage.googleapis.com/shaka-asset-icons/big_buck_bunny.png), © 2008 Blender Foundation, proyecto Peach.
- [Sintel](https://storage.googleapis.com/shaka-asset-icons/sintel.png), © Blender Foundation, proyecto Durian.
- [Tears of Steel](https://storage.googleapis.com/shaka-asset-icons/tears_of_steel.png), Blender Foundation, proyecto Mango.

Estas atribuciones corresponden a las obras. No implican patrocinio ni asociación de Blender, Shaka o Mux con Lunvoryx Player. Las marcas y logotipos conservan sus derechos correspondientes.

## Imágenes generadas para la interfaz

Los siguientes PNG se generaron con la herramienta integrada **imagegen** de OpenAI. Son imágenes sintéticas, no fotografías de archivo ni fotogramas de los vídeos anteriores. Los cuatro archivos miden 1672 × 941 píxeles. Se copiaron las salidas PNG originales sin edición ni recorte de archivo; la interfaz aplica escalado, encuadre y capas de color durante el renderizado. En todas las llamadas se utilizó `transparent_background: false`.

| Archivo en `app/src/main/res/drawable-nodpi/` | Uso | Referencia |
| --- | --- | --- |
| `hero_palma.png` | Fondo panorámico de la pantalla de inicio | Imagen de referencia de Android TV adjuntada por el usuario en esta conversación |
| `category_live.png` | Categoría TV en vivo; también base desaturada de Ajustes | Generación a partir de texto |
| `category_movies.png` | Categoría Películas y fondo de tarjetas sin miniatura | Generación a partir de texto |
| `category_series.png` | Categoría Series | Generación a partir de texto |

La referencia visual proporcionada por el usuario no se distribuye como archivo en este repositorio. No se le atribuye aquí una licencia ni una autoría no verificadas. El identificador exacto de modelo y la semilla no quedaron expuestos en el registro disponible.

### Prompt exacto de `hero_palma.png`

```text
Extract and reconstruct ONLY the panoramic photographic background of the supplied Android TV UI reference. Create a clean wide 16:9 landscape image of Palma de Mallorca cathedral and waterfront at dramatic pink/violet dusk, warm illuminated gothic cathedral on the RIGHT half of frame, palm trees, sunset reflections across calm water. Match the scene and colors of the reference hero very closely. The LEFT half should be darker open water and atmospheric distant mountains/sky, clear negative space for real UI to be overlaid later. Fill entire image with continuous real-looking photograph, not a UI screenshot. Remove ALL text, logos, controls, icons, menu panels, cards, people/TV posters, badges, pills, borders, pagination dots and artificial UI gradients. Do not recreate the TV interface. This is a production background bitmap for a native app.
```

### Prompt exacto de `category_live.png`

```text
Photorealistic still photograph for an Android TV category tile, wide 16:9. A sophisticated dark home cinema living room, navy blue velvet sofa on right, a television screen out of frame casting soft blue light from left, cinematic low key lighting, subtle depth of field, tastefully styled, dark empty center for white UI icon to be added later. No text, no icons, no logos, no UI, no people.
```

### Prompt exacto de `category_movies.png`

```text
Photorealistic cinematic still photograph for an Android TV movie category tile, wide 16:9. A red and cream striped cardboard tub of popcorn on the lower right in a dark red home cinema, out of focus burgundy plush theater seating, warm moody ruby lighting. Main center and left very dark negative space for white UI icon to be added later. No text, no logos, no people, no UI.
```

### Prompt exacto de `category_series.png`

```text
Photorealistic interior photograph for an Android TV series category tile, wide 16:9. Cozy stylish deep purple velvet couch with a soft plum cushion on right, lavender cinematic indirect light from left, low key elegant dark home cinema atmosphere, dark negative space at center for UI icon later. No text, no logos, no people, no UI.
```

## Otros elementos

La marca Lunvoryx Player, los iconos lineales, el icono de lanzamiento y el banner Android TV están definidos en el código y en recursos vectoriales del proyecto. El símbolo común es un televisor de contorno blanco con antena y triángulo de reproducción sobre azul oscuro. El rótulo del banner se convirtió a trazados vectoriales a partir de Segoe UI Bold instalada en el entorno de desarrollo. La tipografía de la interfaz utiliza las fuentes del sistema Android; no se distribuyen archivos de fuentes. Los logotipos de canales importados proceden de las URL `tvg-logo` de cada lista del usuario, no de un catálogo de Lunvoryx Player.
