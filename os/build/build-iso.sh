#!/usr/bin/env bash
set -Eeuo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
WORK_DIR="${WORK_DIR:-${ROOT_DIR}/.live-build}"
OUTPUT_DIR="${OUTPUT_DIR:-${ROOT_DIR}/dist}"
SUITE="${SUITE:-noble}"
ARCH="amd64"

if [[ "$(id -u)" -ne 0 ]]; then
  echo "Este script necesita ejecutarse como root para construir la imagen chroot." >&2
  echo "Usa: sudo $0" >&2
  exit 1
fi

for command in lb debootstrap mksquashfs xorriso; do
  if ! command -v "${command}" >/dev/null 2>&1; then
    echo "Falta '${command}'. Instala las herramientas de construcción Ubuntu antes de continuar:" >&2
    echo "sudo apt-get update && sudo apt-get install -y live-build debootstrap squashfs-tools xorriso" >&2
    exit 2
  fi
done

mkdir -p "${WORK_DIR}" "${OUTPUT_DIR}"
cd "${WORK_DIR}"

# Use the versioned compatibility patch for this Ubuntu/live-build toolchain.
export LIVE_BUILD="${ROOT_DIR}/build/live-build"

# A failed ISO assembly can leave a partial binary stage. Keep bootstrap and
# chroot caches, but ask live-build to invalidate all binary-stage markers.
rm -rf config
mkdir -p config/package-lists config/includes.chroot config/hooks/normal
cp "${ROOT_DIR}/config/package-list.txt" config/package-lists/flurryos.list.chroot
cp -a "${ROOT_DIR}/config/hooks/normal/." config/hooks/normal/
cp -a "${ROOT_DIR}/overlay/." config/includes.chroot/
chmod +x config/hooks/normal/*.chroot config/includes.chroot/usr/local/bin/*

lb config \
  --mode ubuntu \
  --distribution "${SUITE}" \
  --architectures "${ARCH}" \
  --mirror-bootstrap "http://archive.ubuntu.com/ubuntu/" \
  --mirror-chroot "http://archive.ubuntu.com/ubuntu/" \
  --mirror-binary "http://archive.ubuntu.com/ubuntu/" \
  --mirror-chroot-security "http://security.ubuntu.com/ubuntu/" \
  --mirror-binary-security "http://security.ubuntu.com/ubuntu/" \
  --archive-areas "main restricted universe multiverse" \
  --binary-images iso \
  --compression xz \
  --bootloader grub2 \
  --bootappend-live "boot=live components quiet splash" \
  --debian-installer false \
  --memtest none \
  --apt-recommends true \
  --iso-application "FlurryOS OS" \
  --iso-publisher "FlurryOS Project" \
  --iso-volume "FLURRYOS_OS"

if [[ ! -x chroot/usr/bin/env ]]; then
  lb clean --chroot --binary
  rm -rf chroot
else
  lb clean --binary
fi
rm -rf chroot/binary binary binary.*
lb build

ISO="$(find . -maxdepth 1 -type f -name '*.iso' -print -quit)"
if [[ -z "${ISO}" ]]; then
  echo "La construcción terminó sin producir una ISO." >&2
  exit 3
fi

TARGET="${OUTPUT_DIR}/flurryos-os-${SUITE}-${ARCH}.iso"
cp -f "${ISO}" "${TARGET}"
sha256sum "${TARGET}" > "${TARGET}.sha256"
printf 'ISO creada: %s\nSHA-256: %s\n' "${TARGET}" "${TARGET}.sha256"
