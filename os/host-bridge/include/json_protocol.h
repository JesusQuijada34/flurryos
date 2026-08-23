#pragma once

#include <map>
#include <string>
#include <string_view>

namespace flurryos {

struct JsonRequest {
  int version = 0;
  std::string id;
  std::string method;
  std::map<std::string, std::string> args;
};

struct JsonResponse {
  bool ok = false;
  std::string id;
  std::string error_code;
  std::string message;
  std::map<std::string, std::string> result;
};

class JsonProtocol final {
 public:
  static bool parse_request(std::string_view line, JsonRequest& request, std::string& error);
  static std::string serialize_response(const JsonResponse& response);

 private:
  static std::string escape(std::string_view value);
};

}  // namespace flurryos
