#include "translator.h"

#include <algorithm>
#include <cctype>
#include <sstream>
#include <utility>

namespace flurryos {
namespace {

std::string normalize(std::string_view value) {
  std::string result(value);
  std::transform(result.begin(), result.end(), result.begin(), [](unsigned char c) {
    return static_cast<char>(std::tolower(c));
  });
  return result;
}

}  // namespace

TranslationResult Translator::invalid(std::string detail) {
  return {TranslationStatus::InvalidRequest, LinuxBackend::CuttlefishAdb, "", std::move(detail)};
}

TranslationResult Translator::unsupported(std::string domain, std::string detail) {
  return {TranslationStatus::Unsupported, LinuxBackend::CuttlefishAdb, std::move(domain), std::move(detail)};
}

TranslationResult Translator::supported(std::string domain, LinuxBackend backend, std::string detail) {
  return {TranslationStatus::Supported, backend, std::move(domain), std::move(detail)};
}

TranslationResult Translator::translate(std::string_view request) const {
  std::istringstream input{std::string(request)};
  std::string domain;
  std::string operation;
  std::string extra;
  input >> domain >> operation >> extra;
  domain = normalize(domain);
  operation = normalize(operation);
  extra = normalize(extra);

  if (domain.empty() || operation.empty() || !extra.empty()) {
    return invalid("formato esperado: <dominio> <operacion>");
  }

  if (domain == "graphics" && (operation == "egl" || operation == "surface")) {
    return supported(domain, LinuxBackend::WaylandEgl, "Android graphics -> Wayland/EGL/Mesa");
  }
  if (domain == "input" && (operation == "events" || operation == "pointer")) {
    return supported(domain, LinuxBackend::LibinputEvdev, "Android input -> libinput/evdev");
  }
  if (domain == "audio" && (operation == "output" || operation == "input")) {
    return supported(domain, LinuxBackend::PipewireAlsa, "Android audio -> PipeWire/ALSA");
  }
  if (domain == "storage" && (operation == "shared" || operation == "private")) {
    return supported(domain, LinuxBackend::FlurryStore, "Android storage -> FlurryOS controlled store");
  }
  if (domain == "network" && (operation == "virtual" || operation == "online")) {
    return supported(domain, LinuxBackend::NetworkManager, "Android network -> NetworkManager/virtual link");
  }
  if (domain == "runtime" && (operation == "adb" || operation == "lifecycle")) {
    return supported(domain, LinuxBackend::CuttlefishAdb, "Android runtime -> Cuttlefish/ADB");
  }

  return unsupported(domain, "backend no implementado para la operación solicitada");
}

std::string_view backend_name(LinuxBackend backend) {
  switch (backend) {
    case LinuxBackend::WaylandEgl:
      return "wayland-egl";
    case LinuxBackend::LibinputEvdev:
      return "libinput-evdev";
    case LinuxBackend::PipewireAlsa:
      return "pipewire-alsa";
    case LinuxBackend::FlurryStore:
      return "flurry-store";
    case LinuxBackend::NetworkManager:
      return "networkmanager";
    case LinuxBackend::CuttlefishAdb:
      return "cuttlefish-adb";
  }
  return "unknown";
}

std::string_view status_name(TranslationStatus status) {
  switch (status) {
    case TranslationStatus::Supported:
      return "supported";
    case TranslationStatus::Unsupported:
      return "unsupported";
    case TranslationStatus::InvalidRequest:
      return "invalid-request";
  }
  return "unknown";
}

}  // namespace flurryos
