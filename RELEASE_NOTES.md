# FlurryOS v0.1.0

## Primera entrega pública

FlurryOS v0.1.0 es una imagen experimental **Ubuntu Noble x86_64** con escritorio GNOME sobre Wayland, apariencia simple inspirada en Ubuntu y soporte para aplicaciones Linux nativas.

La entrega incluye el puente de integración del host escrito en C++ y el esqueleto del lanzador Android escrito en Java. La arquitectura Android no utiliza Waydroid: el siguiente backend previsto es una imagen AOSP/Cuttlefish ejecutada de forma aislada mediante KVM.

## Incluido

- ISO arrancable para pruebas en máquinas virtuales y sistemas UEFI.
- GNOME, Wayland, tema Yaru, fondo y ajustes visuales propios de FlurryOS.
- Servicio C++ con protocolo IPC local, validación de comandos y operaciones controladas de runtime.
- Lanzador Java Android basado en `PackageManager` e intents explícitos.
- Script reproducible de live-build con compatibilidad local para GRUB2 y squashfs grande.
- Pruebas C++ automatizadas para el protocolo del puente.

## Validación

La ISO fue construida desde este repositorio, reconocida como `ISO 9660 CD-ROM filesystem data (bootable)` y acompañada de un archivo SHA-256. El puente C++ compila con CMake/Ninja y `bridge_smoke_test` pasa al 100 %.

## Limitaciones

Esta versión no incluye todavía una imagen AOSP/Cuttlefish ni garantiza ejecución de APKs en el host. El lanzador Java requiere Android SDK/Gradle y una imagen Android compatible para compilarse y ejecutarse. Google Play, DRM, Play Integrity, binarios ARM-only, aceleración 3D completa, cámaras y sensores no están garantizados.

## Descarga de la ISO

GitHub limita el tamaño de cada asset de release a 2 GiB. Por eso la ISO se publica dividida en partes `flurryos-os-noble-amd64.iso.part-*`. Descarga todas las partes en el mismo directorio y reconstruye la imagen con:

```bash
cat flurryos-os-noble-amd64.iso.part-* > flurryos-os-noble-amd64.iso
sha256sum -c flurryos-os-noble-amd64.iso.sha256
```

La ISO completa es un artefacto grande del release y no forma parte del historial Git.

## Compilación

Consulta [`README.md`](README.md) y [`os/docs/BUILD_STATUS.md`](os/docs/BUILD_STATUS.md). La construcción reproducible genera la ISO completa en `os/dist` y mantiene los artefactos grandes fuera de Git.
