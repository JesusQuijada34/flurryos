#include "json_protocol.h"

#include <cctype>
#include <iomanip>
#include <sstream>
#include <stdexcept>
#include <utility>

namespace flurryos {
namespace {

class Parser final {
 public:
  explicit Parser(std::string_view input) : input_(input) {}

  JsonRequest parse() {
    skip_ws();
    expect('{');
    JsonRequest request;
    bool has_version = false;
    bool has_id = false;
    bool has_method = false;
    skip_ws();
    if (peek('}')) {
      throw std::runtime_error("objeto vacío");
    }
    while (true) {
      const std::string key = string();
      skip_ws();
      expect(':');
      skip_ws();
      if (key == "version") {
        request.version = integer();
        has_version = true;
      } else if (key == "id") {
        request.id = string();
        has_id = true;
      } else if (key == "method") {
        request.method = string();
        has_method = true;
      } else if (key == "args") {
        request.args = object();
      } else {
        throw std::runtime_error("campo desconocido: " + key);
      }
      skip_ws();
      if (peek('}')) {
        consume();
        break;
      }
      expect(',');
      skip_ws();
    }
    skip_ws();
    if (!at_end()) {
      throw std::runtime_error("datos después del objeto");
    }
    if (!has_version || !has_id || !has_method) {
      throw std::runtime_error("faltan version, id o method");
    }
    if (request.version != 1 || request.id.empty() || request.id.size() > 64U || request.method.empty()) {
      throw std::runtime_error("versión, ID o método inválido");
    }
    return request;
  }

 private:
  bool at_end() const { return position_ == input_.size(); }
  char current() const { return at_end() ? '\0' : input_[position_]; }
  bool peek(const char value) const { return current() == value; }
  char consume() {
    if (at_end()) {
      throw std::runtime_error("fin inesperado");
    }
    return input_[position_++];
  }
  void skip_ws() {
    while (!at_end() && std::isspace(static_cast<unsigned char>(current())) != 0) {
      ++position_;
    }
  }
  void expect(const char value) {
    if (consume() != value) {
      throw std::runtime_error("token inesperado");
    }
  }
  std::string string() {
    expect('"');
    std::string result;
    while (!at_end()) {
      const char value = consume();
      if (value == '"') {
        return result;
      }
      if (value == '\\') {
        const char escaped = consume();
        switch (escaped) {
          case '"': result.push_back('"'); break;
          case '\\': result.push_back('\\'); break;
          case '/': result.push_back('/'); break;
          case 'b': result.push_back('\b'); break;
          case 'f': result.push_back('\f'); break;
          case 'n': result.push_back('\n'); break;
          case 'r': result.push_back('\r'); break;
          case 't': result.push_back('\t'); break;
          default: throw std::runtime_error("escape JSON no soportado");
        }
      } else {
        if (static_cast<unsigned char>(value) < 0x20U) {
          throw std::runtime_error("control inválido en string");
        }
        result.push_back(value);
      }
    }
    throw std::runtime_error("string sin cerrar");
  }
  int integer() {
    const std::size_t start = position_;
    if (peek('-')) consume();
    if (at_end() || std::isdigit(static_cast<unsigned char>(current())) == 0) {
      throw std::runtime_error("entero esperado");
    }
    while (!at_end() && std::isdigit(static_cast<unsigned char>(current())) != 0) consume();
    try {
      return std::stoi(std::string(input_.substr(start, position_ - start)));
    } catch (const std::exception&) {
      throw std::runtime_error("entero inválido");
    }
  }
  std::map<std::string, std::string> object() {
    std::map<std::string, std::string> values;
    expect('{');
    skip_ws();
    if (peek('}')) { consume(); return values; }
    while (true) {
      const std::string key = string();
      skip_ws();
      expect(':');
      skip_ws();
      values.emplace(key, string());
      skip_ws();
      if (peek('}')) { consume(); return values; }
      expect(',');
      skip_ws();
    }
  }
  std::string_view input_;
  std::size_t position_ = 0U;
};

}  // namespace

bool JsonProtocol::parse_request(std::string_view line, JsonRequest& request, std::string& error) {
  try {
    request = Parser(line).parse();
    return true;
  } catch (const std::exception& exception) {
    error = exception.what();
    return false;
  }
}

std::string JsonProtocol::escape(const std::string_view value) {
  std::ostringstream output;
  output << std::quoted(std::string(value));
  return output.str();
}

std::string JsonProtocol::serialize_response(const JsonResponse& response) {
  std::ostringstream output;
  output << "{\"version\":1,\"id\":" << escape(response.id) << ",\"ok\":"
         << (response.ok ? "true" : "false");
  if (response.ok) {
    output << ",\"result\":{";
    bool first = true;
    for (const auto& [key, value] : response.result) {
      if (!first) output << ',';
      first = false;
      output << escape(key) << ':' << escape(value);
    }
    output << '}';
  } else {
    output << ",\"error\":{\"code\":" << escape(response.error_code)
           << ",\"message\":" << escape(response.message) << '}';
  }
  output << '}';
  return output.str();
}

}  // namespace flurryos
