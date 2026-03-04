# Compilación para Android, Linux y Windows

Este repositorio contiene:

- `:app` y `:androidApp` para Android.
- `:desktopApp` para escritorio JVM (usable en Linux y Windows).

## Requisitos

- JDK 17 (recomendado Temurin/OpenJDK 17).
- Android SDK instalado y `ANDROID_HOME`/`ANDROID_SDK_ROOT` configurado.
- Para builds firmadas Android: `keystore` y variables de firma (si aplica en tu flujo).

> Si ves errores como `IllegalArgumentException: 25.0.1`, estás usando una versión de Java no compatible con este build. Cambia a JDK 17.

---

## Android

### APK debug

```bash
./gradlew :app:assembleStandardDebug
```

APK resultante (ruta típica):

```text
app/build/outputs/apk/standard/debug/
```

### APK release

```bash
./gradlew :app:assembleStandardRelease
```

APK(s) resultantes:

```text
app/build/outputs/apk/standard/release/
```

---

## Linux (desktop)

Genera distribución ejecutable JVM para Linux:

```bash
./gradlew :desktopApp:installDist
```

Salida:

```text
desktopApp/build/install/desktopApp/
```

Ejecutable Linux:

```bash
desktopApp/build/install/desktopApp/bin/desktopApp
```

También puedes crear el JAR:

```bash
./gradlew :desktopApp:jar
```

---

## Windows (desktop)

El módulo desktop es JVM, por lo que puedes compilarlo en Linux/CI y ejecutarlo en Windows con JRE/JDK 17.

```bash
./gradlew :desktopApp:installDist
```

El script de Windows queda en:

```text
desktopApp/build/install/desktopApp/bin/desktopApp.bat
```

Para compilar directamente en Windows, ejecuta lo mismo en PowerShell/CMD dentro del repo.

---

## Build completa rápida

```bash
./gradlew :app:assembleStandardDebug :desktopApp:installDist
```

Con eso obtienes Android + distribución desktop (Linux/Windows script).
