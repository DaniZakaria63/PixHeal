// litert.cpp — Single JNI boundary for the LiteRT module.
// Only handles JNI ↔ C++ type conversion. All model logic lives in litert_bridge.cpp.
// Kotlin only talks to this file.

#include "litert_bridge.h"

#include <android/asset_manager.h>
#include <android/asset_manager_jni.h>
#include <android/bitmap.h>
#include <android/log.h>
#include <jni.h>

#include <cstring>
#include <vector>

#define LOG_TAG "litert"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// ── Helpers ──────────────────────────────────────────────────────────────

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

JNIEXPORT jboolean JNICALL
Java_id_my_daniza_pixheal_litert_LitertBridge_nativeLoadModel(
    JNIEnv* env, jclass /*clazz*/,
    jobject assetManager, jstring modelName, jint modelType) {

    const char* name = env->GetStringUTFChars(modelName, nullptr);
    std::string nameStr(name);
    env->ReleaseStringUTFChars(modelName, name);

    LOGI("Loading model: %s", nameStr.c_str());
    auto modelData = readAssetFile(env, assetManager, nameStr);
    if (modelData.empty()) {
        LOGE("Failed to read model file");
        return JNI_FALSE;
    }
    LOGI("Model file size: %zu bytes", modelData.size());

    auto type = static_cast<litert::ModelType>(modelType);
    bool ok = litert::LitertBridge::getInstance().loadModel(
        modelData.data(), modelData.size(), type);

    if (ok) {
        auto& info = litert::LitertBridge::getInstance().getModelInfo();
        LOGI("Model loaded. Input: %dx%dx%d, Output: %dx%dx%d",
             info.inputWidth, info.inputHeight, info.inputChannels,
             info.outputWidth, info.outputHeight, info.outputChannels);
    }
    return ok ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_id_my_daniza_pixheal_litert_LitertBridge_nativeRunSuperRes(
    JNIEnv* env, jclass /*clazz*/,
    jobject bitmap, jfloatArray output) {

    AndroidBitmapInfo info;
    int width = 0, height = 0, stride = 0;
    const uint8_t* pixels = lockBitmap(env, bitmap, &info, &width, &height, &stride);
    if (!pixels) return JNI_FALSE;

    auto& bridge = litert::LitertBridge::getInstance();
    const auto& modelInfo = bridge.getModelInfo();
    int outputSize = modelInfo.outputHeight * modelInfo.outputWidth
                     * modelInfo.outputChannels;
    std::vector<float> outputFloat(outputSize);

    bool ok = bridge.runSuperResInference(pixels, width, height, stride,
                                           outputFloat.data());
    AndroidBitmap_unlockPixels(env, bitmap);

    if (!ok) {
        LOGE("Super-res inference failed");
        return JNI_FALSE;
    }

    env->SetFloatArrayRegion(output, 0, outputSize, outputFloat.data());
    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL
Java_id_my_daniza_pixheal_litert_LitertBridge_nativeRunInpainting(
    JNIEnv* env, jclass /*clazz*/,
    jobject imageBitmap, jobject maskBitmap, jfloatArray output) {

    AndroidBitmapInfo imgInfo, maskInfo;
    int imgW = 0, imgH = 0, imgStride = 0;
    int maskW = 0, maskH = 0, maskStride = 0;

    const uint8_t* imgPixels = lockBitmap(env, imageBitmap, &imgInfo,
                                           &imgW, &imgH, &imgStride);
    if (!imgPixels) return JNI_FALSE;

    const uint8_t* maskPixels = lockBitmap(env, maskBitmap, &maskInfo,
                                            &maskW, &maskH, &maskStride);
    if (!maskPixels) {
        AndroidBitmap_unlockPixels(env, imageBitmap);
        return JNI_FALSE;
    }

    auto& bridge = litert::LitertBridge::getInstance();
    const auto& modelInfo = bridge.getModelInfo();
    int outputSize = modelInfo.outputHeight * modelInfo.outputWidth
                     * modelInfo.outputChannels;
    std::vector<float> outputFloat(outputSize);

    bool ok = bridge.runInpaintingInference(imgPixels, imgW, imgH, imgStride,
                                             maskPixels, maskW, maskH, maskStride,
                                             outputFloat.data());

    AndroidBitmap_unlockPixels(env, imageBitmap);
    AndroidBitmap_unlockPixels(env, maskBitmap);

    if (!ok) {
        LOGE("Inpainting inference failed");
        return JNI_FALSE;
    }

    env->SetFloatArrayRegion(output, 0, outputSize, outputFloat.data());
    return JNI_TRUE;
}

JNIEXPORT jintArray JNICALL
Java_id_my_daniza_pixheal_litert_LitertBridge_nativeGetInputShape(
    JNIEnv* env, jclass /*clazz*/) {

    auto& bridge = litert::LitertBridge::getInstance();
    if (!bridge.isLoaded()) {
        return nullptr;
    }
    const auto& info = bridge.getModelInfo();
    jintArray result = env->NewIntArray(4);
    jint shape[4] = {1, info.inputHeight, info.inputWidth, info.inputChannels};
    env->SetIntArrayRegion(result, 0, 4, shape);
    return result;
}

JNIEXPORT void JNICALL
Java_id_my_daniza_pixheal_litert_LitertBridge_nativeClose(
    JNIEnv* /*env*/, jclass /*clazz*/) {

    litert::LitertBridge::getInstance().close();
}

} // extern "C"
