# Flinger Local Server

Aplicación Android nativa (Kotlin, API 24+) que levanta un servidor HTTP en el puerto 8080 para servir archivos desde una carpeta seleccionada por el usuario — similar a SimpleServer / http.server de Python, pero en Android.

## Características

- **Servidor HTTP real** usando NanoHTTPD 2.3.2
- **Navegación de directorios** con listado HTML estilo dark-mode
- **Streaming de archivos** con detección automática de MIME type
- **Servicio en segundo plano** con notificación persistente (Foreground Service)
- **Selector de carpeta** via SAF (Storage Access Framework) con permisos persistibles
- **IP local automática** vía WifiManager
- **Botón "Detener"** en notificación para control rápido

## Requisitos

- Android 7.0+ (API 24)
- targetSdk 34 (Android 14)
- JDK 17, Gradle 8.6, AGP 8.3.2

## Dependencias principales

| Librería | Versión |
|---|---|
| NanoHTTPD | 2.3.2 |
| AndroidX AppCompat | 1.6.1 |
| Material Components | 1.11.0 |
| DocumentFile | 1.0.1 |
| ConstraintLayout | 2.1.4 |

## CI/CD

GitHub Actions (`.github/workflows/android.yml`) compila automáticamente el APK Debug en cada push a `main`.

## Build local

```bash
gradle assembleDebug
```

El APK queda en `app/build/outputs/apk/debug/app-debug.apk`.

## Uso del CI Loop (Agente)

```bash
export GITHUB_PERSONAL_ACCESS_TOKEN=tu_token_aqui
python3 ci_loop.py
```
