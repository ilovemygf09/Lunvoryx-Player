# GUÍA DEFINITIVA: SUBIR LUNVORYX PLAYER A GITHUB Y PUBLICAR ACTUALIZACIONES

Esta guía está diseñada paso a paso para **Windows con PowerShell**, pensada tanto si es la primera vez que usas Git y GitHub como si quieres un procedimiento profesional y seguro.

---

## 0. Conceptos Básicos en 2 Minutos

Para entender con claridad qué estamos haciendo:

* **Git:** Es el programa instalado en tu ordenador que registra los cambios en tus archivos a lo largo del tiempo (como una máquina del tiempo local).
* **GitHub:** Es la plataforma web en la nube donde se guarda una copia remota de tu proyecto para compartirlo, colaborar o distribuir versiones.
* **Commit:** Es una «foto fija» o guardado oficial de tu código en un momento exacto, con un mensaje que explica qué cambiaste.
* **Rama (`branch`):** Es una línea de trabajo independiente. La rama principal se llama `main`.
* **Tag (Etiqueta):** Es un marcapáginas permanente en un commit específico para identificar una versión (por ejemplo: `v1.0.0`).
* **Release:** Es una publicación formal en GitHub asociada a un Tag, donde se redactan las novedades para los usuarios y se adjunta el archivo instalable (`.apk`).

---

## 1. Preparativos en Windows

### Paso 1.1: Comprobar o Instalar Git
Abre **PowerShell** en tu ordenador y escribe:

```powershell
git --version
```

* **Si te devuelve algo como `git version 2.x.x`:** ¡Ya tienes Git instalado! Ve al Paso 1.2.
* **Si te da error («no se reconoce como un comando»):**
  Puedes instalarlo fácilmente desde PowerShell ejecutando:
  ```powershell
  winget install --id Git.Git -e --source winget
  ```
  *(O descargando el instalador oficial desde [git-scm.com](https://git-scm.com/download/win)). Tras instalarlo, cierra y vuelve a abrir PowerShell.*

### Paso 1.2: Configurar tu identidad en Git
Configura tu nombre y el correo con el que te registraste en GitHub (solo se hace una vez):

```powershell
git config --global user.name "Tu Nombre"
git config --global user.email "tu-correo-de-github@ejemplo.com"
```

---

## 2. Crear tu Repositorio en GitHub

1. Entra en tu navegador a tu cuenta de [GitHub](https://github.com) y ve a [https://github.com/new](https://github.com/new) (o pulsa en el botón **+** arriba a la derecha -> **New repository**).
2. **Repository name:** Escribe `Lunvoryx-Player` (o el nombre en minúsculas `lunvoryx-player`).
3. **Description:** Puedes poner `Reproductor IPTV nativo para Android TV con interfaz cinematográfica y glassmorphism`.
4. **Visibilidad (Público vs Privado):**
   * **Recomendado: Public (Público):** Las GitHub Releases públicas permiten que cualquier televisor con la aplicación instalada consulte las versiones y descargue el APK sin necesidad de tokens de acceso ni inicios de sesión en la TV.
   * **Private (Privado):** Solo tú tendrás acceso al código, pero el actualizador del televisor requerirá tokens personales para acceder a la API de GitHub.
5. **Opciones iniciales (IMPORTANTE):**
   * **DESMARCA** *Add a README file*.
   * **DESMARCA** *Add .gitignore*.
   * **DESMARCA** *Choose a license*.
   *(Tu proyecto ya tiene estos archivos preparados y configurados localmente).*
6. Haz clic en **Create repository**.
7. Copia la URL de tu repositorio (aparecerá en la pantalla, con formato `https://github.com/TU_USUARIO/Lunvoryx-Player.git`).

---

## 3. Conectar tu Carpeta Local y Hacer el Primer Commit

Abre PowerShell y sitúate en la carpeta raíz del proyecto:

```powershell
cd c:\Users\David\Videos\IPTVCODEX-ANTIGRAVITY
```

### Paso 3.1: Comprobar qué archivos entrarán
Ejecuta:

```powershell
git status
```

Verás que el `.gitignore` configurado protege automáticamente tus carpetas de compilación, tus capturas temporales y cualquier archivo sensible.

### Paso 3.2: Añadir los archivos y crear el commit inicial

```powershell
# 1. Preparar todos los archivos auditados para el guardado
git add .

# 2. Comprobar que no hay archivos temporales ni keystores en la lista en verde
git status

# 3. Crear el commit oficial de la versión 1.0.0
git commit -m "Lunvoryx Player v1.0.0: version inicial para Android TV"

# 4. Asegurar que la rama principal se llama main
git branch -M main

# 5. Conectar con tu repositorio en GitHub (sustituye TU_USUARIO por tu usuario real de GitHub)
git remote add origin https://github.com/TU_USUARIO/Lunvoryx-Player.git

# (Si ya existiera un remoto previo y quieres corregirlo, usa:)
# git remote set-url origin https://github.com/TU_USUARIO/Lunvoryx-Player.git
```

### Paso 3.3: Subir el proyecto a GitHub por primera vez

```powershell
git push -u origin main
```

> **Autenticación en GitHub:**  
> Windows abrirá una pequeña ventana del asistente de GitHub para iniciar sesión en tu navegador con un solo clic («Sign in with your browser»). Acéptalo y el push se completará al instante.

---

## 4. Cómo Compilar y Firmar el APK para Distribución

Para que el APK pueda instalarse en cualquier televisor y actualizarse en el futuro sin conflictos de firma:

### Opción A: Compilar la Variante Preview (Para Pruebas)
Esta variante aplica toda la optimización y ofuscación R8, no permite depuración y está firmada con la clave de desarrollo de Android:

```powershell
cmd.exe /c "set JAVA_HOME=C:\Program Files\Android\Android Studio\jbr&& gradlew.bat assemblePreview"
```
El APK resultante se genera en:  
`app/build/outputs/apk/preview/app-preview.apk`

---

### Opción B: Compilar con Tu Propia Clave de Firma Oficial (Recomendado para Producción)

1. **Crear tu propio almacén de claves (Keystore) una sola vez:**
   Ejecuta en PowerShell:
   ```powershell
   & "C:\Program Files\Android\Android Studio\jbr\bin\keytool.exe" -genkey -v -keystore c:\Users\David\lunvoryx-release-key.jks -alias lunvoryx-key -keyalg RSA -keysize 2048 -validity 10000
   ```
   *(Te pedirá una contraseña para el almacén y datos básicos de tu organización. Guarda bien esa contraseña).*

2. **Configurar las credenciales sin subirlas a Git:**
   En tu archivo `gradle.properties` (que nunca se sube con contraseñas reales a GitHub) o mediante variables de entorno en tu sesión de PowerShell:
   ```powershell
   $env:RELEASE_STORE_FILE="c:/Users/David/lunvoryx-release-key.jks"
   $env:RELEASE_STORE_PASSWORD="TuPasswordDeAlmacen"
   $env:RELEASE_KEY_ALIAS="lunvoryx-key"
   $env:RELEASE_KEY_PASSWORD="TuPasswordDeClave"
   ```

3. **Compilar el APK de Release firmado:**
   ```powershell
   cmd.exe /c "set JAVA_HOME=C:\Program Files\Android\Android Studio\jbr&& gradlew.bat assembleRelease"
   ```
   El APK oficial se genera en:  
   `app/build/outputs/apk/release/app-release.apk`

4. **Copiar y renombrar el APK para publicarlo:**
   ```powershell
   Copy-Item "app\build\outputs\apk\release\app-release.apk" "artifacts\LunvoryxPlayer-1.0.0.apk"
   ```

---

## 5. Cómo Publicar una Release en GitHub (Paso a Paso)

Cada vez que quieras publicar una versión para que los televisores la detecten e instalen:

### Paso 5.1: Incrementar la versión en el código
En `app/build.gradle`:
* Sube `versionCode` (de `1` a `2`, `3`, etc.).
* Sube `versionName` (de `'1.0.0'` a `'1.0.1'`, `'1.1.0'`, etc.).

### Paso 5.2: Subir el commit a GitHub
```powershell
git add app/build.gradle CHANGELOG.md
git commit -m "Bump version to v1.0.1"
git push origin main
```

### Paso 5.3: Crear la Release en la Web de GitHub
1. Entra a tu repositorio en GitHub y haz clic en la sección derecha **Releases** -> **Draft a new release** (o visita `https://github.com/TU_USUARIO/Lunvoryx-Player/releases/new`).
2. **Choose a tag:** Escribe la versión con una 'v' delante (ejemplo: `v1.0.1`) y haz clic en **Create new tag on publish**.
3. **Release title:** Escribe `Lunvoryx Player v1.0.1`.
4. **Description:** Pega las novedades del `CHANGELOG.md` para que los usuarios las lean.
5. **Adjuntar el APK:** En la caja que dice **Attach binaries by dropping them here or selecting them**, arrastra el archivo APK compilado (`LunvoryxPlayer-1.0.1.apk`).
6. Haz clic en el botón verde **Publish release**.

---

## 6. Comprobación y Verificación de la Actualización en la TV

1. **Al encender el televisor o abrir Lunvoryx Player:**  
   La aplicación consulta automáticamente tu repositorio GitHub. Si detecta la nueva versión, mostrará una ventana flotante con 3 opciones:
   * **Actualizar:** Descarga el APK desde GitHub con barra de progreso, verifica su integridad y firma, y abre el instalador.
   * **No recordar en 24h:** Silencia el aviso durante 24 horas.
   * **No actualizar:** Cierra el aviso en la sesión actual.
2. **Desde Ajustes:**  
   En **Ajustes** -> **Comprobar actualizaciones**, el usuario puede comprobar en cualquier momento el estado de la versión instalada.
3. **Ajuste del Repositorio:**  
   En **Ajustes** -> **Repositorio de GitHub**, verifica que el valor sea `TU_USUARIO/Lunvoryx-Player`.

---

## 7. Preguntas Frecuentes y Resolución de Errores

### ¿Qué hago si me equivoqué en el último commit (antes de hacer push)?
Puedes corregir el mensaje o añadir archivos olvidados con:
```powershell
git commit --amend -m "Nuevo mensaje corregido"
```

### ¿Cómo consulto el historial de cambios en cualquier momento?
```powershell
git log --oneline -n 5
```

### ¿Qué hago si quiero volver a un estado anterior?
Para ver el código tal como estaba en un commit o tag anterior sin borrar nada:
```powershell
git checkout v1.0.0
# Y para volver a la versión más reciente:
git checkout main
```

### ¿Qué pasa si GitHub rechaza mi push por permisos o autenticación?
Asegúrate de haber iniciado sesión con el usuario propietario del repositorio. En GitHub, ve a **Settings** -> **Developer Settings** -> **Personal Access Tokens (Tokens classic)** y crea un token con permiso `repo` si tu terminal te pide contraseña manual.
