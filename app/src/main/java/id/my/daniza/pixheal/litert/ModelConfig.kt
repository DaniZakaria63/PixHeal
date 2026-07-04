package id.my.daniza.pixheal.litert

data class ModelConfig(
    val inputWidth: Int,
    val inputHeight: Int,
    val inputChannels: Int,
    val outputWidth: Int,
    val outputHeight: Int,
    val outputChannels: Int
) {
    companion object {
        /** Real-ESRGAN-x4plus default input resolution */
        val ESRGAN_DEFAULT = ModelConfig(128, 128, 3, 512, 512, 3)
        /** AOT-GAN default input resolution */
        val AOTGAN_DEFAULT = ModelConfig(512, 512, 3, 512, 512, 3)
    }
}
