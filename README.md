# FlurryOS

FlurryOS es una distribución experimental **Ubuntu x86_64** orientada a ejecutar aplicaciones Linux nativas y, mediante una capa separada, aplicaciones Android sin Waydroid. El escritorio usa GNOME sobre Wayland con una apariencia simple inspirada en Ubuntu. El proyecto combina un puente del sistema en C++ con un lanzador Android en Java.

> **Estado:** prototipo experimental. La ISO inicial es utilizable para pruebas en máquinas virtuales y arranque UEFI, pero la compatibilidad completa con APKs requiere integrar y validar una imagen AOSP/Cuttlefish real.

## Arquitectura

El host Ubuntu ejecuta las aplicaciones Linux normales y el escritorio. La futura instancia Android se ejecutará de forma aislada con AOSP/Cuttlefish sobre KVM. El servicio C++ coordina el ciclo de vida del runtime y acepta únicamente operaciones explícitas; el componente Java vive dentro del entorno Android y usa `PackageManager` e intents para descubrir y lanzar APKs.

FlurryOS **no incluye Waydroid**. Tampoco pretende reimplementar ART o todo el framework Android en C++; esa función la proporciona AOSP dentro del runtime virtualizado.

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

## Estado de la capa Android

El lanzador Java es el componente Android inicial. Para compilarlo hacen falta Android SDK, Gradle y una imagen/dispositivo AOSP compatible. El controlador C++ ya separa las operaciones `status`, `start`, `stop`, `install` y `launch`, pero la integración de Cuttlefish y la comunicación de ventanas, audio, portapapeles y archivos se desarrollarán por etapas.

Consulta [`os/docs/BUILD_STATUS.md`](os/docs/BUILD_STATUS.md) y [`docs/os_architecture_notes.md`](docs/os_architecture_notes.md) para conocer las validaciones y limitaciones actuales.

## Licencia

Este repositorio contiene una base experimental. Los componentes derivados de Ubuntu, GNOME, Android y otras dependencias conservan sus respectivas licencias; antes de redistribuir imágenes modificadas deben revisarse sus avisos y condiciones.
