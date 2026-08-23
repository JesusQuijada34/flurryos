# Requisitos del host para compilar AOSP Android 17

Fuente oficial consultada el 23 de agosto de 2026: https://source.android.com/docs/setup/start/requirements

La guía de AOSP indica que el host debe ser Linux x86_64 y recomienda al menos **400 GB libres**: aproximadamente 250 GB para el checkout y 150 GB para la compilación. También indica un mínimo de **64 GB de RAM** para el entorno de compilación de referencia.

El sandbox actual tiene 6 CPUs, aproximadamente 3.8 GiB de RAM y 26 GiB libres en disco. Por tanto, se pueden instalar y verificar herramientas pequeñas, pero no es técnicamente viable sincronizar ni compilar aquí el árbol completo AOSP Android 17. La compilación debe ejecutarse en una máquina persistente x86_64 con almacenamiento y memoria suficientes.

La misma guía recomienda para Ubuntu 18.04 o posterior los paquetes `git-core`, `gnupg`, `flex`, `bison`, `build-essential`, `zip`, `curl`, `zlib1g-dev`, `libc6-dev-i386`, `x11proto-core-dev`, `libx11-dev`, `lib32z1-dev`, `libgl1-mesa-dev`, `libxml2-utils`, `xsltproc`, `unzip` y `fontconfig`, además de Repo 2.4 o superior. Cuttlefish requiere además virtualización KVM en el host.
