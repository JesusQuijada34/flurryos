# Traductor de API Android a backends nativos Linux

El módulo `Translator` de FlurryOS convierte una llamada Android normalizada en un **plan de ejecución de datos**, no en un comando shell. Esto permite que el daemon C++ seleccione el backend Linux apropiado sin ejecutar texto recibido desde una aplicación.

## Formato

La forma legacy y de diagnóstico es:

```text
<dominio> <operación> [clave=valor ...]
```

Ejemplos:

```text
graphics egl
input events
audio output
storage private
network online iface=android0
runtime lifecycle
```

Los tokens tienen una longitud máxima y solo admiten caracteres alfanuméricos, guion bajo, guion, punto y `=`. La solicitud completa está limitada a 256 bytes y a cuatro opciones. Las opciones se validan, pero el traductor no las incorpora a una orden arbitraria: cada backend decide qué valores admitirá en una implementación posterior.

## Tabla de traducción

| API Android normalizada | Backend Linux | Acción | Ejecutable lógico | Argumentos |
|---|---|---|---|---|
| `graphics egl` / `graphics surface` | `wayland-egl` | `create-surface` | `flurry-wayland` | `surface create` |
| `input events` / `input pointer` | `libinput-evdev` | `read-input-events` | `flurry-input` | `events` |
| `audio output` | `pipewire-alsa` | `play-audio` | `flurry-audio` | `playback` |
| `audio input` | `pipewire-alsa` | `capture-audio` | `flurry-audio` | `capture` |
| `storage shared` | `flurry-store` | `open-store` | `flurry-store` | `open shared` |
| `storage private` | `flurry-store` | `open-private-store` | `flurry-store` | `open private` |
| `network virtual` / `network online` | `networkmanager` | `configure-network` | `flurry-network` | `configure` |
| `runtime adb` | `cuttlefish-adb` | `start-runtime` | `flurry-runtime` | `adb` |
| `runtime lifecycle` | `cuttlefish-adb` | `runtime-lifecycle` | `flurry-runtime` | `lifecycle` |

Los nombres `flurry-wayland`, `flurry-input`, `flurry-audio`, `flurry-store`, `flurry-network` y `flurry-runtime` representan adaptadores nativos previstos. El traductor **no los ejecuta**; devuelve sus nombres y argumentos separados para que un supervisor con políticas pueda resolverlos mediante `execve`, listas permitidas o servicios systemd.

## Respuesta JSON del daemon

Una solicitud:

```json
{"version":1,"id":"translate-1","method":"translate","args":{"domain":"audio","operation":"output"}}
```

devuelve, cuando está soportada:

```json
{"version":1,"id":"translate-1","ok":true,"result":{"domain":"audio","backend":"pipewire-alsa","action":"play-audio","native_executable":"flurry-audio","native_call":"flurry-audio playback","detail":"Android AudioTrack -> PipeWire playback"}}
```

Las operaciones desconocidas producen `BACKEND_UNSUPPORTED`; una sintaxis inválida produce `INVALID_JSON` a nivel de protocolo o `invalid-request` en el traductor. La compatibilidad no implica que todos los binarios nativos estén incluidos todavía: el proyecto separa el plan de traducción de la futura implementación de cada backend.

## Alcance técnico

Este componente es una capa de adaptación experimental para la arquitectura Ubuntu/GNOME/Wayland + AOSP/Cuttlefish de FlurryOS. No es una implementación universal del Android Framework ni una traducción completa de la API NDK/SDK. Las aplicaciones Android se ejecutan dentro del runtime Android aislado; el traductor describe cómo las capacidades de gráficos, entrada, audio, almacenamiento, red y ciclo de vida se conectarán con servicios Linux controlados.
