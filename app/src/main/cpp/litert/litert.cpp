#include "litert.h"

#include <android/asset_manager_jni.h>
#include <android/log.h>
#include <cstring>

#define LOG_TAG "LitertBridge"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace litert {
namespace jni {

LitertBridge& LitertBridge::getInstance() {
    static LitertBridge instance;
    return instance;
}

std::vector<uint8_t> LitertBridge::readAssetFile(JNIEnv* env, jobject assetManager,
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

    std::vector<uint8_t> result(static_cast<size_t>(length));
    std::memcpy(result.data(), buffer, length);

    AAsset_close(asset);
    return result;
}

std::vector<uint8_t> LitertBridge::readBitmapPixels(JNIEnv* env, jobject bitmap,
                                                     AndroidBitmapInfo* outInfo) {
    if (AndroidBitmap_getInfo(env, bitmap, outInfo) < 0) {
        LOGE("Failed to get bitmap info");
        return {};
    }

    void* pixels = nullptr;
    if (AndroidBitmap_lockPixels(env, bitmap, &pixels) < 0) {
        LOGE("Failed to lock bitmap pixels");
        return {};
    }

    size_t size = outInfo->height * outInfo->stride;
    const auto* ptr = static_cast<const uint8_t*>(pixels);
    std::vector<uint8_t> result(ptr, ptr + size);

    AndroidBitmap_unlockPixels(env, bitmap);
    return result;
}

bool LitertBridge::loadModel(JNIEnv* env, jobject assetManager,
                              jstring modelName, ModelType type) {
    const char* nameChars = env->GetStringUTFChars(modelName, nullptr);
    std::string name(nameChars);
    env->ReleaseStringUTFChars(modelName, nameChars);

    LOGI("Loading model: %s", name.c_str());
    auto modelData = readAssetFile(env, assetManager, name);
    if (modelData.empty()) {
        LOGE("Failed to read model file: %s", name.c_str());
        return false;
    }
    LOGI("Model file size: %zu bytes", modelData.size());

    modelLoader_ = std::make_unique<ModelLoader>();
    if (!modelLoader_->loadFromBuffer(modelData.data(), modelData.size(), type)) {
        LOGE("TfLite model creation failed");
        modelLoader_.reset();
        return false;
    }

    modelInfo_ = modelLoader_->getModelInfo();
    modelLoaded_ = true;
    LOGI("Model loaded. Input: %dx%dx%d, Output: %dx%dx%d",
         modelInfo_.inputWidth, modelInfo_.inputHeight, modelInfo_.inputChannels,
         modelInfo_.outputWidth, modelInfo_.outputHeight, modelInfo_.outputChannels);
    return true;
}

bool LitertBridge::runSuperResInference(JNIEnv* env, jobject bitmap,
                                         jfloatArray output) {
    if (!modelLoaded_ || modelInfo_.type != ModelType::SuperResolution) {
        LOGE("Model not loaded or wrong model type");
        return false;
    }

    AndroidBitmapInfo info;
    auto pixels = readBitmapPixels(env, bitmap, &info);
    if (pixels.empty()) return false;

    if (info.format != ANDROID_BITMAP_FORMAT_RGBA_8888) {
        LOGE("Bitmap must be ARGB_8888 format");
        return false;
    }

    auto input = ImageProcessor::preprocessSuperRes(
        pixels.data(), info.width, info.height,
        modelInfo_.inputWidth, modelInfo_.inputHeight);

    int outputSize = modelInfo_.outputHeight * modelInfo_.outputWidth
                     * modelInfo_.outputChannels;
    std::vector<float> outputFloat(outputSize);

    if (!InferenceRunner::runSuperRes(modelLoader_->getInterpreter(),
                                       input.data(), outputFloat.data())) {
        LOGE("Inference failed");
        return false;
    }

    env->SetFloatArrayRegion(output, 0, outputSize, outputFloat.data());
    return true;
}

bool LitertBridge::runInpaintingInference(JNIEnv* env, jobject imageBitmap,
                                           jobject maskBitmap, jfloatArray output) {
    if (!modelLoaded_ || modelInfo_.type != ModelType::Inpainting) {
        LOGE("Model not loaded or wrong model type");
        return false;
    }

    AndroidBitmapInfo imgInfo;
    auto imgPixels = readBitmapPixels(env, imageBitmap, &imgInfo);
    if (imgPixels.empty()) return false;

    AndroidBitmapInfo maskInfo;
    auto maskPixels = readBitmapPixels(env, maskBitmap, &maskInfo);
    if (maskPixels.empty()) return false;

    if (imgInfo.format != ANDROID_BITMAP_FORMAT_RGBA_8888) {
        LOGE("Image must be ARGB_8888 format");
        return false;
    }

    auto preprocessed = ImageProcessor::preprocessInpainting(
        imgPixels.data(), maskPixels.data(),
        imgInfo.width, imgInfo.height,
        modelInfo_.inputWidth, modelInfo_.inputHeight);

    int outputSize = modelInfo_.outputHeight * modelInfo_.outputWidth
                     * modelInfo_.outputChannels;
    std::vector<float> outputFloat(outputSize);

    if (!InferenceRunner::runInpainting(modelLoader_->getInterpreter(),
                                         preprocessed.image.data(),
                                         preprocessed.mask.data(),
                                         outputFloat.data())) {
        LOGE("Inpainting inference failed");
        return false;
    }

    env->SetFloatArrayRegion(output, 0, outputSize, outputFloat.data());
    return true;
}

jintArray LitertBridge::getInputShape(JNIEnv* env) {
    if (!modelLoaded_) {
        return nullptr;
    }
    jintArray result = env->NewIntArray(4);
    jint shape[4] = {1, modelInfo_.inputHeight, modelInfo_.inputWidth,
                     modelInfo_.inputChannels};
    env->SetIntArrayRegion(result, 0, 4, shape);
    return result;
}

void LitertBridge::close() {
    if (modelLoader_) {
        modelLoader_->close();
        modelLoader_.reset();
    }
    modelLoaded_ = false;
}

} // namespace jni
} // namespace litert

// =====================================================================
// JNI Exports — these are the only symbols Kotlin calls
// =====================================================================

extern "C" {

JNIEXPORT jboolean JNICALL
Java_id_my_daniza_pixheal_litert_LitertBridge_nativeLoadModel(
    JNIEnv* env, jclass /*clazz*/,
    jobject assetManager, jstring modelName, jint modelType) {

    auto type = static_cast<litert::ModelType>(modelType);
    return litert::jni::LitertBridge::getInstance().loadModel(
        env, assetManager, modelName, type) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_id_my_daniza_pixheal_litert_LitertBridge_nativeRunSuperRes(
    JNIEnv* env, jclass /*clazz*/,
    jobject bitmap, jfloatArray output) {

    return litert::jni::LitertBridge::getInstance().runSuperResInference(
        env, bitmap, output) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_id_my_daniza_pixheal_litert_LitertBridge_nativeRunInpainting(
    JNIEnv* env, jclass /*clazz*/,
    jobject imageBitmap, jobject maskBitmap, jfloatArray output) {

    return litert::jni::LitertBridge::getInstance().runInpaintingInference(
        env, imageBitmap, maskBitmap, output) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jintArray JNICALL
Java_id_my_daniza_pixheal_litert_LitertBridge_nativeGetInputShape(
    JNIEnv* env, jclass /*clazz*/) {

    return litert::jni::LitertBridge::getInstance().getInputShape(env);
}

JNIEXPORT void JNICALL
Java_id_my_daniza_pixheal_litert_LitertBridge_nativeClose(
    JNIEnv* /*env*/, jclass /*clazz*/) {

    litert::jni::LitertBridge::getInstance().close();
}

} // extern "C"
