# FlurryOS v0.3.0-android17-bootstrap

## Objetivo

Esta versión fija el motor Android de FlurryOS en AOSP Android 17 para Cuttlefish x86_64 y añade la integración reproducible de una aplicación de Ajustes propia desde el proceso de construcción de la imagen.

## Implementado

El manifiesto está fijado en `android-17.0.0_r1` con el commit `5bc9a7ce1cd78dd53613bbfd0ebf506e1e4adb0f`. El target definido es `aosp_cf_x86_64_only_phone-userdebug`.

`FlurrySettings` es una aplicación Java de sistema con certificado `platform`, módulo AOSP `Android.bp`, permisos privilegiados allowlisted y una interfaz de configuración inicial. Permite consultar la versión y arquitectura, cambiar brillo y tema, abrir los paneles de Wi-Fi, pantalla y aplicaciones y ejecutar una comprobación inicial de permisos.

El launcher FlurryOS también dispone de `Android.bp` para compilarse directamente en AOSP, junto con `FlurrySettings`, dentro del producto Cuttlefish. El script `os/android/aosp/build-android17.sh` sincroniza el árbol, copia ambos módulos, modifica `PRODUCT_PACKAGES`, compila las imágenes y las copia a `os/dist/android17`.

## Validación realizada

Pasaron el validador estático de manifiestos, la comprobación estructural de fuentes Java, `bash -n` del script AOSP y `git diff --check`.

## Limitación explícita

Este prerelease no contiene todavía `system.img`, `vendor.img` ni un APK compilado. La compilación real de AOSP Android 17 requiere sincronizar el árbol completo, disponer del toolchain Android y contar con espacio, memoria y tiempo suficientes. Cuttlefish requiere virtualización KVM para su ejecución. No se debe interpretar este prerelease como una imagen Android ya compilada o instalada.

La siguiente etapa es ejecutar el script en una máquina de compilación persistente x86_64 y publicar los artefactos Android resultantes, junto con sus hashes.
