from pathlib import Path
import re
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parents[3]

for manifest in [
    root / "os/android-settings/AndroidManifest.xml",
    root / "os/android-settings/privapp-permissions-com.flurryos.settings.xml",
    root / "os/android-launcher/app/src/main/AndroidManifest.xml",
]:
    ET.parse(manifest)

for source in [
    root / "os/android-settings/src/com/flurryos/settings/SettingsActivity.java",
    root / "os/android-launcher/app/src/main/java/com/flurryos/launcher/MainActivity.java",
    root / "os/android-launcher/app/src/main/java/com/flurryos/launcher/BridgeService.java",
    root / "os/android-launcher/app/src/main/java/com/flurryos/launcher/BridgeSocketServer.java",
]:
    text = source.read_text(encoding="utf-8")
    if text.count("{") != text.count("}"):
        raise SystemExit(f"llaves desbalanceadas: {source}")

config = (root / "os/android/aosp/android-version.env").read_text(encoding="utf-8")
required = [
    "FLURRYOS_ANDROID_VERSION=17",
    "AOSP_MANIFEST_REF=android-17.0.0_r1",
    "AOSP_CUTTLEFISH_TARGET=aosp_cf_x86_64_only_phone-userdebug",
]
for item in required:
    if item not in config:
        raise SystemExit(f"falta configuración: {item}")

settings_bp = (root / "os/android-settings/Android.bp").read_text(encoding="utf-8")
for item in ["name: \"FlurrySettings\"", "certificate: \"platform\"", "privileged: true", "required: [\"flurrysettings-privapp-permissions\"]"]:
    if item not in settings_bp:
        raise SystemExit(f"falta integración AOSP: {item}")

print("ANDROID 17 STATIC INTEGRATION CHECK PASSED")
