# X-LOCALHOST

**X-LOCALHOST** es una solución profesional para Android (Kotlin, API 24+) que permite desplegar un servidor HTTP robusto en el puerto 8080. Diseñada para la eficiencia y la portabilidad, permite servir archivos locales y gestionar peticiones web directamente desde el dispositivo móvil.

## Características Principales

*   **Motor HTTP de Alto Rendimiento**: Implementación basada en NanoHTTPD 2.3.2 para una gestión de peticiones estable.
*   **Gestión de Directorios Avanzada**: Interfaz de listado HTML optimizada con soporte para modo oscuro.
*   **Transmisión de Datos Optimizada**: Streaming de archivos con detección automática de tipos MIME para una compatibilidad total.
*   **Arquitectura de Servicio Persistente**: Ejecución en segundo plano mediante Foreground Service con notificaciones de control integradas.
*   **Seguridad y Acceso**: Integración con Storage Access Framework (SAF) para una gestión de permisos granular y segura.
*   **Conectividad Inteligente**: Detección automática de IP local y gestión dinámica de interfaces de red.

## Especificaciones Técnicas

*   **Compatibilidad**: Android 7.0 (API 24) o superior.
*   **SDK de Destino**: Android 14 (API 34).
*   **Entorno de Desarrollo**: JDK 17, Gradle 8.6, AGP 8.3.2.

## Dependencias de Grado Empresarial

| Componente | Versión | Propósito |
| :--- | :--- | :--- |
| **NanoHTTPD** | 2.3.2 | Motor de servidor HTTP embebido. |
| **AndroidX AppCompat** | 1.6.1 | Compatibilidad de componentes de interfaz. |
| **Material Components** | 1.11.0 | Implementación de estándares de diseño Material. |
| **DocumentFile** | 1.0.1 | Abstracción de acceso a archivos SAF. |
| **Jetpack Compose** | 1.6.x | Framework moderno para la interfaz de usuario. |

## Integración Continua (CI/CD)

El proyecto utiliza **GitHub Actions** para garantizar la calidad del código. Cada actualización en la rama principal activa un flujo de trabajo (`.github/workflows/android.yml`) que compila y verifica la integridad del artefacto APK.

## Documentación Legal y Licencia

Para consultar los términos de uso, exenciones de responsabilidad y la licencia MIT oficial de este software, acceda a la carpeta [docs/](docs/).

## Proceso de Compilación

Para generar una compilación local, ejecute el siguiente comando desde la raíz del proyecto:

```bash
./gradlew assembleDebug
```

El artefacto resultante se ubicará en: `app/build/outputs/apk/debug/app-debug.apk`.

---
© 2024 Flinger Apps Corporation. Todos los derechos reservados.
