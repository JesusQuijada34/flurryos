#pragma once

#include <string>
#include <string_view>
#include <vector>

namespace flurryos {

enum class TranslationStatus {
  Supported,
  Unsupported,
  InvalidRequest,
};

enum class LinuxBackend {
  WaylandEgl,
  LibinputEvdev,
  PipewireAlsa,
  FlurryStore,
  NetworkManager,
  CuttlefishAdb,
};

enum class NativeAction {
  CreateSurface,
  ReadInputEvents,
  PlayAudio,
  CaptureAudio,
  OpenStore,
  OpenPrivateStore,
  ConfigureNetwork,
  StartRuntime,
  RuntimeLifecycle,
};

struct NativeCall {
  std::string executable;
  std::vector<std::string> arguments;
};

struct TranslationResult {
  TranslationStatus status;
  LinuxBackend backend;
  NativeAction action;
  std::string domain;
  std::string detail;
  NativeCall native_call;
};

class Translator final {
 public:
  // Request format: <android-domain> <operation> [key=value ...].
  // The translator returns a data-only native plan and never executes a process.
  TranslationResult translate(std::string_view request) const;

 private:
  static TranslationResult invalid(std::string detail);
  static TranslationResult unsupported(std::string domain, std::string detail);
  static TranslationResult supported(std::string domain, LinuxBackend backend,
                                     NativeAction action, std::string detail,
                                     NativeCall native_call);
};

std::string_view backend_name(LinuxBackend backend);
std::string_view status_name(TranslationStatus status);
std::string_view action_name(NativeAction action);
std::string native_call_string(const NativeCall& call);

}  // namespace flurryos
