#include "inference_runner.h"

#include "tensorflow/lite/c/c_api.h"

#include <cstring>

namespace litert {

bool InferenceRunner::runSuperRes(TfLiteInterpreter* interpreter,
                                   const float* input, float* output) {
    if (!interpreter) return false;

    TfLiteTensor* inputTensor = TfLiteInterpreterGetInputTensor(interpreter, 0);
    size_t inputBytes = TfLiteTensorByteSize(inputTensor);
    TfLiteStatus copyInStatus = TfLiteTensorCopyFromBuffer(inputTensor, input, inputBytes);
    if (copyInStatus != kTfLiteOk) {
        return false;
    }

    if (!invokeInterpreter(interpreter)) {
        return false;
    }

    const TfLiteTensor* outputTensor = TfLiteInterpreterGetOutputTensor(interpreter, 0);
    size_t outputBytes = TfLiteTensorByteSize(outputTensor);
    TfLiteStatus copyOutStatus = TfLiteTensorCopyToBuffer(outputTensor, output, outputBytes);
    if (copyOutStatus != kTfLiteOk) {
        return false;
    }

    return true;
}

bool InferenceRunner::runInpainting(TfLiteInterpreter* interpreter,
                                     const float* image, const float* mask,
                                     float* output) {
    if (!interpreter) return false;

    // Input 0: image tensor [0,1] NHWC — model internally normalizes to [-1,1]
    TfLiteTensor* imgTensor = TfLiteInterpreterGetInputTensor(interpreter, 0);
    if (TfLiteTensorCopyFromBuffer(imgTensor, image,
                                    TfLiteTensorByteSize(imgTensor)) != kTfLiteOk) {
        return false;
    }

    // Input 1: mask tensor (if model has mask input)
    if (TfLiteInterpreterGetInputTensorCount(interpreter) > 1) {
        TfLiteTensor* maskTensor = TfLiteInterpreterGetInputTensor(interpreter, 1);
        if (TfLiteTensorCopyFromBuffer(maskTensor, mask,
                                        TfLiteTensorByteSize(maskTensor)) != kTfLiteOk) {
            return false;
        }
    }

    if (!invokeInterpreter(interpreter)) {
        return false;
    }

    const TfLiteTensor* outputTensor = TfLiteInterpreterGetOutputTensor(interpreter, 0);
    if (TfLiteTensorCopyToBuffer(outputTensor, output,
                                  TfLiteTensorByteSize(outputTensor)) != kTfLiteOk) {
        return false;
    }

    return true;
}

bool InferenceRunner::invokeInterpreter(TfLiteInterpreter* interpreter) {
    return TfLiteInterpreterInvoke(interpreter) == kTfLiteOk;
}

} // namespace litert
