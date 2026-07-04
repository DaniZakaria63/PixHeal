#pragma once

#include <cstddef>
#include <cstdint>
#include <vector>

namespace litert {

class ImageProcessor {
public:
    // Pre-process for Real-ESRGAN-x4plus:
    //   ARGB_8888 → resize bilinear → extract RGB → normalize [0,1]
    //   Output: NHWC [1, H, W, 3] float32 in [0, 1]
    static std::vector<float> preprocessSuperRes(
        const uint8_t* pixels, int srcWidth, int srcHeight,
        int targetWidth, int targetHeight);

    // Pre-process for AOT-GAN inpainting:
    //   Both image and mask: ARGB_8888 → resize bilinear → extract single
    //   channel (mask: R channel, image: RGB) → normalize [0,1]
    //   Model internally handles: [0,1]→[-1,1], mask application, inference,
    //   and output blending. We just feed raw [0,1] tensors.
    struct InpaintingInput {
        std::vector<float> image; // NHWC [1, H, W, 3] in [0, 1]
        std::vector<float> mask;  // NHWC [1, H, W, 1] in [0, 1]
    };
    static InpaintingInput preprocessInpainting(
        const uint8_t* imagePixels, const uint8_t* maskPixels,
        int srcWidth, int srcHeight,
        int targetWidth, int targetHeight);

    // Post-process: output float [0,1] NHWC → ARGB_8888 bytes
    // Used for both models since both output [0,1] RGB.
    static std::vector<uint8_t> postprocessToARGB(
        const float* output, int outWidth, int outHeight);

private:
    static void resizeBilinear(const uint8_t* src, int sw, int sh, int sc,
                               uint8_t* dst, int dw, int dh);
};

} // namespace litert
