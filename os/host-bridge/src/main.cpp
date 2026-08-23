#include "apk_validator.h"
#include "bridge_protocol.h"
#include "json_protocol.h"
#include "runtime_controller.h"
#include "translator.h"

#include <cerrno>
#include <csignal>
#include <cstdlib>
#include <cstring>
#include <filesystem>
#include <iostream>
#include <sstream>
#include <string>

#include <sys/socket.h>
#include <sys/stat.h>
#include <sys/un.h>
#include <unistd.h>

namespace {

volatile std::sig_atomic_t running = 1;
int server_fd = -1;
std::string socket_path;

void stop_server(int) {
  running = 0;
  if (server_fd >= 0) {
    shutdown(server_fd, SHUT_RDWR);
  }
}

bool write_all(const int fd, const std::string& message) {
  std::size_t written = 0U;
  while (written < message.size()) {
    const ssize_t count = write(fd, message.data() + written, message.size() - written);
    if (count < 0) {
      if (errno == EINTR) {
        continue;
      }
      return false;
    }
    written += static_cast<std::size_t>(count);
  }
  return true;
}

std::string env_or(const char* name, const char* fallback) {
  const char* value = std::getenv(name);
  return value == nullptr ? std::string(fallback) : std::string(value);
}

std::string parse_socket_path(int argc, char** argv) {
  for (int index = 1; index + 1 < argc; ++index) {
    if (std::string(argv[index]) == "--socket") {
      return argv[index + 1];
    }
  }
  return "/run/flurryos/bridge.sock";
}

flurryos::CommandResult handle_command(flurryos::RuntimeController& runtime, const flurryos::Translator& translator, const std::string& line) {
  std::istringstream input(line);
  std::string command;
  input >> command;

  if (command == "PING") {
    return {true, "PONG"};
  }
  if (command == "STATUS") {
    return runtime.status();
  }
  if (command == "ANDROID_START") {
    return runtime.start();
  }
  if (command == "ANDROID_STOP") {
    return runtime.stop();
  }
  if (command == "LAUNCH") {
    std::string package_name;
    input >> package_name;
    return runtime.launch(package_name);
  }
  if (command == "INSTALL") {
    std::string apk_path;
    input >> apk_path;
    return runtime.install(apk_path);
  }
  if (command == "TRANSLATE") {
    std::string domain;
    std::string operation;
    input >> domain >> operation;
    const auto result = translator.translate(domain + " " + operation);
    std::ostringstream response;
    response << flurryos::status_name(result.status) << " domain=" << result.domain
             << " backend=" << flurryos::backend_name(result.backend)
             << " action=" << flurryos::action_name(result.action)
             << " native=" << flurryos::native_call_string(result.native_call)
             << " detail=" << result.detail;
    return {result.status == flurryos::TranslationStatus::Supported, response.str()};
  }
  if (command == "CAPABILITIES") {
    return {true, "graphics=wayland-egl input=libinput-evdev audio=pipewire-alsa storage=flurry-store network=networkmanager runtime=cuttlefish-adb"};
  }
  return flurryos::BridgeProtocol::handle(line);
}

flurryos::JsonResponse handle_json_request(flurryos::RuntimeController& runtime, const flurryos::Translator& translator,
                                           const flurryos::ApkValidator& apk_validator,
                                           const flurryos::JsonRequest& request) {
  flurryos::JsonResponse response;
  response.id = request.id;
  const auto fail = [&response](std::string code, std::string message) {
    response.ok = false;
    response.error_code = std::move(code);
    response.message = std::move(message);
  };
  const auto command = [&response](const flurryos::CommandResult& result) {
    response.ok = result.ok;
    if (result.ok) {
      response.result.emplace("output", result.payload);
    } else {
      response.error_code = "RUNTIME_ERROR";
      response.message = result.payload;
    }
  };

  if (request.method == "status") {
    command(runtime.status());
  } else if (request.method == "android.start") {
    command(runtime.start());
  } else if (request.method == "android.stop") {
    command(runtime.stop());
  } else if (request.method == "apps.list") {
    command(runtime.bridge_request("{\"version\":1,\"id\":\"" + request.id + "\",\"method\":\"apps.list\",\"args\":{}}"));
  } else if (request.method == "app.launch") {
    const auto package = request.args.find("package");
    if (package == request.args.end()) {
      fail("PACKAGE_REQUIRED", "falta args.package");
    } else {
      const auto validation = flurryos::BridgeProtocol::handle("LAUNCH " + package->second);
      if (!validation.ok) {
        fail("PACKAGE_INVALID", validation.payload);
      } else {
        const std::string bridge_json = "{\"version\":1,\"id\":\"" + request.id + "\",\"method\":\"app.launch\",\"args\":{\"package\":\"" + package->second + "\"}}";
        command(runtime.bridge_request(bridge_json));
      }
    }
  } else if (request.method == "apk.install") {
    const auto file_id = request.args.find("file_id");
    if (file_id == request.args.end()) {
      fail("FILE_ID_REQUIRED", "falta args.file_id");
    } else {
      const auto validation = apk_validator.validate_file_id(file_id->second);
      if (!validation.ok) {
        fail(validation.error_code, validation.message);
      } else {
        command(runtime.install(validation.path.string()));
      }
    }
  } else if (request.method == "capabilities") {
    response.ok = true;
    response.result = {{"graphics", "wayland-egl"}, {"input", "libinput-evdev"},
                       {"audio", "pipewire-alsa"}, {"storage", "flurry-store"},
                       {"network", "networkmanager"}, {"runtime", "cuttlefish-adb"}};
  } else if (request.method == "translate") {
    const auto domain = request.args.find("domain");
    const auto operation = request.args.find("operation");
    if (domain == request.args.end() || operation == request.args.end()) {
      fail("TRANSLATION_ARGS_REQUIRED", "faltan args.domain o args.operation");
    } else {
      const auto translated = translator.translate(domain->second + " " + operation->second);
      response.ok = translated.status == flurryos::TranslationStatus::Supported;
      response.result = {{"domain", translated.domain}, {"backend", std::string(flurryos::backend_name(translated.backend))},
                         {"action", std::string(flurryos::action_name(translated.action))},
                         {"native_executable", translated.native_call.executable},
                         {"native_call", flurryos::native_call_string(translated.native_call)},
                         {"detail", translated.detail}};
      if (!response.ok) {
        response.error_code = "BACKEND_UNSUPPORTED";
        response.message = translated.detail;
        response.result.clear();
      }
    }
  } else {
    fail("METHOD_NOT_FOUND", "método no permitido");
  }
  return response;
}

}  // namespace

int main(int argc, char** argv) {
  socket_path = parse_socket_path(argc, argv);
  const std::filesystem::path path(socket_path);
  std::error_code error;
  std::filesystem::create_directories(path.parent_path(), error);
  if (error) {
    std::cerr << "No se pudo crear el directorio del socket: " << error.message() << '\n';
    return 1;
  }

  const std::string adb_binary = env_or("FLURRYOS_ADB_BIN", "/usr/bin/adb");
  const std::string serial = env_or("FLURRYOS_ANDROID_SERIAL", "localhost:6520");
  const std::string android_home = env_or("FLURRYOS_ANDROID_HOME", "/var/lib/flurryos/android");
  const std::string apk_inbox = env_or("FLURRYOS_APK_INBOX", "/var/lib/flurryos/apks/inbox");
  flurryos::RuntimeController runtime(adb_binary, serial, android_home);
  const flurryos::Translator translator;
  const flurryos::ApkValidator apk_validator(apk_inbox);

  std::signal(SIGINT, stop_server);
  std::signal(SIGTERM, stop_server);
  std::signal(SIGPIPE, SIG_IGN);

  unlink(socket_path.c_str());
  server_fd = socket(AF_UNIX, SOCK_STREAM, 0);
  if (server_fd < 0) {
    std::cerr << "No se pudo crear el socket: " << std::strerror(errno) << '\n';
    return 2;
  }

  sockaddr_un address{};
  address.sun_family = AF_UNIX;
  if (socket_path.size() >= sizeof(address.sun_path)) {
    std::cerr << "Ruta del socket demasiado larga\n";
    close(server_fd);
    return 3;
  }
  std::strncpy(address.sun_path, socket_path.c_str(), sizeof(address.sun_path) - 1U);

  if (bind(server_fd, reinterpret_cast<sockaddr*>(&address), sizeof(address)) < 0 || listen(server_fd, 16) < 0) {
    std::cerr << "No se pudo publicar el socket: " << std::strerror(errno) << '\n';
    close(server_fd);
    unlink(socket_path.c_str());
    return 4;
  }
  chmod(socket_path.c_str(), 0660);

  std::cout << "flurryos-bridge escuchando en " << socket_path << '\n';
  while (running != 0) {
    const int client_fd = accept(server_fd, nullptr, nullptr);
    if (client_fd < 0) {
      if (errno == EINTR) {
        continue;
      }
      if (running != 0) {
        std::cerr << "Error aceptando cliente: " << std::strerror(errno) << '\n';
      }
      break;
    }

    char buffer[4096]{};
    const ssize_t count = read(client_fd, buffer, sizeof(buffer) - 1U);
    if (count > 0) {
      const std::string request_line(buffer, static_cast<std::size_t>(count));
      const auto first = request_line.find_first_not_of(" \\t\\r\\n");
      if (first != std::string::npos && request_line[first] == '{') {
        flurryos::JsonRequest request;
        std::string parse_error;
        flurryos::JsonResponse response;
        if (flurryos::JsonProtocol::parse_request(request_line, request, parse_error)) {
          response = handle_json_request(runtime, translator, apk_validator, request);
        } else {
          response.id = "";
          response.error_code = "INVALID_JSON";
          response.message = parse_error;
        }
        write_all(client_fd, flurryos::JsonProtocol::serialize_response(response) + "\\n");
      } else {
        const flurryos::CommandResult result = handle_command(runtime, translator, request_line);
        const std::string response = (result.ok ? "OK " : "ERROR ") + result.payload + "\\n";
        write_all(client_fd, response);
      }
    }
    close(client_fd);
  }

  close(server_fd);
  server_fd = -1;
  unlink(socket_path.c_str());
  return 0;
}
