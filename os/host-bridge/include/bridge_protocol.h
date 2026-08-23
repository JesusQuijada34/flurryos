#pragma once

#include <string>
#include <string_view>

namespace flurryos {

struct CommandResult {
  bool ok;
  std::string payload;
};

class BridgeProtocol final {
 public:
  static CommandResult handle(std::string_view line);

 private:
  static bool valid_package_name(std::string_view package_name);
  static bool valid_apk_path(std::string_view path);
};

}  // namespace flurryos
