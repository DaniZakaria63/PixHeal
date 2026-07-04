#pragma once

#include "model_loader.h"
#include "image_processor.h"
#include "inference_runner.h"

#include <android/asset_manager.h>
#include <android/bitmap.h>
#include <jni.h>
#include <memory>
#include <string>
#include <vector>

namespace litert {
namespace jni {

// Singleton holding the currently loaded model and pipeline
class LitertBridge {
public:
    static LitertBridge& getInstance();

    bool loadModel(JNIEnv* env, jobject assetManager, jstring modelName,
                   ModelType type);
    bool runSuperResInference(JNIEnv* env, jobject bitmap, jfloatArray output);
    bool runInpaintingInference(JNIEnv* env, jobject imageBitmap,
                                jobject maskBitmap, jfloatArray output);
    jintArray getInputShape(JNIEnv* env);
    void close();

private:
    LitertBridge() = default;

    std::unique_ptr<ModelLoader> modelLoader_;
    ModelInfo modelInfo_{};
    bool modelLoaded_ = false;

    std::vector<uint8_t> readAssetFile(JNIEnv* env, jobject assetManager,
                                       const std::string& filename);
    std::vector<uint8_t> readBitmapPixels(JNIEnv* env, jobject bitmap,
                                          AndroidBitmapInfo* outInfo);
};

} // namespace jni
} // namespace litert
