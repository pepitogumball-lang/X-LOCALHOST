# 🤖 Protocolo Operativo para Manus IA - Proyecto X-LOCALHOST

## 📌 Misión
Tu objetivo es finalizar y pulir el servidor HTTP local **X-LOCALHOST** (antes Flinger Local Server). Este proyecto debe ser una herramienta robusta para servir archivos desde Android a la red local.

---

## 🛠 Instrucciones Críticas

1. **Entorno de Trabajo:** 
   - No compiles localmente. Tu tarea es escribir el código fuente (Kotlin/Java) y la configuración de Gradle.
   - El APK real se genera en la pestaña **Actions** de GitHub mediante el workflow `.github/workflows/android.yml`.

2. **Funcionalidades a Asegurar:**
   - **Servidor Estable:** Basado en NanoHTTPD.
   - **Interfaz Dark Mode:** El listado de archivos en el navegador debe ser elegante y oscuro.
   - **Foreground Service:** El servidor NO debe morir cuando la app se minimiza.
   - **Permisos SAF:** Manejo correcto de Storage Access Framework para Android 11+.

3. **Ciclo de Debugging:**
   - Sube tus cambios al repo.
   - Si el build falla en GitHub Actions: **Descarga los logs**, identifica el error (Gradle, dependencias, sintaxis) y sube el fix inmediatamente.
   - No declares el proyecto como terminado hasta que el APK en Actions sea funcional y descargable.

---

## 📂 Estructura del Proyecto
- `app/src/main/java/com/phlox/simpleserver/` -> Lógica del servidor y UI.
- `app/src/main/res/` -> Layouts y recursos.
- `.github/workflows/android.yml` -> El motor de compilación.

## 🚦 Objetivo de Entrega
Un APK funcional llamado **X-LOCALHOST** que permita elegir una carpeta, iniciar el servidor y ver los archivos desde cualquier navegador en la misma WiFi.

**¡Manos a la obra, Manus! Convierte este servidor en la herramienta definitiva de transferencia local.**
