#pragma once

#include <cstddef>
#include <cstdint>
#include <vector>

struct TfLiteInterpreter;

namespace litert {

class InferenceRunner {
public:
    // Run super-resolution. Input/output: NHWC float32 [0,1].
    static bool runSuperRes(TfLiteInterpreter* interpreter,
                            const float* input, float* output);

    // Run inpainting. image: NHWC [1,H,W,3] in [0,1], mask: NHWC [1,H,W,1] in [0,1].
    // Output: NHWC [1,H,W,3] in [0,1] — model internally handles normalization + blending.
    static bool runInpainting(TfLiteInterpreter* interpreter,
                              const float* image, const float* mask,
                              float* output);

private:
    static bool invokeInterpreter(TfLiteInterpreter* interpreter);
};

} // namespace litert
