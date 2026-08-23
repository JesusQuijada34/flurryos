#!/usr/bin/env bash
set -Eeuo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
CONFIG_FILE="${ROOT_DIR}/os/android/aosp/android-version.env"
# shellcheck disable=SC1090
source "${CONFIG_FILE}"

AOSP_DIR="${AOSP_DIR:-${ROOT_DIR}/os/.aosp-android17}"
JOBS="${JOBS:-$(nproc)}"
SETTINGS_SOURCE="${ROOT_DIR}/os/android-settings"
LAUNCHER_SOURCE="${ROOT_DIR}/os/android-launcher"
PRODUCT_MK=""

require_command() {
    command -v "$1" >/dev/null 2>&1 || {
        echo "Falta la herramienta requerida: $1" >&2
        exit 2
    }
}

for command in git repo python3 java; do
    require_command "${command}"
done

if [[ ! -d "${AOSP_DIR}/.repo" ]]; then
    mkdir -p "${AOSP_DIR}"
    repo init --no-repo-verify -u "${AOSP_MANIFEST_URL}" -b "${AOSP_MANIFEST_REF}"
fi

actual_revision="$(git -C "${AOSP_DIR}/.repo/manifests" rev-parse HEAD 2>/dev/null || true)"
if [[ "${actual_revision}" != "${AOSP_MANIFEST_COMMIT}" ]]; then
    echo "Sincronizando el manifiesto fijado ${AOSP_MANIFEST_REF} (${AOSP_MANIFEST_COMMIT})..."
    repo sync -c --force-sync --no-clone-bundle --no-tags -j"${JOBS}"
    actual_revision="$(git -C "${AOSP_DIR}/.repo/manifests" rev-parse HEAD)"
fi
if [[ "${actual_revision}" != "${AOSP_MANIFEST_COMMIT}" ]]; then
    echo "El manifiesto no coincide con el commit fijado: ${actual_revision}" >&2
    exit 3
fi

rm -rf "${AOSP_DIR}/packages/apps/FlurrySettings" "${AOSP_DIR}/packages/apps/FlurryLauncher"
mkdir -p "${AOSP_DIR}/packages/apps/FlurrySettings" "${AOSP_DIR}/packages/apps/FlurryLauncher"
cp -a "${SETTINGS_SOURCE}/Android.bp" "${SETTINGS_SOURCE}/AndroidManifest.xml" "${SETTINGS_SOURCE}/privapp-permissions-com.flurryos.settings.xml" "${AOSP_DIR}/packages/apps/FlurrySettings/"
cp -a "${SETTINGS_SOURCE}/src" "${AOSP_DIR}/packages/apps/FlurrySettings/"
cp -a "${SETTINGS_SOURCE}/res" "${AOSP_DIR}/packages/apps/FlurrySettings/"
cp -a "${LAUNCHER_SOURCE}/Android.bp" "${AOSP_DIR}/packages/apps/FlurryLauncher/"
cp -a "${LAUNCHER_SOURCE}/app/src/main/java" "${AOSP_DIR}/packages/apps/FlurryLauncher/"
cp -a "${LAUNCHER_SOURCE}/app/src/main/res" "${AOSP_DIR}/packages/apps/FlurryLauncher/"
cp -a "${LAUNCHER_SOURCE}/app/src/main/AndroidManifest.xml" "${AOSP_DIR}/packages/apps/FlurryLauncher/"

PRODUCT_MK="$(find "${AOSP_DIR}/device/google/cuttlefish" -type f -name "${AOSP_CUTTLEFISH_PRODUCT}.mk" -print -quit)"
if [[ -z "${PRODUCT_MK}" || ! -f "${PRODUCT_MK}" ]]; then
    echo "No se encontró el producto Cuttlefish: ${AOSP_CUTTLEFISH_PRODUCT}" >&2
    exit 5
fi

if ! grep -qxF 'PRODUCT_PACKAGES += FlurrySettings' "${PRODUCT_MK}"; then
    printf '\n# FlurryOS Android 17 integration\nPRODUCT_PACKAGES += FlurrySettings\n' >> "${PRODUCT_MK}"
fi
if ! grep -qxF 'PRODUCT_PACKAGES += FlurryLauncher' "${PRODUCT_MK}"; then
    printf 'PRODUCT_PACKAGES += FlurryLauncher\n' >> "${PRODUCT_MK}"
fi

pushd "${AOSP_DIR}" >/dev/null
# shellcheck disable=SC1091
source build/envsetup.sh
lunch "${AOSP_CUTTLEFISH_TARGET}"
PRODUCT_OUT="$(get_build_var PRODUCT_OUT)"
m -j"${JOBS}" systemimage vendorimage userdataimage bootimage
popd >/dev/null

if [[ ! -f "${PRODUCT_OUT}/system.img" ]]; then
    echo "No se encontró system.img en ${PRODUCT_OUT}" >&2
    exit 4
fi

mkdir -p "${ROOT_DIR}/os/dist/android17"
cp -f "${PRODUCT_OUT}/system.img" "${ROOT_DIR}/os/dist/android17/"
cp -f "${PRODUCT_OUT}/vendor.img" "${ROOT_DIR}/os/dist/android17/" 2>/dev/null || true
cp -f "${PRODUCT_OUT}/userdata.img" "${ROOT_DIR}/os/dist/android17/" 2>/dev/null || true
cp -f "${PRODUCT_OUT}/boot.img" "${ROOT_DIR}/os/dist/android17/" 2>/dev/null || true
printf '%s\n' "Android ${FLURRYOS_ANDROID_VERSION} listo en ${ROOT_DIR}/os/dist/android17"
