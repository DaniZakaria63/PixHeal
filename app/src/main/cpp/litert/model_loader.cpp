#include "model_loader.h"

#include "tensorflow/lite/c/c_api.h"

#include <cstring>

namespace litert {

ModelLoader::ModelLoader() = default;

ModelLoader::~ModelLoader() {
    close();
}

bool ModelLoader::loadFromBuffer(const uint8_t* modelData, size_t modelSize, ModelType type) {
    close();

    model_ = TfLiteModelCreate(modelData, modelSize);
    if (!model_) {
        return false;
    }

    options_ = TfLiteInterpreterOptionsCreate();
    TfLiteInterpreterOptionsSetNumThreads(options_, 4);

    interpreter_ = TfLiteInterpreterCreate(model_, options_);
    if (!interpreter_) {
        TfLiteModelDelete(model_);
        model_ = nullptr;
        return false;
    }

    if (TfLiteInterpreterAllocateTensors(interpreter_) != kTfLiteOk) {
        TfLiteInterpreterDelete(interpreter_);
        interpreter_ = nullptr;
        TfLiteModelDelete(model_);
        model_ = nullptr;
        return false;
    }

    populateModelInfo(type);
    loaded_ = true;
    return true;
}

bool ModelLoader::isLoaded() const {
    return loaded_;
}

const ModelInfo& ModelLoader::getModelInfo() const {
    return info_;
}

TfLiteInterpreter* ModelLoader::getInterpreter() {
    return interpreter_;
}

TfLiteModel* ModelLoader::getModel() {
    return model_;
}

void ModelLoader::populateModelInfo(ModelType type) {
    info_.type = type;

    // Input tensor 0 = image (always present for both models)
    const TfLiteTensor* inputImage = TfLiteInterpreterGetInputTensor(interpreter_, 0);
    info_.inputHeight = TfLiteTensorDim(inputImage, 1);
    info_.inputWidth = TfLiteTensorDim(inputImage, 2);
    info_.inputChannels = TfLiteTensorDim(inputImage, 3);

    // Mask tensor (only for inpainting models)
    if (type == ModelType::Inpainting && TfLiteInterpreterGetInputTensorCount(interpreter_) > 1) {
        info_.maskChannels = TfLiteTensorDim(
            TfLiteInterpreterGetInputTensor(interpreter_, 1), 3);
    } else {
        info_.maskChannels = 0;
    }

    // Output tensor 0 = result image
    const TfLiteTensor* output = TfLiteInterpreterGetOutputTensor(interpreter_, 0);
    info_.outputHeight = TfLiteTensorDim(output, 1);
    info_.outputWidth = TfLiteTensorDim(output, 2);
    info_.outputChannels = TfLiteTensorDim(output, 3);
}

void ModelLoader::close() {
    if (interpreter_) {
        TfLiteInterpreterDelete(interpreter_);
        interpreter_ = nullptr;
    }
    if (options_) {
        TfLiteInterpreterOptionsDelete(options_);
        options_ = nullptr;
    }
    if (model_) {
        TfLiteModelDelete(model_);
        model_ = nullptr;
    }
    loaded_ = false;
}

} // namespace litert
