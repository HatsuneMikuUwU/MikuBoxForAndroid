cmake_minimum_required(VERSION 3.22.1)
project(mikubox_core)

if(NOT DEFINED MIHOMO_JNI_LIBS_DIR)
    message(FATAL_ERROR "MIHOMO_JNI_LIBS_DIR was not supplied by Gradle")
endif()

set(MIHOMO_LIBRARY "${MIHOMO_JNI_LIBS_DIR}/${ANDROID_ABI}/libmihomo.so")
if(NOT EXISTS "${MIHOMO_LIBRARY}")
    message(FATAL_ERROR "Mihomo bridge is missing for ${ANDROID_ABI}: ${MIHOMO_LIBRARY}")
endif()

add_library(mihomo SHARED IMPORTED)
set_target_properties(mihomo PROPERTIES IMPORTED_LOCATION "${MIHOMO_LIBRARY}")

add_library(mikubox_core SHARED mihomo_jni.cpp)
target_link_libraries(mikubox_core mihomo log)
