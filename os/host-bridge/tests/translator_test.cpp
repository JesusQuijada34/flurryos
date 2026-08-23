#include "translator.h"

#include <cassert>
#include <string>

int main() {
  const flurryos::Translator translator;

  const auto graphics = translator.translate("graphics egl");
  assert(graphics.status == flurryos::TranslationStatus::Supported);
  assert(graphics.backend == flurryos::LinuxBackend::WaylandEgl);
  assert(flurryos::backend_name(graphics.backend) == "wayland-egl");

  const auto input = translator.translate("input events");
  assert(input.status == flurryos::TranslationStatus::Supported);
  assert(input.backend == flurryos::LinuxBackend::LibinputEvdev);

  const auto audio = translator.translate("audio output");
  assert(audio.status == flurryos::TranslationStatus::Supported);
  assert(audio.backend == flurryos::LinuxBackend::PipewireAlsa);

  const auto runtime = translator.translate("runtime adb");
  assert(runtime.status == flurryos::TranslationStatus::Supported);
  assert(runtime.backend == flurryos::LinuxBackend::CuttlefishAdb);

  const auto unknown = translator.translate("camera preview");
  assert(unknown.status == flurryos::TranslationStatus::Unsupported);
  assert(flurryos::status_name(unknown.status) == "unsupported");

  const auto malformed = translator.translate("graphics egl extra");
  assert(malformed.status == flurryos::TranslationStatus::InvalidRequest);

  return 0;
}
