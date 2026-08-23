# FlurryOS v0.2.0

## Lanzador Android de escritorio

Esta versión sustituye la pantalla Android básica por una interfaz principal Java estilo Ubuntu. Incluye identidad visual FlurryOS, indicador del runtime Android, búsqueda de aplicaciones, cuadrícula con iconos y paquetes, actualización del catálogo, accesos rápidos a Ajustes y Archivos y lanzamiento mediante componentes explícitos.

El servicio Android valida estrictamente la versión, el identificador, el método y los argumentos JSON. Los lanzamientos se resuelven con `PackageManager` y se ejecutan en el hilo principal mediante `Handler`/`Looper`. El servidor de socket abstracto usa un lector limitado a 64 KiB para impedir que una línea JSON excesiva se acumule antes de su validación.

## Traductor Android → Linux

El traductor C++ devuelve ahora una estructura con dominio, backend Linux, acción nativa, ejecutable lógico y argumentos separados. Incluye mapeos para gráficos Wayland/EGL, entrada libinput/evdev, audio PipeWire, almacenamiento FlurryStore, red NetworkManager y ciclo de vida Cuttlefish/ADB.

El traductor no ejecuta comandos. Los nombres `flurry-wayland`, `flurry-input`, `flurry-audio`, `flurry-store`, `flurry-network` y `flurry-runtime` son puntos de integración controlados para una futura capa supervisora basada en `execve` o servicios systemd.

## Aplicaciones Linux añadidas

El perfil de la ISO ahora solicita Firefox, VLC, GNOME Calculator, GNOME Disk Utility, GNOME Screenshot, GNOME System Monitor, Eye of GNOME, Simple Scan, GIMP, LibreOffice Writer, LibreOffice Calc, OpenSSH Client, htop y btop, además del escritorio GNOME existente.

También se crea `/var/lib/flurryos/apks/inbox` con propietario `flurryos` y modo `0750` para el flujo de validación de APKs.

## Validación

La compilación limpia CMake/Ninja del puente C++ terminó correctamente y las tres pruebas pasaron:

```text
bridge_smoke_test   Passed
translator_test     Passed
json_and_apk_test   Passed
100% tests passed, 0 tests failed
```

También pasaron `git diff --check` y `sh -n` sobre el hook de construcción de usuarios de la ISO.

La compilación Android completa no se ejecutó en el entorno de desarrollo porque no hay Android SDK ni Gradle instalados. Por tanto, esta release publica el código fuente Java y no afirma que exista todavía un APK compilado. La compatibilidad universal de APKs y la integración real de una imagen AOSP/Cuttlefish siguen siendo trabajo posterior.
