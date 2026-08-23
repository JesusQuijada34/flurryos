#pragma once

#include <filesystem>
#include <string>
#include <string_view>

namespace flurryos {

struct ApkValidation {
  bool ok = false;
  std::filesystem::path path;
  std::string error_code;
  std::string message;
};

class ApkValidator final {
 public:
  explicit ApkValidator(std::filesystem::path inbox, std::uintmax_t max_bytes = 512U * 1024U * 1024U);
  ApkValidation validate_file_id(std::string_view file_id) const;

 private:
  std::filesystem::path inbox_;
  std::uintmax_t max_bytes_;
};

}  // namespace flurryos
