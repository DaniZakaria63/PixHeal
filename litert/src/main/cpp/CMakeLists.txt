

# DEVELOPER NOTE:
# LiteRT inference in native is unstable, we move to stable version under LiteRT Maven AAR.
# >Adjust later when dev has much time.

cmake_minimum_required(VERSION 3.22.1)

project("litert")

# ── LiteRT SDK paths ──────────────────────────────────────────────────
set(LITERT_SDK_DIR "${CMAKE_CURRENT_SOURCE_DIR}/litert_sdk" CACHE PATH "Extracted LiteRT AAR")
set(LITERT_INCLUDE_DIR "${LITERT_SDK_DIR}/headers/")
set(LITERT_LIB_DIR "${LITERT_SDK_DIR}/jni/${ANDROID_ABI}")

# ── LiteRT prebuilt shared library ────────────────────────────────────
add_library(tensorflowlite_jni SHARED IMPORTED)
set_target_properties(tensorflowlite_jni PROPERTIES
    IMPORTED_LOCATION "${LITERT_LIB_DIR}/libtensorflowlite_jni.so"
    INTERFACE_INCLUDE_DIRECTORIES "${LITERT_INCLUDE_DIR}"
)

# ── litert_core — pure C++ model handler, zero JNI dependencies ───────
add_library(litert_core STATIC
    model_loader.cpp
    image_processor.cpp
    inference_runner.cpp
    litert_bridge.cpp
)

target_include_directories(litert_core PUBLIC
    "${LITERT_INCLUDE_DIR}"
    "${CMAKE_CURRENT_SOURCE_DIR}"
)

target_link_libraries(litert_core PUBLIC
    tensorflowlite_jni
    log
)

# ── JNI shared library ───────────────────────────────────────────────
add_library(${CMAKE_PROJECT_NAME} SHARED
    litert.cpp
)

target_link_libraries(${CMAKE_PROJECT_NAME}
    litert_core
    android
    log
    jnigraphics
)
