# Guía de Contribución (Contributing Guide)

¡Gracias por tu interés en colaborar con **Lunvoryx Player**! Para mantener un código limpio, seguro y optimizado para dispositivos Android TV, sigue las pautas indicadas a continuación.

---

## 1. Requisitos de Desarrollo

- **JDK:** Java 17 (OpenJDK / Temurin / Android Studio JBR).
- **Android SDK:** Compile SDK 37, Min SDK 23 (Android 6.0 Marshmallow+), Target SDK 36.
- **Gradle:** Versión 9.6.0 (incluida a través de `gradlew`).
- **Dispositivo de prueba:** Android TV real (Google TV / Fire OS) o Emulador Android TV con perfil de control D-Pad.

---

## 2. Flujo de Trabajo con Git

1. Haz un fork del repositorio oficial en GitHub.
2. Clona tu bifurcación localmente:
   ```powershell
   git clone https://github.com/TU_USUARIO/Lunvoryx-Player.git
   cd Lunvoryx-Player
   ```
3. Crea una rama descriptiva para tu cambio:
   ```powershell
   git checkout -b feature/nombre-de-la-mejora
   ```
4. Realiza tus modificaciones respetando la arquitectura del proyecto.

---

## 3. Normas de Calidad y Seguridad

- **Navegación 100% accesible con D-Pad:** Toda vista, botón o tarjeta interactiva debe tener un estado de foco claramente visible con el mando a distancia (`SquircleGlassDrawable`).
- **Rendimiento en hardware modesto:** Prioriza un consumo de memoria contenido y evita crear objetos innecesarios en bucles de renderizado o listas grandes.
- **Seguridad primero:**
  - NUNCA incluyas API keys, contraseñas, URLs privadas de listas ni claves de firma en el código fuente ni en los commits.
  - Toda conexión externa de actualización o metadatos debe exigir HTTPS.
  - Respeta las restricciones del servidor HTTP local y la protección por token criptográfico.
- **Compatibilidad de diseño:** Mantén la coherencia visual del sistema glassmorphism y esquinas continuas (squircles con curvatura $G^2$).

---

## 4. Ejecución de Pruebas y Compilación

Antes de enviar un Pull Request, ejecuta obligatoriamente en tu terminal:

```powershell
# Pruebas unitarias en la JVM
.\gradlew.bat testDebugUnitTest

# Análisis estático Android Lint
.\gradlew.bat lintPreview

# Compilación de la variante Preview
.\gradlew.bat assemblePreview
```

Todos los comandos deben finalizar con éxito (`BUILD SUCCESSFUL`) y 0 errores.

---

## 5. Envío del Pull Request

1. Sube tu rama a tu fork:
   ```powershell
   git push -u origin feature/nombre-de-la-mejora
   ```
2. Abre un **Pull Request** hacia la rama `main` del repositorio oficial.
3. Rellena la plantilla de Pull Request detallando los cambios introducidos y las pruebas realizadas.
