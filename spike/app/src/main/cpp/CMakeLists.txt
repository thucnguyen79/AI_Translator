# Built only when the Bergamot sources are present in ./bergamot (see spike/README.md).
cmake_minimum_required(VERSION 3.22.1)
project(spike_native CXX)

set(CMAKE_CXX_STANDARD 17)

add_subdirectory(bergamot)

add_library(bergamot_jni SHARED bergamot_jni.cpp)
target_include_directories(bergamot_jni PRIVATE ${CMAKE_CURRENT_SOURCE_DIR}/bergamot/src)
target_link_libraries(bergamot_jni bergamot-translator android log)
