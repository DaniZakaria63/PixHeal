package id.my.daniza.litert.data

enum class TensorDataType { FLOAT32, UINT8 }

data class ModelConfig(
    val inputWidth: Int,
    val inputHeight: Int,
    val inputChannels: Int,
    val outputWidth: Int,
    val outputHeight: Int,
    val outputChannels: Int,
    val outputType: TensorDataType = TensorDataType.UINT8,
) {
    val inputPixels: Int get() = inputWidth * inputHeight * inputChannels
    val outputPixels: Int get() = outputWidth * outputHeight * outputChannels

    companion object {
        /** Real-ESRGAN-x4plus default resolution */
        val ESRGAN_DEFAULT = ModelConfig(128, 128, 3, 512, 512, 3, TensorDataType.UINT8)
        /** AOT-GAN default resolution */
        val AOTGAN_DEFAULT = ModelConfig(512, 512, 3, 512, 512, 3, TensorDataType.FLOAT32)
        /** DeepLabV3+ MobileNet segmentation */
        val DEEPLABV3_DEFAULT = ModelConfig(520, 520, 3, 520, 520, 21, TensorDataType.UINT8)
    }
}
