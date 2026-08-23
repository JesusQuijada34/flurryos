cmake_minimum_required(VERSION 3.16)
project(flurryos_bridge VERSION 0.1.0 LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)
set(CMAKE_BUILD_WITH_INSTALL_RPATH TRUE)

add_library(flurryos_bridge_core
  src/bridge_protocol.cpp
  src/runtime_controller.cpp
  src/translator.cpp
)
target_include_directories(flurryos_bridge_core PUBLIC include)
target_compile_options(flurryos_bridge_core PRIVATE -Wall -Wextra -Wpedantic -Wconversion)

add_executable(flurryos-bridge
  src/main.cpp
)
target_link_libraries(flurryos-bridge PRIVATE flurryos_bridge_core)
target_compile_options(flurryos-bridge PRIVATE -Wall -Wextra -Wpedantic -Wconversion)

include(CTest)
if(BUILD_TESTING)
  add_executable(bridge_smoke_test tests/bridge_smoke_test.cpp)
  target_link_libraries(bridge_smoke_test PRIVATE flurryos_bridge_core)
  target_compile_options(bridge_smoke_test PRIVATE -Wall -Wextra -Wpedantic -Wconversion)
  add_test(NAME bridge_smoke_test COMMAND bridge_smoke_test)
  add_executable(translator_test tests/translator_test.cpp)
  target_link_libraries(translator_test PRIVATE flurryos_bridge_core)
  target_compile_options(translator_test PRIVATE -Wall -Wextra -Wpedantic -Wconversion)
  add_test(NAME translator_test COMMAND translator_test)
endif()

install(TARGETS flurryos-bridge RUNTIME DESTINATION usr/lib/flurryos)
install(FILES include/bridge_protocol.h include/translator.h DESTINATION usr/include/flurryos)
