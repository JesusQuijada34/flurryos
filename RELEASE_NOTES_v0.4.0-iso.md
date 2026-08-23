# FlurryOS v0.4.0 — Bootable Noble ISO

Esta versión publica una ISO arrancable de FlurryOS basada en Ubuntu Noble amd64, con escritorio GNOME/Wayland, tema Yaru, herramientas Android/Cuttlefish, aplicaciones Linux y el daemon C++ `flurryos-bridge` empaquetado dentro de `/usr/lib/flurryos`.

## Contenido

La imagen incluye Firefox, VLC, Calculadora GNOME, Discos, Capturas, Monitor del sistema, visor de imágenes, Simple Scan, GIMP, LibreOffice Writer/Calc, OpenSSH, htop, btop, CMake, Ninja, ADB, QEMU/KVM y las herramientas del runtime FlurryOS. El directorio `/var/lib/flurryos/apks/inbox` es creado por el hook de instalación con permisos restringidos.

El daemon C++ se compila durante el proceso de la ISO y se instala como:

```text
/usr/lib/flurryos/flurryos-bridge
/etc/systemd/system/flurryos-bridge.service
```

## Verificación

La ISO generada es ISO 9660 con volumen `FLURRYOS_OS`, contiene kernel e initramfs Noble bajo `/casper`, y tiene un registro El Torito BIOS válido:

```text
flurryos-os-noble-amd64.iso: ISO 9660 CD-ROM filesystem data 'FLURRYOS_OS' (bootable)
El Torito boot img: BIOS /boot/grub/grub_eltorito
```

El checksum se valida con:

```bash
sha256sum -c flurryos-os-noble-amd64.iso.sha256
```

## Instalación

Escribe la ISO en una memoria USB desde Linux con una herramienta de imágenes de disco, por ejemplo:

```bash
sudo dd if=flurryos-os-noble-amd64.iso of=/dev/sdX bs=16M status=progress conv=fsync
```

Sustituye `/dev/sdX` por el dispositivo USB completo, no por una partición. La operación destruye el contenido del dispositivo de destino.

## Alcance Android

Esta ISO es el sistema host Ubuntu/FlurryOS. No contiene todavía `system.img`, `vendor.img` ni una imagen AOSP Android 17 compilada. La integración AOSP/Cuttlefish y `FlurrySettings` permanece preparada en el repositorio para ejecutarse en un host de compilación con los requisitos oficiales de AOSP y KVM disponible.

La validación de esta release confirma arranque BIOS mediante El Torito. No se afirma compatibilidad UEFI hasta generar y comprobar un catálogo EFI explícito.
