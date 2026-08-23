# FlurryOS

FlurryOS es una distribución experimental **Ubuntu x86_64** orientada a ejecutar aplicaciones Linux nativas y, mediante una capa separada, aplicaciones Android sin Waydroid. El escritorio usa GNOME sobre Wayland con una apariencia simple inspirada en Ubuntu. El proyecto combina un puente del sistema en C++ con un lanzador Android en Java.

> **Estado:** prototipo experimental. La ISO inicial es utilizable para pruebas en máquinas virtuales y arranque UEFI, pero la compatibilidad completa con APKs requiere integrar y validar una imagen AOSP/Cuttlefish real.

## Arquitectura

El host Ubuntu ejecuta las aplicaciones Linux normales y el escritorio. La futura instancia Android se ejecutará de forma aislada con AOSP/Cuttlefish sobre KVM. El servicio C++ coordina el ciclo de vida del runtime y acepta únicamente operaciones explícitas; el componente Java vive dentro del entorno Android y usa `PackageManager` e intents para descubrir y lanzar APKs.

FlurryOS **no incluye Waydroid**. El motor Android fijado para la siguiente imagen es **Android 17 AOSP**, compilado para `aosp_cf_x86_64_only_phone-userdebug` sobre Cuttlefish/KVM. Tampoco pretende reimplementar ART o todo el framework Android en C++; esa función la proporciona AOSP dentro del runtime virtualizado.

## Compilar

En Ubuntu 24.04 x86_64 instala las herramientas base:

```bash
sudo apt-get update
sudo apt-get install -y live-build debootstrap squashfs-tools xorriso syslinux-utils cmake ninja-build g++
```

Compila la ISO con:

```bash
sudo WORK_DIR="$PWD/os/.live-build" \
  OUTPUT_DIR="$PWD/os/dist" \
  ./os/build/build-iso.sh
```

El script contiene una copia local del ensamblador compatible con GRUB2 y archivos squashfs grandes. Los directorios de trabajo y las imágenes generadas están excluidos de Git.

## Validar el puente C++

```bash
cmake -S os/host-bridge -B /tmp/flurryos-host-bridge -G Ninja
cmake --build /tmp/flurryos-host-bridge
ctest --test-dir /tmp/flurryos-host-bridge --output-on-failure
```

## Lanzador Android con interfaz de escritorio

El módulo `os/android-launcher` incluye una interfaz principal Java con estética Ubuntu: cabecera FlurryOS, indicador del runtime Cuttlefish, búsqueda, cuadrícula de aplicaciones con iconos, accesos rápidos a Ajustes y Archivos y lanzamiento mediante actividades explícitas. La interfaz usa widgets del framework Android para mantenerse compatible con una imagen AOSP/Cuttlefish mínima.

El servicio `BridgeService` atiende el socket abstracto `flurryos-bridge`; `BridgeSocketServer` limita cada JSON Line a 64 KiB y el lanzamiento se despacha al hilo principal de Android. El host C++ puede conectarse mediante `adb forward tcp:6521 localabstract:flurryos-bridge`.

## Traductor Android-x86-like

El puente C++ expone un traductor de dominios Android hacia backends Linux controlados. La interfaz de prueba usa el socket del daemon:

```text
TRANSLATE graphics egl
TRANSLATE input events
TRANSLATE audio output
TRANSLATE storage shared
TRANSLATE network virtual
TRANSLATE runtime adb
CAPABILITIES
```

Las traducciones iniciales son `graphics -> Wayland/EGL/Mesa`, `input -> libinput/evdev`, `audio -> PipeWire/ALSA`, `storage -> FlurryStore`, `network -> NetworkManager` y `runtime -> Cuttlefish/ADB`. El traductor ahora devuelve una acción nativa, un ejecutable lógico y argumentos separados; no ejecuta shell ni convierte bytecode DEX en código Linux. Esta separación permite que un supervisor posterior aplique políticas antes de resolver cada backend.

Consulta [`docs/android-api-translator.md`](docs/android-api-translator.md) para el contrato, la tabla de mapeos y sus límites.

## Aplicaciones Linux incluidas

El perfil de la ISO incluye GNOME Files, Terminal, Settings, Firefox, VLC, Calculadora, Discos, Capturas, Monitor del sistema, Visor de imágenes, Simple Scan, GIMP, LibreOffice Writer/Calc, OpenSSH Client, htop y btop. El catálogo puede ampliarse modificando [`os/config/package-list.txt`](os/config/package-list.txt) antes de reconstruir la ISO.

## Android 17 y configuración inicial

La versión de Android no se selecciona dinámicamente. FlurryOS fija el manifiesto AOSP `android-17.0.0_r1` mediante `os/android/aosp/android-version.env`, incluyendo el commit del manifiesto para que una release no dependa de una rama móvil. El objetivo es `aosp_cf_x86_64_only_phone-userdebug`.

La aplicación `os/android-settings` se integra como `FlurrySettings`, con certificado `platform`, `privileged: true` y `product_specific: true`. Se añade a `PRODUCT_PACKAGES` antes de compilar, por lo que queda dentro de la imagen Android desde el primer arranque. No es una APK de usuario instalada al azar después de iniciar el sistema: es una aplicación de Ajustes del sistema diseñada para configuración inicial de brillo, tema, conectividad, pantalla, aplicaciones y diagnóstico del runtime.

Para comprobar herramientas y recursos sin descargar AOSP:

```bash
./os/android/aosp/build-android17.sh --check-only
```

Para construir la imagen Android 17 en una máquina de desarrollo con espacio y memoria suficientes:

```bash
AOSP_DIR="$PWD/os/.aosp-android17" \
  JOBS="$(nproc)" \
  ./os/android/aosp/build-android17.sh
```

El script sincroniza el manifiesto fijado, copia `FlurrySettings` al árbol AOSP, modifica el producto Cuttlefish, ejecuta Soong y copia `system.img`, `vendor.img`, `userdata.img` y `boot.img` a `os/dist/android17`. La compilación real requiere `repo`, Java, Python, el árbol AOSP completo y soporte KVM para ejecutar Cuttlefish; no se realizó dentro del sandbox actual por no disponer de ese toolchain ni del espacio requerido.

Consulta [`os/android/aosp/android-version.env`](os/android/aosp/android-version.env), [`os/android/aosp/build-android17.sh`](os/android/aosp/build-android17.sh), [`docs/android17-cuttlefish-findings.md`](docs/android17-cuttlefish-findings.md), [`os/docs/BUILD_STATUS.md`](os/docs/BUILD_STATUS.md) y [`docs/os_architecture_notes.md`](docs/os_architecture_notes.md) para conocer el procedimiento y las limitaciones actuales.

## Licencia

Este repositorio contiene una base experimental. Los componentes derivados de Ubuntu, GNOME, Android y otras dependencias conservan sus respectivas licencias; antes de redistribuir imágenes modificadas deben revisarse sus avisos y condiciones.
