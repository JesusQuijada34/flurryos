#include "translator.h"

#include <cassert>
#include <string>

int main() {
  const flurryos::Translator translator;

  const auto graphics = translator.translate("graphics egl");
  assert(graphics.status == flurryos::TranslationStatus::Supported);
  assert(graphics.backend == flurryos::LinuxBackend::WaylandEgl);
  assert(graphics.action == flurryos::NativeAction::CreateSurface);
  assert(graphics.native_call.executable == "flurry-wayland");
  assert(flurryos::backend_name(graphics.backend) == "wayland-egl");
  assert(flurryos::action_name(graphics.action) == "create-surface");

  const auto input = translator.translate("input events");
  assert(input.status == flurryos::TranslationStatus::Supported);
  assert(input.backend == flurryos::LinuxBackend::LibinputEvdev);

  const auto audio = translator.translate("audio output");
  assert(audio.status == flurryos::TranslationStatus::Supported);
  assert(audio.backend == flurryos::LinuxBackend::PipewireAlsa);
  assert(audio.action == flurryos::NativeAction::PlayAudio);
  assert(flurryos::native_call_string(audio.native_call) == "flurry-audio playback");

  const auto runtime = translator.translate("runtime adb");
  assert(runtime.status == flurryos::TranslationStatus::Supported);
  assert(runtime.backend == flurryos::LinuxBackend::CuttlefishAdb);

  const auto unknown = translator.translate("camera preview");
  assert(unknown.status == flurryos::TranslationStatus::Unsupported);
  assert(flurryos::status_name(unknown.status) == "unsupported");

  const auto malformed = translator.translate("graphics egl extra");
  assert(malformed.status == flurryos::TranslationStatus::InvalidRequest);
  const auto unsafe = translator.translate("graphics egl ;rm");
  assert(unsafe.status == flurryos::TranslationStatus::InvalidRequest);
  const auto option = translator.translate("network online iface=android0");
  assert(option.status == flurryos::TranslationStatus::Supported);
  const auto too_long = translator.translate(std::string(257, 'a'));
  assert(too_long.status == flurryos::TranslationStatus::InvalidRequest);

  return 0;
}
