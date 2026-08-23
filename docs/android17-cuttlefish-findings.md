# Hallazgos verificados para Android 17 + Cuttlefish

Fecha de consulta: 23 de agosto de 2026.

## Android 17 y AOSP

La documentación oficial de AOSP publica notas de Android 17 y señala que, desde 2026, para compilar y contribuir se debe utilizar el manifiesto `android-latest-release`, que referencia el release más reciente publicado en AOSP. Fuente: https://source.android.com/docs/whatsnew/android-17-release

La documentación oficial de Android Developers indica que Android 17 alcanzó estabilidad de plataforma durante 2026 y que el release está disponible. Fuente: https://developer.android.com/about/versions/17

Para FlurryOS se debe registrar el manifiesto exacto usado durante la compilación —commit/tag y fecha— en vez de depender indefinidamente de `android-latest-release`, porque esta rama puede avanzar hacia releases posteriores.

## Cuttlefish y KVM

La guía oficial de Cuttlefish describe el dispositivo como una máquina virtual dependiente de la virtualización del host. La disponibilidad de KVM se puede comprobar con:

```bash
grep -c -w "vmx\\|svm" /proc/cpuinfo
find /dev -name kvm
```

Fuente: https://source.android.com/docs/devices/cuttlefish/get-started

## Decisión de arquitectura

El target previsto es AOSP/Cuttlefish x86_64. La imagen debe construirse para la misma versión Android que se documente en el manifiesto y debe contener la app de Ajustes propia como aplicación del sistema, no instalarla después como APK de usuario. Eso permite configurar desde el primer arranque aspectos del dispositivo virtual y mantener las capacidades privilegiadas bajo control del sistema.
