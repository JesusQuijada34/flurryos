#include "apk_validator.h"

#include <algorithm>
#include <cctype>
#include <system_error>

namespace flurryos {

ApkValidator::ApkValidator(std::filesystem::path inbox, const std::uintmax_t max_bytes)
    : inbox_(std::move(inbox)), max_bytes_(max_bytes) {}

ApkValidation ApkValidator::validate_file_id(const std::string_view file_id) const {
  if (file_id.empty() || file_id.size() > 96U) {
    return {false, {}, "APK_ID_INVALID", "identificador de APK vacío o demasiado largo"};
  }
  const bool valid_name = std::all_of(file_id.begin(), file_id.end(), [](const char value) {
    const auto character = static_cast<unsigned char>(value);
    return std::isalnum(character) != 0 || value == '.' || value == '_' || value == '-';
  });
  if (!valid_name || file_id.find("..") != std::string_view::npos || file_id.front() == '.') {
    return {false, {}, "APK_ID_INVALID", "identificador de APK no permitido"};
  }
  if (file_id.size() < 4U || file_id.substr(file_id.size() - 4U) != ".apk") {
    return {false, {}, "APK_EXTENSION_INVALID", "el archivo debe terminar en .apk"};
  }

  std::error_code error;
  const auto inbox = std::filesystem::canonical(inbox_, error);
  if (error) {
    return {false, {}, "INBOX_UNAVAILABLE", "el directorio inbox no está disponible"};
  }
  const auto candidate = std::filesystem::canonical(inbox_ / std::string(file_id), error);
  if (error || candidate.parent_path() != inbox || candidate.filename() != file_id) {
    return {false, {}, "APK_NOT_FOUND", "el APK no existe dentro del inbox"};
  }
  if (!std::filesystem::is_regular_file(candidate, error) || error) {
    return {false, {}, "APK_NOT_REGULAR", "el APK no es un archivo regular"};
  }
  const auto size = std::filesystem::file_size(candidate, error);
  if (error || size == 0U || size > max_bytes_) {
    return {false, {}, "APK_SIZE_INVALID", "el tamaño del APK no está permitido"};
  }
  return {true, candidate, "", "APK validado"};
}

}  // namespace flurryos
