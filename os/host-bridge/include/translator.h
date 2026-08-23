#pragma once

#include <string>
#include <string_view>

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

struct TranslationResult {
  TranslationStatus status;
  LinuxBackend backend;
  std::string domain;
  std::string detail;
};

class Translator final {
 public:
  TranslationResult translate(std::string_view request) const;

 private:
  static TranslationResult invalid(std::string detail);
  static TranslationResult unsupported(std::string domain, std::string detail);
  static TranslationResult supported(std::string domain, LinuxBackend backend, std::string detail);
};

std::string_view backend_name(LinuxBackend backend);
std::string_view status_name(TranslationStatus status);

}  // namespace flurryos
