# Traductor Android-x86-like de FlurryOS

## Objetivo

El traductor de FlurryOS será una **capa de adaptación de plataforma**, no un intérprete que convierta cada instrucción Android en una llamada Linux. Android-x86 define su objetivo como portar AOSP a plataformas x86 y actuar como un BSP para PCs comunes [1]. FlurryOS seguirá esa idea en versión modular: Android conserva ART, el framework y sus APIs; C++ adapta los servicios de plataforma al escritorio Ubuntu/Wayland.

## Capas de adaptación

| Dominio Android | Adaptador FlurryOS | Backend Linux inicial | Resultado esperado |
|---|---|---|---|
| Gráficos | `GraphicsAdapter` | Wayland/EGL/Mesa o superficie Cuttlefish | Superficie Android visible en el escritorio |
| Entrada | `InputAdapter` | libinput/evdev y Wayland | Teclado, ratón y eventos táctiles normalizados |
| Audio | `AudioAdapter` | PipeWire/ALSA | Reproducción y captura con dispositivos del host |
| Almacenamiento | `StorageAdapter` | `/var/lib/flurryos/android` y directorio compartido controlado | Datos Android aislados y transferencia explícita |
| Red | `NetworkAdapter` | NetworkManager y red virtual Cuttlefish | Conectividad Android sin acceso arbitrario al host |
| Ciclo de vida | `RuntimeAdapter` | Cuttlefish/KVM, ADB y systemd | Inicio, estado, instalación, lanzamiento y parada |
| API entre procesos | `IpcAdapter` | Socket Unix, credenciales del proceso y mensajes validados | Comunicación segura entre escritorio y runtime |

## Por qué no es una traducción completa

El framework Android accede a hardware mediante HALs con interfaces estables; en versiones modernas, los HALs se ejecutan como servicios binderizados y AIDL es la interfaz preferida para nuevos HALs [2]. Por ello, la implementación correcta es adaptar servicios concretos y exponerlos al framework Android, no interceptar llamadas Java aleatorias en el host Linux.

El C++ de FlurryOS se ocupará de la orquestación del runtime, las superficies del host, la entrada, el audio, el almacenamiento compartido y la comunicación IPC. ART y el framework Android continuarán ejecutándose dentro de AOSP/Cuttlefish. Java/JNI servirá para las partes que necesiten conocer `PackageManager`, intents, permisos y ciclo de vida de actividades.

## Contrato del traductor

El módulo debe recibir solicitudes normalizadas y devolver resultados explícitos. La primera interfaz propuesta es:

```text
status
start
stop
install <apk-permitido>
launch <package> [activity]
capabilities
```

No se aceptarán comandos shell arbitrarios, rutas fuera de los directorios permitidos ni nombres de paquete que contengan traversal. Los adaptadores deben devolver errores tipados como `KVM_UNAVAILABLE`, `RUNTIME_OFFLINE`, `ADB_TIMEOUT`, `APK_REJECTED` o `BACKEND_UNSUPPORTED`.

## Fases de implementación

La primera fase implementará el contrato C++ y un `Translator` que traduzca las capacidades Android a backends Linux disponibles, sin afirmar que ya renderiza Android por sí solo. La segunda fase conectará `RuntimeAdapter` con Cuttlefish y ADB. La tercera fase añadirá gráficos y entrada reales; la cuarta añadirá PipeWire, almacenamiento compartido y red; la quinta incorporará Java/JNI y pruebas de APK.

## Criterios de compatibilidad

La compatibilidad se comprobará por dominios: primero arranque y ADB, después instalación y lanzamiento de una APK de prueba, luego gráficos, entrada, audio, red y archivos. Las aplicaciones que requieran Google Play, DRM, binarios ARM-only o hardware físico quedarán fuera de la compatibilidad garantizada.

## Referencias

[1]: https://www.android-x86.org/ "Android-x86: Run Android on your PC"

[2]: https://source.android.com/docs/core/architecture/hal "AOSP: Hardware abstraction layer overview"

[3]: https://source.android.com/docs/devices/cuttlefish "AOSP: Cuttlefish virtual Android devices"
