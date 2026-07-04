#include "litert_bridge.h"

#include <cstring>

namespace litert {

std::vector<uint8_t> LitertBridge::extractPixelsARGB(const uint8_t* pixelData,
                                                      int width, int height, int stride) {
    std::vector<uint8_t> packed(width * height * 4);
    for (int y = 0; y < height; ++y) {
        const uint8_t* srcRow = pixelData + y * stride;
        uint8_t* dstRow = packed.data() + y * width * 4;
        std::memcpy(dstRow, srcRow, width * 4);
    }
    return packed;
}

bool LitertBridge::loadModel(const uint8_t* modelData, size_t modelSize, ModelType type) {
    modelLoaded_ = false;
    modelLoader_ = std::make_unique<ModelLoader>();
    if (!modelLoader_->loadFromBuffer(modelData, modelSize, type)) {
        modelLoader_.reset();
        return false;
    }
    modelLoaded_ = true;
    return true;
}

bool LitertBridge::isLoaded() const {
    return modelLoaded_;
}

const ModelInfo& LitertBridge::getModelInfo() const {
    return modelLoader_->getModelInfo();
}

bool LitertBridge::runSuperResInference(const uint8_t* pixelData, int width, int height,
                                         int stride, float* output) {
    if (!modelLoaded_) return false;

    auto packed = extractPixelsARGB(pixelData, width, height, stride);
    const auto& info = modelLoader_->getModelInfo();

    auto input = ImageProcessor::preprocessSuperRes(
        packed.data(), width, height, info.inputWidth, info.inputHeight);

    return InferenceRunner::runSuperRes(modelLoader_->getInterpreter(),
                                         input.data(), output);
}

bool LitertBridge::runInpaintingInference(const uint8_t* imagePixels,
                                           int imgWidth, int imgHeight, int imgStride,
                                           const uint8_t* maskPixels,
                                           int maskWidth, int maskHeight, int maskStride,
                                           float* output) {
    if (!modelLoaded_) return false;

    auto imgPacked = extractPixelsARGB(imagePixels, imgWidth, imgHeight, imgStride);
    auto maskPacked = extractPixelsARGB(maskPixels, maskWidth, maskHeight, maskStride);
    const auto& info = modelLoader_->getModelInfo();

    auto preprocessed = ImageProcessor::preprocessInpainting(
        imgPacked.data(), maskPacked.data(),
        imgWidth, imgHeight,
        info.inputWidth, info.inputHeight);

    return InferenceRunner::runInpainting(modelLoader_->getInterpreter(),
                                           preprocessed.image.data(),
                                           preprocessed.mask.data(),
                                           output);
}

} // namespace litert
