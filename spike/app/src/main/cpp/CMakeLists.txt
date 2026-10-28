# Built only when the Bergamot submodule (third_party/bergamot-translator) is initialised; Gradle passes BERGAMOT_DIR.
cmake_minimum_required(VERSION 3.22.1)
project(spike_native CXX)

set(CMAKE_CXX_STANDARD 17)

add_subdirectory(${BERGAMOT_DIR} bergamot)

add_library(bergamot_jni SHARED bergamot_jni.cpp)
target_include_directories(bergamot_jni PRIVATE ${BERGAMOT_DIR}/src)
target_link_libraries(bergamot_jni bergamot-translator android log)
