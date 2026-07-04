package id.my.daniza.pixheal.litert

data class InferenceResult(
    val scores: FloatArray,
    val outputWidth: Int,
    val outputHeight: Int,
    val channels: Int
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is InferenceResult) return false
        return outputWidth == other.outputWidth &&
                outputHeight == other.outputHeight &&
                channels == other.channels &&
                scores.contentEquals(other.scores)
    }

    override fun hashCode(): Int {
        var result = outputWidth
        result = 31 * result + outputHeight
        result = 31 * result + channels
        result = 31 * result + scores.contentHashCode()
        return result
    }
}
