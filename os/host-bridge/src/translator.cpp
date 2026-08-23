#include "translator.h"

#include <algorithm>
#include <cctype>
#include <initializer_list>
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

bool valid_token(std::string_view value) {
  if (value.empty() || value.size() > 64U) return false;
  return std::all_of(value.begin(), value.end(), [](unsigned char c) {
    return std::isalnum(c) != 0 || c == '_' || c == '-' || c == '.' || c == '=';
  });
}

NativeCall call(std::string executable, std::initializer_list<const char*> arguments) {
  NativeCall result;
  result.executable = std::move(executable);
  for (const char* argument : arguments) result.arguments.emplace_back(argument);
  return result;
}

}  // namespace

TranslationResult Translator::invalid(std::string detail) {
  return {TranslationStatus::InvalidRequest, LinuxBackend::CuttlefishAdb, NativeAction::RuntimeLifecycle,
          "", std::move(detail), {}};
}

TranslationResult Translator::unsupported(std::string domain, std::string detail) {
  return {TranslationStatus::Unsupported, LinuxBackend::CuttlefishAdb, NativeAction::RuntimeLifecycle,
          std::move(domain), std::move(detail), {}};
}

TranslationResult Translator::supported(std::string domain, LinuxBackend backend, NativeAction action,
                                        std::string detail, NativeCall native_call) {
  return {TranslationStatus::Supported, backend, action, std::move(domain), std::move(detail), std::move(native_call)};
}

TranslationResult Translator::translate(std::string_view request) const {
  if (request.size() > 256U) return invalid("solicitud demasiado larga");
  std::istringstream input{std::string(request)};
  std::string domain;
  std::string operation;
  if (!(input >> domain >> operation) || !valid_token(domain) || !valid_token(operation)) {
    return invalid("formato esperado: <dominio> <operacion> [clave=valor]");
  }
  std::string option;
  std::size_t option_count = 0U;
  while (input >> option) {
    if (!valid_token(option) || option.find('=') == std::string::npos) {
      return invalid("las opciones deben usar clave=valor con caracteres seguros");
    }
    if (++option_count > 4U) return invalid("demasiadas opciones");
  }
  domain = normalize(domain);
  operation = normalize(operation);

  if (domain == "graphics" && (operation == "egl" || operation == "surface")) {
    return supported(domain, LinuxBackend::WaylandEgl, NativeAction::CreateSurface,
                     "Android graphics -> Wayland/EGL/Mesa", call("flurry-wayland", {"surface", "create"}));
  }
  if (domain == "input" && (operation == "events" || operation == "pointer")) {
    return supported(domain, LinuxBackend::LibinputEvdev, NativeAction::ReadInputEvents,
                     "Android input -> libinput/evdev", call("flurry-input", {"events"}));
  }
  if (domain == "audio" && operation == "output") {
    return supported(domain, LinuxBackend::PipewireAlsa, NativeAction::PlayAudio,
                     "Android AudioTrack -> PipeWire playback", call("flurry-audio", {"playback"}));
  }
  if (domain == "audio" && operation == "input") {
    return supported(domain, LinuxBackend::PipewireAlsa, NativeAction::CaptureAudio,
                     "Android AudioRecord -> PipeWire capture", call("flurry-audio", {"capture"}));
  }
  if (domain == "storage" && operation == "shared") {
    return supported(domain, LinuxBackend::FlurryStore, NativeAction::OpenStore,
                     "Android shared storage -> FlurryStore", call("flurry-store", {"open", "shared"}));
  }
  if (domain == "storage" && operation == "private") {
    return supported(domain, LinuxBackend::FlurryStore, NativeAction::OpenPrivateStore,
                     "Android private storage -> sandboxed FlurryStore", call("flurry-store", {"open", "private"}));
  }
  if (domain == "network" && (operation == "virtual" || operation == "online")) {
    return supported(domain, LinuxBackend::NetworkManager, NativeAction::ConfigureNetwork,
                     "Android network -> NetworkManager virtual link", call("flurry-network", {"configure"}));
  }
  if (domain == "runtime" && operation == "adb") {
    return supported(domain, LinuxBackend::CuttlefishAdb, NativeAction::StartRuntime,
                     "Android runtime -> Cuttlefish/ADB", call("flurry-runtime", {"adb"}));
  }
  if (domain == "runtime" && operation == "lifecycle") {
    return supported(domain, LinuxBackend::CuttlefishAdb, NativeAction::RuntimeLifecycle,
                     "Android lifecycle -> Cuttlefish supervisor", call("flurry-runtime", {"lifecycle"}));
  }
  return unsupported(domain, "backend no implementado para la operación solicitada");
}

std::string_view backend_name(LinuxBackend backend) {
  switch (backend) {
    case LinuxBackend::WaylandEgl: return "wayland-egl";
    case LinuxBackend::LibinputEvdev: return "libinput-evdev";
    case LinuxBackend::PipewireAlsa: return "pipewire-alsa";
    case LinuxBackend::FlurryStore: return "flurry-store";
    case LinuxBackend::NetworkManager: return "networkmanager";
    case LinuxBackend::CuttlefishAdb: return "cuttlefish-adb";
  }
  return "unknown";
}

std::string_view status_name(TranslationStatus status) {
  switch (status) {
    case TranslationStatus::Supported: return "supported";
    case TranslationStatus::Unsupported: return "unsupported";
    case TranslationStatus::InvalidRequest: return "invalid-request";
  }
  return "unknown";
}

std::string_view action_name(NativeAction action) {
  switch (action) {
    case NativeAction::CreateSurface: return "create-surface";
    case NativeAction::ReadInputEvents: return "read-input-events";
    case NativeAction::PlayAudio: return "play-audio";
    case NativeAction::CaptureAudio: return "capture-audio";
    case NativeAction::OpenStore: return "open-store";
    case NativeAction::OpenPrivateStore: return "open-private-store";
    case NativeAction::ConfigureNetwork: return "configure-network";
    case NativeAction::StartRuntime: return "start-runtime";
    case NativeAction::RuntimeLifecycle: return "runtime-lifecycle";
  }
  return "unknown";
}

std::string native_call_string(const NativeCall& native_call) {
  std::ostringstream output;
  output << native_call.executable;
  for (const auto& argument : native_call.arguments) output << ' ' << argument;
  return output.str();
}

}  // namespace flurryos
