package id.my.daniza.pixheal.data.editing

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Bitmap.Config
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import id.my.daniza.litert.LitertBridge
import id.my.daniza.pixheal.data.ui.BasicAdjustValues
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

sealed class EffectResult {
    data class Success(val bitmap: Bitmap) : EffectResult()
    data class Error(val message: String) : EffectResult()
}

@Singleton
class EditEffectManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val litertBridge: LitertBridge,
) {
    private val mutex = Mutex()
    private var loadedModel: LoadedModel = LoadedModel.NONE

    private enum class LoadedModel { NONE, ESRGAN, AOTGAN, DEEPLABV3 }

    suspend fun enhance(uri: Uri, qualityMode: Boolean = false): EffectResult = withContext(Dispatchers.IO) {
        val scaleTarget = if (qualityMode) LitertBridge.MODE_QUALITY else LitertBridge.MODE_FAST
        Timber.i("enhance: %s mode (target=%d)", if (qualityMode) "quality" else "fast", scaleTarget)
        val start = System.currentTimeMillis()

        val bitmap = decodeBitmap(uri)
            ?: return@withContext EffectResult.Error("Failed to decode image")
        Timber.i("enhance: decoded %dx%d %s", bitmap.width, bitmap.height, bitmap.config)

        val loadErr = ensureModelLoaded(LoadedModel.ESRGAN)
        if (loadErr != null) return@withContext EffectResult.Error("AI model failed to start: $loadErr")

        val result = litertBridge.runSuperRes(bitmap, scaleTarget)
            ?: return@withContext EffectResult.Error("AI inference failed")

        val elapsed = System.currentTimeMillis() - start
        Timber.i("enhance: done in %dms, output %dx%d", elapsed, result.width, result.height)
        EffectResult.Success(result)
    }

    suspend fun inpaint(imageUri: Uri, maskBitmap: Bitmap): EffectResult = withContext(Dispatchers.IO) {
        val start = System.currentTimeMillis()

        val image = decodeBitmap(imageUri)
            ?: return@withContext EffectResult.Error("Failed to decode image")
        Timber.i("inpaint: decoded image %dx%d, mask %dx%d", image.width, image.height, maskBitmap.width, maskBitmap.height)

        val loadErr = ensureModelLoaded(LoadedModel.AOTGAN)
        if (loadErr != null) return@withContext EffectResult.Error("AI model failed to start: $loadErr")

        val result = litertBridge.runInpainting(image, maskBitmap)
            ?: return@withContext EffectResult.Error("AI inference failed")

        val final = blendResult(image, result, maskBitmap)
        result.recycle()

        val elapsed = System.currentTimeMillis() - start
        Timber.i("inpaint: done in %dms, output %dx%d", elapsed, final.width, final.height)
        EffectResult.Success(final)
    }

    suspend fun applyBasicAdjustments(uri: Uri, values: BasicAdjustValues): EffectResult = withContext(Dispatchers.IO) {
        if (values.isDefault) {
            val bitmap = decodeBitmap(uri)
                ?: return@withContext EffectResult.Error("Failed to decode image")
            return@withContext EffectResult.Success(bitmap)
        }
        val bitmap = decodeBitmap(uri)
            ?: return@withContext EffectResult.Error("Failed to decode image")
        val start = System.currentTimeMillis()
        val result = applyAdjustmentsToBitmap(bitmap, values)
        if (result !== bitmap) bitmap.recycle()
        Timber.i("adjustments: done in %dms", System.currentTimeMillis() - start)
        EffectResult.Success(result)
    }

    suspend fun applyBasicAdjustments(bitmap: Bitmap, values: BasicAdjustValues): EffectResult = withContext(Dispatchers.IO) {
        if (values.isDefault) return@withContext EffectResult.Success(bitmap.copy(Config.ARGB_8888, false))
        val start = System.currentTimeMillis()
        val result = applyAdjustmentsToBitmap(bitmap, values)
        Timber.i("adjustments (in-memory): done in %dms", System.currentTimeMillis() - start)
        EffectResult.Success(result)
    }

    suspend fun crop(uri: Uri, left: Int, top: Int, width: Int, height: Int): EffectResult = withContext(Dispatchers.IO) {
        val bitmap = decodeBitmap(uri)
            ?: return@withContext EffectResult.Error("Failed to decode image")
        val cropped = Bitmap.createBitmap(bitmap, left, top, width, height)
        bitmap.recycle()
        EffectResult.Success(cropped)
    }

    suspend fun rotate90(uri: Uri): EffectResult = withContext(Dispatchers.IO) {
        val bitmap = decodeBitmap(uri)
            ?: return@withContext EffectResult.Error("Failed to decode image")
        val rotated = Bitmap.createBitmap(bitmap.height, bitmap.width, Config.ARGB_8888)
        val canvas = Canvas(rotated)
        val matrix = Matrix().apply {
            postRotate(90f)
            postTranslate(rotated.width.toFloat(), 0f)
        }
        canvas.drawBitmap(bitmap, matrix, null)
        bitmap.recycle()
        EffectResult.Success(rotated)
    }

    suspend fun rotate270(uri: Uri): EffectResult = withContext(Dispatchers.IO) {
        val bitmap = decodeBitmap(uri)
            ?: return@withContext EffectResult.Error("Failed to decode image")
        val rotated = Bitmap.createBitmap(bitmap.height, bitmap.width, Config.ARGB_8888)
        val canvas = Canvas(rotated)
        val matrix = Matrix().apply {
            postRotate(270f)
            postTranslate(0f, rotated.height.toFloat())
        }
        canvas.drawBitmap(bitmap, matrix, null)
        bitmap.recycle()
        EffectResult.Success(rotated)
    }

    suspend fun flipHorizontal(uri: Uri): EffectResult = withContext(Dispatchers.IO) {
        val bitmap = decodeBitmap(uri)
            ?: return@withContext EffectResult.Error("Failed to decode image")
        val flipped = Bitmap.createBitmap(bitmap.width, bitmap.height, Config.ARGB_8888)
        val canvas = Canvas(flipped)
        val matrix = Matrix().apply { postScale(-1f, 1f, bitmap.width / 2f, bitmap.height / 2f) }
        canvas.drawBitmap(bitmap, matrix, null)
        bitmap.recycle()
        EffectResult.Success(flipped)
    }

    suspend fun flipVertical(uri: Uri): EffectResult = withContext(Dispatchers.IO) {
        val bitmap = decodeBitmap(uri)
            ?: return@withContext EffectResult.Error("Failed to decode image")
        val flipped = Bitmap.createBitmap(bitmap.width, bitmap.height, Config.ARGB_8888)
        val canvas = Canvas(flipped)
        val matrix = Matrix().apply { postScale(1f, -1f, bitmap.width / 2f, bitmap.height / 2f) }
        canvas.drawBitmap(bitmap, matrix, null)
        bitmap.recycle()
        EffectResult.Success(flipped)
    }

    suspend fun segmentImage(uri: Uri): IntArray? = withContext(Dispatchers.IO) {
        val bitmap = decodeBitmap(uri)
            ?: return@withContext null

        val loadErr = ensureModelLoaded(LoadedModel.DEEPLABV3)
        if (loadErr != null) {
            bitmap.recycle()
            return@withContext null
        }

        val result = litertBridge.runSegmentation(bitmap)
        bitmap.recycle()
        result
    }

    suspend fun removeBackground(
        originalUri: Uri,
        mask: IntArray,
        foregroundClasses: Set<Int> = setOf(15),
        threshold: Float = 0.5f,
        hardness: Float = 0.5f,
        edgeSoften: Float = 0f,
    ): EffectResult = withContext(Dispatchers.IO) {
        val bitmap = decodeBitmap(originalUri)
            ?: return@withContext EffectResult.Error("Failed to decode image")

        val w = bitmap.width
        val h = bitmap.height

        if (mask.size != w * h) {
            bitmap.recycle()
            return@withContext EffectResult.Error("Mask size mismatch: expected ${w * h}, got ${mask.size}")
        }

        val alphaMask = buildAlphaMask(mask, w, h, foregroundClasses, threshold, hardness, edgeSoften)
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

        for (i in pixels.indices) {
            val a = alphaMask[i].toInt() and 0xFF
            if (a == 0) {
                pixels[i] = 0
            } else if (a < 255) {
                val r = ((pixels[i] shr 16) and 0xFF) * a / 255
                val g = ((pixels[i] shr 8) and 0xFF) * a / 255
                val b = (pixels[i] and 0xFF) * a / 255
                pixels[i] = (a shl 24) or (r shl 16) or (g shl 8) or b
            }
        }

        bitmap.recycle()
        EffectResult.Success(Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888))
    }

    suspend fun removeBackgroundViaMask(originalUri: Uri, maskBitmap: Bitmap): EffectResult = withContext(Dispatchers.IO) {
        val bitmap = decodeBitmap(originalUri)
            ?: return@withContext EffectResult.Error("Failed to decode image")

        val w = bitmap.width
        val h = bitmap.height

        val maskScaled = if (maskBitmap.width != w || maskBitmap.height != h) {
            Bitmap.createScaledBitmap(maskBitmap, w, h, false)
        } else {
            maskBitmap
        }

        val maskPixels = IntArray(w * h)
        maskScaled.getPixels(maskPixels, 0, w, 0, 0, w, h)
        if (maskScaled !== maskBitmap) maskScaled.recycle()

        val origPixels = IntArray(w * h)
        bitmap.getPixels(origPixels, 0, w, 0, 0, w, h)
        bitmap.recycle()

        val dim = minOf(w, h)
        val featherPx = (dim * 0.01f).toInt().coerceAtLeast(2)
        val maskWithFeather = ByteArray(w * h)
        for (i in maskPixels.indices) {
            maskWithFeather[i] = ((maskPixels[i] shr 16) and 0xFF).toByte()
        }
        applyBoxBlur(maskWithFeather, w, h, featherPx)

        for (i in origPixels.indices) {
            val maskVal = maskWithFeather[i].toInt() and 0xFF
            if (maskVal <= 30) {
                origPixels[i] = 0
            } else if (maskVal < 255) {
                val r = ((origPixels[i] shr 16) and 0xFF) * maskVal / 255
                val g = ((origPixels[i] shr 8) and 0xFF) * maskVal / 255
                val b = (origPixels[i] and 0xFF) * maskVal / 255
                origPixels[i] = (maskVal shl 24) or (r shl 16) or (g shl 8) or b
            }
        }

        EffectResult.Success(Bitmap.createBitmap(origPixels, w, h, Bitmap.Config.ARGB_8888))
    }

    private fun buildAlphaMask(
        mask: IntArray,
        w: Int,
        h: Int,
        foregroundClasses: Set<Int>,
        threshold: Float,
        hardness: Float,
        edgeSoften: Float,
    ): ByteArray {
        val size = w * h
        val raw = ByteArray(size)

        for (i in mask.indices) {
            raw[i] = if (mask[i] in foregroundClasses) 255.toByte() else 0.toByte()
        }

        val dim = minOf(w, h).toFloat()
        if (edgeSoften > 0f) {
            val radius = (edgeSoften * dim * 0.05f).toInt().coerceAtLeast(1)
            applyBoxBlur(raw, w, h, radius)
        }

        if (hardness < 1f) {
            val edgeWidth = ((1f - hardness) * dim * 0.05f).toInt().coerceAtLeast(1)
            applyDistanceAlpha(raw, w, h, edgeWidth, threshold)
        }

        return raw
    }

    private fun applyBoxBlur(bytes: ByteArray, w: Int, h: Int, radius: Int) {
        if (radius < 1) return
        val r = radius.coerceAtMost(50)
        val temp = ByteArray(bytes.size)
        boxBlurHorizontal(bytes, temp, w, h, r)
        boxBlurVertical(temp, bytes, w, h, r)
    }

    private fun boxBlurHorizontal(src: ByteArray, dst: ByteArray, w: Int, h: Int, radius: Int) {
        for (y in 0 until h) {
            val rowStart = y * w
            var sum = 0
            var count = 0
            for (x in -radius until w) {
                val enterIdx = (x + radius).coerceIn(0, w - 1)
                sum += src[rowStart + enterIdx].toInt() and 0xFF
                count++
                if (x - radius - 1 >= 0) {
                    val leaveIdx = x - radius - 1
                    sum -= src[rowStart + leaveIdx].toInt() and 0xFF
                    count--
                }
                if (x >= 0) {
                    dst[rowStart + x] = (sum / count).toByte()
                }
            }
        }
    }

    private fun boxBlurVertical(src: ByteArray, dst: ByteArray, w: Int, h: Int, radius: Int) {
        for (x in 0 until w) {
            var sum = 0
            var count = 0
            for (y in -radius until h) {
                val enterIdx = (y + radius).coerceIn(0, h - 1)
                sum += src[enterIdx * w + x].toInt() and 0xFF
                count++
                if (y - radius - 1 >= 0) {
                    val leaveIdx = y - radius - 1
                    sum -= src[leaveIdx * w + x].toInt() and 0xFF
                    count--
                }
                if (y >= 0) {
                    dst[y * w + x] = (sum / count).toByte()
                }
            }
        }
    }

    private fun applyDistanceAlpha(bytes: ByteArray, w: Int, h: Int, edgeWidth: Int, threshold: Float) {
        val dist = IntArray(bytes.size) { Int.MAX_VALUE }
        val queue = ArrayDeque<Int>()

        for (i in bytes.indices) {
            val a = bytes[i].toInt() and 0xFF
            if (a == 0) {
                dist[i] = 0
                queue.addLast(i)
            }
            if (a == 255) {
                bytes[i] = 255.toByte()
            }
        }

        while (queue.isNotEmpty()) {
            val idx = queue.removeFirst()
            val d = dist[idx]
            if (d >= edgeWidth) continue

            val x = idx % w
            val y = idx / w
            val neighbors = listOf(
                if (x > 0) idx - 1 else -1,
                if (x < w - 1) idx + 1 else -1,
                if (y > 0) idx - w else -1,
                if (y < h - 1) idx + w else -1,
            )

            for (nIdx in neighbors) {
                if (nIdx >= 0 && dist[nIdx] > d + 1) {
                    dist[nIdx] = d + 1
                    queue.addLast(nIdx)
                    val t = (dist[nIdx].toFloat() / edgeWidth).coerceIn(0f, 1f)
                    val alpha = (t * 255f).toInt()
                    bytes[nIdx] = ((alpha * threshold).coerceIn(0f, 255f)).toInt().toByte()
                }
            }
        }
    }

    private fun applyAdjustmentsToBitmap(source: Bitmap, values: BasicAdjustValues): Bitmap {
        val w = source.width
        val h = source.height
        val pixels = IntArray(w * h)
        source.getPixels(pixels, 0, w, 0, 0, w, h)

        applyAdjustments(pixels, w, h, values)

        return Bitmap.createBitmap(pixels, w, h, Config.ARGB_8888)
    }

    private fun applyAdjustments(pixels: IntArray, w: Int, h: Int, v: BasicAdjustValues) {
        val brightnessOffset = v.brightness * 2.55f
        val contrastFactor = if (v.contrast != 0f) (100f + v.contrast) / 100f else 1f
        val contrastFactorInv = 1f / contrastFactor.coerceAtLeast(0.01f)
        val saturationFactor = 1f + v.saturation / 100f
        val shadowFactor = v.shadows / 100f
        val highlightFactor = v.highlights / 100f
        val tempShift = v.temperature * 0.8f
        val vignetteStrength = v.vignette / 100f

        val hasContrast = v.contrast != 0f
        val hasSaturation = v.saturation != 0f
        val hasShadows = v.shadows != 0f
        val hasHighlights = v.highlights != 0f
        val hasTemperature = v.temperature != 0f
        val hasVignette = v.vignette != 0f
        val hasBrightness = v.brightness != 0f

        val centerX = w / 2f
        val centerY = h / 2f
        val maxDist = Math.sqrt((centerX * centerX + centerY * centerY).toDouble()).toFloat()
        val invMaxDist = if (maxDist > 0f) 1f / maxDist else 0f

        for (i in pixels.indices) {
            var a = (pixels[i] ushr 24) and 0xFF
            var r = (pixels[i] shr 16) and 0xFF
            var g = (pixels[i] shr 8) and 0xFF
            var b = pixels[i] and 0xFF

            val lum = 0.299f * r + 0.587f * g + 0.114f * b

            if (hasShadows || hasHighlights) {
                if (lum < 128f && shadowFactor != 0f) {
                    val weight = ((128f - lum) / 128f) * shadowFactor
                    r = (r + r * weight).toInt().coerceIn(0, 255)
                    g = (g + g * weight).toInt().coerceIn(0, 255)
                    b = (b + b * weight).toInt().coerceIn(0, 255)
                }
                if (lum > 128f && highlightFactor != 0f) {
                    val weight = ((lum - 128f) / 128f) * highlightFactor
                    r = (r + r * weight).toInt().coerceIn(0, 255)
                    g = (g + g * weight).toInt().coerceIn(0, 255)
                    b = (b + b * weight).toInt().coerceIn(0, 255)
                }
            }

            if (hasBrightness) {
                r = (r + brightnessOffset).toInt().coerceIn(0, 255)
                g = (g + brightnessOffset).toInt().coerceIn(0, 255)
                b = (b + brightnessOffset).toInt().coerceIn(0, 255)
            }

            if (hasContrast) {
                r = ((r - 128f) * contrastFactor + 128f).toInt().coerceIn(0, 255)
                g = ((g - 128f) * contrastFactor + 128f).toInt().coerceIn(0, 255)
                b = ((b - 128f) * contrastFactor + 128f).toInt().coerceIn(0, 255)
            }

            if (hasSaturation) {
                val sr = (lum + saturationFactor * (r - lum)).toInt().coerceIn(0, 255)
                val sg = (lum + saturationFactor * (g - lum)).toInt().coerceIn(0, 255)
                val sb = (lum + saturationFactor * (b - lum)).toInt().coerceIn(0, 255)
                r = sr; g = sg; b = sb
            }

            if (hasTemperature) {
                r = (r + tempShift).toInt().coerceIn(0, 255)
                b = (b - tempShift).toInt().coerceIn(0, 255)
            }

            if (hasVignette) {
                val x = (i % w).toFloat()
                val y = (i / w).toFloat()
                val dx = (x - centerX)
                val dy = (y - centerY)
                val dist = Math.sqrt((dx * dx + dy * dy).toDouble()).toFloat() * invMaxDist
                val vignetteFade = (dist * dist * vignetteStrength).coerceIn(0f, 1f)
                if (vignetteFade > 0f) {
                    val scale = 1f - vignetteFade
                    r = (r * scale).toInt().coerceIn(0, 255)
                    g = (g * scale).toInt().coerceIn(0, 255)
                    b = (b * scale).toInt().coerceIn(0, 255)
                }
            }

            pixels[i] = (a shl 24) or (r shl 16) or (g shl 8) or b
        }
    }

    private suspend fun ensureModelLoaded(target: LoadedModel): String? {
        if (loadedModel == target && litertBridge.isLoaded) return null
        return mutex.withLock {
            if (loadedModel == target && litertBridge.isLoaded) return null
            try {
                when (target) {
                    LoadedModel.ESRGAN -> litertBridge.loadModel("esrgan/real_esrgan_x4plus.tflite")
                    LoadedModel.AOTGAN -> litertBridge.loadModel("migan/migan_fp16.tflite")
                    LoadedModel.NONE -> return null
                    LoadedModel.DEEPLABV3 -> litertBridge.loadModel("deeplabv3/deeplabv3_plus_mobilenet.tflite")
                }
                loadedModel = target
                null
            } catch (e: Exception) {
                loadedModel = LoadedModel.NONE
                e.message ?: "Unknown error"
            }
        }
    }

    private fun blendResult(original: Bitmap, inpainted: Bitmap, mask: Bitmap): Bitmap {
        val w = original.width
        val h = original.height
        val origPixels = IntArray(w * h)
        val inpaintedPixels = IntArray(w * h)
        val maskPixels = IntArray(w * h)
        original.getPixels(origPixels, 0, w, 0, 0, w, h)

        val maskScaled = if (mask.width != w || mask.height != h) {
            Bitmap.createScaledBitmap(mask, w, h, false)
        } else {
            mask
        }

        if (inpainted.width != w || inpainted.height != h) {
            val scaled = Bitmap.createScaledBitmap(inpainted, w, h, true)
            scaled.getPixels(inpaintedPixels, 0, w, 0, 0, w, h)
            scaled.recycle()
        } else {
            inpainted.getPixels(inpaintedPixels, 0, w, 0, 0, w, h)
        }

        maskScaled.getPixels(maskPixels, 0, w, 0, 0, w, h)
        if (maskScaled !== mask) maskScaled.recycle()

        val blended = IntArray(w * h)
        for (i in blended.indices) {
            val maskGray = ((maskPixels[i] shr 16) and 0xFF) * 0.299f +
                           ((maskPixels[i] shr 8) and 0xFF) * 0.587f +
                           (maskPixels[i] and 0xFF) * 0.114f
            if (maskGray > 30f) {
                blended[i] = inpaintedPixels[i]
            } else {
                blended[i] = origPixels[i]
            }
        }

        return Bitmap.createBitmap(blended, w, h, Config.ARGB_8888)
    }

    private fun decodeBitmap(uri: Uri): Bitmap? {
        val decoded = try {
            when (uri.scheme) {
                "file" -> BitmapFactory.decodeFile(uri.path)
                else -> context.contentResolver.openInputStream(uri)?.use { input ->
                    BitmapFactory.decodeStream(input)
                }
            }
        } catch (_: Exception) { null } ?: return null

        if (decoded.config == Config.ARGB_8888) return decoded
        val converted = decoded.copy(Config.ARGB_8888, false)
        decoded.recycle()
        return converted
    }
}
