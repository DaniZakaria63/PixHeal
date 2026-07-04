#pragma once

#include <cstddef>
#include <cstdint>
#include <string>

struct TfLiteModel;
struct TfLiteInterpreterOptions;
struct TfLiteInterpreter;

namespace litert {

enum class ModelType {
    SuperResolution, // Real-ESRGAN-x4plus
    Inpainting        // AOT-GAN
};

struct ModelInfo {
    int inputWidth;
    int inputHeight;
    int inputChannels;
    int outputWidth;
    int outputHeight;
    int outputChannels;
    int maskChannels; // 0 for super-res, 1 for inpainting
    ModelType type;
};

class ModelLoader {
public:
    ModelLoader();
    ~ModelLoader();

    ModelLoader(const ModelLoader&) = delete;
    ModelLoader& operator=(const ModelLoader&) = delete;

    bool loadFromBuffer(const uint8_t* modelData, size_t modelSize, ModelType type);
    bool isLoaded() const;
    const ModelInfo& getModelInfo() const;

    TfLiteInterpreter* getInterpreter();
    TfLiteModel* getModel();

    void close();

private:
    TfLiteModel* model_ = nullptr;
    TfLiteInterpreterOptions* options_ = nullptr;
    TfLiteInterpreter* interpreter_ = nullptr;
    ModelInfo info_{};
    bool loaded_ = false;

    void populateModelInfo(ModelType type);
};

} // namespace litert
