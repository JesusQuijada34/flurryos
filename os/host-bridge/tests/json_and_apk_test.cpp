#include "apk_validator.h"
#include "json_protocol.h"

#include <cassert>
#include <filesystem>
#include <fstream>
#include <string>

int main() {
  flurryos::JsonRequest request;
  std::string error;
  const bool parsed = flurryos::JsonProtocol::parse_request(
      R"({"version":1,"id":"42","method":"apk.install","args":{"file_id":"notes.apk"}})", request, error);
  assert(parsed);
  assert(request.version == 1);
  assert(request.id == "42");
  assert(request.method == "apk.install");
  assert(request.args.at("file_id") == "notes.apk");

  assert(!flurryos::JsonProtocol::parse_request(
      R"({"version":2,"id":"42","method":"status"})", request, error));
  assert(!flurryos::JsonProtocol::parse_request(
      R"({"version":1,"id":"42","method":"status","extra":true})", request, error));

  flurryos::JsonResponse response;
  response.ok = true;
  response.id = "42";
  response.result = {{"state", "online"}};
  const std::string encoded = flurryos::JsonProtocol::serialize_response(response);
  assert(encoded.find("\"ok\":true") != std::string::npos);
  assert(encoded.find("\"state\":\"online\"") != std::string::npos);

  const auto inbox = std::filesystem::temp_directory_path() / "flurryos-apk-test";
  std::error_code cleanup_error;
  std::filesystem::remove_all(inbox, cleanup_error);
  std::filesystem::create_directories(inbox);
  {
    std::ofstream apk(inbox / "notes.apk", std::ios::binary);
    apk << 'x';
  }
  const flurryos::ApkValidator validator(inbox, 1024U);
  assert(validator.validate_file_id("notes.apk").ok);
  assert(!validator.validate_file_id("../secret.apk").ok);
  assert(!validator.validate_file_id("notes.zip").ok);
  assert(!validator.validate_file_id("missing.apk").ok);
  std::filesystem::remove_all(inbox, cleanup_error);
  return 0;
}
