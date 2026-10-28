cmake_minimum_required(VERSION 3.22.1)
project(aitranslator_translate CXX)

set(CMAKE_CXX_STANDARD 17)

# BERGAMOT_DIR is passed by Gradle (third_party/bergamot-translator).
add_subdirectory(${BERGAMOT_DIR} bergamot)

add_library(bergamot_jni SHARED bergamot_jni.cpp)
target_include_directories(bergamot_jni PRIVATE ${BERGAMOT_DIR}/src)
target_link_libraries(bergamot_jni bergamot-translator android log)
