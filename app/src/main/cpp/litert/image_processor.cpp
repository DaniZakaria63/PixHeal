#include "image_processor.h"

#include <algorithm>
#include <cmath>

namespace litert {

void ImageProcessor::resizeBilinear(const uint8_t* src, int sw, int sh, int sc,
                                     uint8_t* dst, int dw, int dh) {
    float xRatio = static_cast<float>(sw) / dw;
    float yRatio = static_cast<float>(sh) / dh;

    for (int y = 0; y < dh; ++y) {
        float srcY = y * yRatio;
        int y0 = static_cast<int>(srcY);
        int y1 = std::min(y0 + 1, sh - 1);
        float yFrac = srcY - y0;

        for (int x = 0; x < dw; ++x) {
            float srcX = x * xRatio;
            int x0 = static_cast<int>(srcX);
            int x1 = std::min(x0 + 1, sw - 1);
            float xFrac = srcX - x0;

            for (int c = 0; c < sc; ++c) {
                float top = src[(y0 * sw + x0) * sc + c] * (1 - xFrac) +
                            src[(y0 * sw + x1) * sc + c] * xFrac;
                float bot = src[(y1 * sw + x0) * sc + c] * (1 - xFrac) +
                            src[(y1 * sw + x1) * sc + c] * xFrac;
                dst[(y * dw + x) * sc + c] = static_cast<uint8_t>(
                    std::clamp(top * (1 - yFrac) + bot * yFrac, 0.0f, 255.0f));
            }
        }
    }
}

std::vector<float> ImageProcessor::preprocessSuperRes(
    const uint8_t* pixels, int srcWidth, int srcHeight,
    int targetWidth, int targetHeight) {

    std::vector<uint8_t> resized(targetWidth * targetHeight * 4);
    resizeBilinear(pixels, srcWidth, srcHeight, 4,
                   resized.data(), targetWidth, targetHeight);

    std::vector<float> tensor(targetHeight * targetWidth * 3);
    for (int i = 0; i < targetHeight * targetWidth; ++i) {
        tensor[i * 3 + 0] = resized[i * 4 + 1] / 255.0f; // R
        tensor[i * 3 + 1] = resized[i * 4 + 2] / 255.0f; // G
        tensor[i * 3 + 2] = resized[i * 4 + 3] / 255.0f; // B
    }
    return tensor;
}

ImageProcessor::InpaintingInput ImageProcessor::preprocessInpainting(
    const uint8_t* imagePixels, const uint8_t* maskPixels,
    int srcWidth, int srcHeight,
    int targetWidth, int targetHeight) {

    InpaintingInput result;
    result.image.resize(targetHeight * targetWidth * 3);
    result.mask.resize(targetHeight * targetWidth);

    std::vector<uint8_t> imgResized(targetWidth * targetHeight * 4);
    resizeBilinear(imagePixels, srcWidth, srcHeight, 4,
                   imgResized.data(), targetWidth, targetHeight);

    std::vector<uint8_t> maskSingle(srcWidth * srcHeight);
    for (int i = 0; i < srcWidth * srcHeight; ++i) {
        maskSingle[i] = maskPixels[i * 4];
    }
    std::vector<uint8_t> maskResized(targetWidth * targetHeight);
    resizeBilinear(maskSingle.data(), srcWidth, srcHeight, 1,
                   maskResized.data(), targetWidth, targetHeight);

    for (int i = 0; i < targetHeight * targetWidth; ++i) {
        result.image[i * 3 + 0] = imgResized[i * 4 + 1] / 255.0f;
        result.image[i * 3 + 1] = imgResized[i * 4 + 2] / 255.0f;
        result.image[i * 3 + 2] = imgResized[i * 4 + 3] / 255.0f;
        result.mask[i] = maskResized[i] / 255.0f;
    }
    return result;
}

} // namespace litert
