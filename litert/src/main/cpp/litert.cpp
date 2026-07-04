// litert.cpp — Single JNI boundary for the LiteRT module.
// Allocates LitertBridge instances on the heap, returns opaque jlong handles
// to Kotlin. Kotlin owns the lifecycle — must call nativeClose(handle) to free.
//
// Thread safety: registry access is protected by a mutex. Inference calls
// hold a shared reference to the bridge while running, preventing close()
// from freeing memory mid-inference.

#include "litert_bridge.h"

#include <android/asset_manager.h>
#include <android/asset_manager_jni.h>
#include <android/bitmap.h>
#include <android/log.h>
#include <jni.h>

#include <cstring>
#include <mutex>
#include <shared_mutex>
#include <unordered_map>
#include <vector>

#define LOG_TAG "litert"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

static std::unordered_map<jlong, std::shared_ptr<litert::LitertBridge>> registry;
static jlong nextHandle = 1;
static std::shared_mutex registryMutex;

static std::shared_ptr<litert::LitertBridge> getBridge(jlong handle) {
    std::shared_lock lock(registryMutex);
    auto it = registry.find(handle);
    return (it != registry.end()) ? it->second : nullptr;
}

static std::vector<uint8_t> readAssetFile(JNIEnv* env, jobject assetManager,
                                           const std::string& filename) {
    AAssetManager* mgr = AAssetManager_fromJava(env, assetManager);
    if (!mgr) {
        LOGE("Failed to get AAssetManager");
        return {};
    }
    AAsset* asset = AAssetManager_open(mgr, filename.c_str(), AASSET_MODE_BUFFER);
    if (!asset) {
        LOGE("Failed to open asset: %s", filename.c_str());
        return {};
    }
    const void* buffer = AAsset_getBuffer(asset);
    off_t length = AAsset_getLength(asset);
    std::vector<uint8_t> result(static_cast<const uint8_t*>(buffer),
                                static_cast<const uint8_t*>(buffer) + length);
    AAsset_close(asset);
    return result;
}

static const uint8_t* lockBitmap(JNIEnv* env, jobject bitmap,
                                  AndroidBitmapInfo* info, int* width,
                                  int* height, int* stride) {
    if (AndroidBitmap_getInfo(env, bitmap, info) < 0) {
        LOGE("Failed to get bitmap info");
        return nullptr;
    }
    if (info->format != ANDROID_BITMAP_FORMAT_RGBA_8888) {
        LOGE("Bitmap must be ARGB_8888");
        return nullptr;
    }
    void* pixels = nullptr;
    if (AndroidBitmap_lockPixels(env, bitmap, &pixels) < 0) {
        LOGE("Failed to lock bitmap pixels");
        return nullptr;
    }
    *width = info->width;
    *height = info->height;
    *stride = info->stride;
    return static_cast<const uint8_t*>(pixels);
}

// ── JNI Exports ──────────────────────────────────────────────────────────

extern "C" {

JNIEXPORT jlong JNICALL
Java_id_my_daniza_litert_LitertBridge_nativeLoadModel(
    JNIEnv* env, jclass /*clazz*/,
    jobject assetManager, jstring modelName, jint modelType, jintArray outShape) {

    const char* name = env->GetStringUTFChars(modelName, nullptr);
    std::string nameStr(name);
    env->ReleaseStringUTFChars(modelName, name);

    LOGI("Loading model: %s", nameStr.c_str());
    auto modelData = readAssetFile(env, assetManager, nameStr);
    if (modelData.empty()) {
        LOGE("Failed to read model file");
        return 0;
    }
    LOGI("Model file size: %zu bytes", modelData.size());

    auto bridge = std::make_shared<litert::LitertBridge>();
    auto type = static_cast<litert::ModelType>(modelType);
    if (!bridge->loadModel(modelData.data(), modelData.size(), type)) {
        LOGE("TfLite model creation failed");
        return 0;
    }

    std::unique_lock lock(registryMutex);
    jlong handle = nextHandle++;
    registry[handle] = bridge;
    lock.unlock();

    auto& info = bridge->getModelInfo();
    jint shape[2] = {info.outputWidth, info.outputHeight};
    env->SetIntArrayRegion(outShape, 0, 2, shape);

    LOGI("Model loaded (handle=%lld). Input: %dx%dx%d, Output: %dx%dx%d",
         static_cast<long long>(handle),
         info.inputWidth, info.inputHeight, info.inputChannels,
         info.outputWidth, info.outputHeight, info.outputChannels);
    return handle;
}

JNIEXPORT jfloatArray JNICALL
Java_id_my_daniza_litert_LitertBridge_nativeRunSuperRes(
    JNIEnv* env, jclass /*clazz*/,
    jlong handle, jobject bitmap) {

    auto bridge = getBridge(handle);
    if (!bridge || !bridge->isLoaded()) {
        LOGE("Invalid handle or model not loaded: %lld", static_cast<long long>(handle));
        return nullptr;
    }

    AndroidBitmapInfo info;
    int width = 0, height = 0, stride = 0;
    const uint8_t* pixels = lockBitmap(env, bitmap, &info, &width, &height, &stride);
    if (!pixels) return nullptr;

    auto result = bridge->runSuperRes(pixels, width, height, stride);
    AndroidBitmap_unlockPixels(env, bitmap);

    if (result.empty()) {
        LOGE("Super-res inference failed");
        return nullptr;
    }

    jfloatArray output = env->NewFloatArray(result.size());
    env->SetFloatArrayRegion(output, 0, result.size(), result.data());
    return output;
}

JNIEXPORT jfloatArray JNICALL
Java_id_my_daniza_litert_LitertBridge_nativeRunInpainting(
    JNIEnv* env, jclass /*clazz*/,
    jlong handle, jobject imageBitmap, jobject maskBitmap) {

    auto bridge = getBridge(handle);
    if (!bridge || !bridge->isLoaded()) {
        LOGE("Invalid handle or model not loaded: %lld", static_cast<long long>(handle));
        return nullptr;
    }

    AndroidBitmapInfo imgInfo, maskInfo;
    int imgW = 0, imgH = 0, imgStride = 0;
    int maskW = 0, maskH = 0, maskStride = 0;

    const uint8_t* imgPixels = lockBitmap(env, imageBitmap, &imgInfo,
                                           &imgW, &imgH, &imgStride);
    if (!imgPixels) return nullptr;

    const uint8_t* maskPixels = lockBitmap(env, maskBitmap, &maskInfo,
                                            &maskW, &maskH, &maskStride);
    if (!maskPixels) {
        AndroidBitmap_unlockPixels(env, imageBitmap);
        return nullptr;
    }

    auto result = bridge->runInpainting(imgPixels, imgW, imgH, imgStride,
                                         maskPixels, maskW, maskH, maskStride);

    AndroidBitmap_unlockPixels(env, imageBitmap);
    AndroidBitmap_unlockPixels(env, maskBitmap);

    if (result.empty()) {
        LOGE("Inpainting inference failed");
        return nullptr;
    }

    jfloatArray output = env->NewFloatArray(result.size());
    env->SetFloatArrayRegion(output, 0, result.size(), result.data());
    return output;
}

JNIEXPORT jintArray JNICALL
Java_id_my_daniza_litert_LitertBridge_nativeGetInputShape(
    JNIEnv* env, jclass /*clazz*/, jlong handle) {

    auto bridge = getBridge(handle);
    if (!bridge || !bridge->isLoaded()) {
        return nullptr;
    }
    const auto& info = bridge->getModelInfo();
    jintArray result = env->NewIntArray(4);
    jint shape[4] = {1, info.inputHeight, info.inputWidth, info.inputChannels};
    env->SetIntArrayRegion(result, 0, 4, shape);
    return result;
}

JNIEXPORT void JNICALL
Java_id_my_daniza_litert_LitertBridge_nativeClose(
    JNIEnv* /*env*/, jclass /*clazz*/, jlong handle) {

    std::shared_ptr<litert::LitertBridge> toDelete;
    {
        std::unique_lock lock(registryMutex);
        auto it = registry.find(handle);
        if (it != registry.end()) {
            toDelete = it->second;
            registry.erase(it);
        }
    }
    LOGI("Closing model (handle=%lld)", static_cast<long long>(handle));
}

} // extern "C"
