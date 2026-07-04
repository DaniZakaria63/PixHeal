#pragma once

#include "model_loader.h"
#include "image_processor.h"
#include "inference_runner.h"

#include <cstdint>
#include <memory>
#include <vector>

namespace litert {

class LitertBridge {
public:
    LitertBridge() = default;

    bool loadModel(const uint8_t* modelData, size_t modelSize, ModelType type);
    bool isLoaded() const;
    const ModelInfo& getModelInfo() const;

    // Runs inference. Returns float32 tensor — Kotlin handles bitmap creation.
    std::vector<float> runSuperRes(const uint8_t* pixelData, int width, int height,
                                   int stride);
    std::vector<float> runInpainting(const uint8_t* imagePixels,
                                     int imgWidth, int imgHeight, int imgStride,
                                     const uint8_t* maskPixels,
                                     int maskWidth, int maskHeight, int maskStride);

private:
    std::unique_ptr<ModelLoader> modelLoader_;
    bool modelLoaded_ = false;

    std::vector<uint8_t> extractPixelsARGB(const uint8_t* pixelData,
                                           int width, int height, int stride);
};

} // namespace litert
