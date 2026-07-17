package id.my.daniza.litert

import android.content.Context
import android.graphics.Bitmap
import com.google.ai.edge.litert.Accelerator
import com.google.ai.edge.litert.CompiledModel
import dagger.hilt.android.qualifiers.ApplicationContext
import id.my.daniza.litert.data.BitmapOps
import id.my.daniza.litert.data.ModelConfig
import id.my.daniza.litert.data.TensorDataType
import id.my.daniza.litert.handler.Deeplabv3Handler
import id.my.daniza.litert.handler.EsrganHandler
import id.my.daniza.litert.handler.MiganHandler
import kotlinx.coroutines.coroutineScope
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LitertBridge @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    @Volatile
    private var model: CompiledModel? = null
        private set

    var parallelism: Int = 4
        private set

    val isLoaded get() = model != null

    fun loadModel(assetPath: String) {
        close()
        model = CompiledModel.create(context.assets, assetPath, CompiledModel.Options(Accelerator.CPU))
        parallelism = Runtime.getRuntime().availableProcessors().coerceIn(2, 4)
        Timber.i("loadModel: loaded %s (parallelism=%d)", assetPath, parallelism)
    }

    fun loadModelFromFile(file: File) {
        close()
        model = CompiledModel.create(file.absolutePath, CompiledModel.Options(Accelerator.CPU))
        parallelism = Runtime.getRuntime().availableProcessors().coerceIn(2, 4)
        Timber.i("loadModelFromFile: loaded %s (parallelism=%d)", file.absolutePath, parallelism)
    }

    fun close() {
        Timber.i("close")
        model?.close()
        model = null
    }

    suspend fun runSuperRes(
        bitmap: Bitmap,
        scaleTarget: Int = BitmapOps.MODE_FAST,
        progress: ((Int, Int) -> Unit)? = null,
    ): Bitmap? = coroutineScope {
        val session = createSession(ModelConfig.ESRGAN_DEFAULT)
        EsrganHandler.run(session, bitmap, scaleTarget, progress)
    }

    suspend fun runInpainting(
        imageBitmap: Bitmap,
        maskBitmap: Bitmap,
        progress: ((Int, Int) -> Unit)? = null,
    ): Bitmap? = coroutineScope {
        val session = createSession(ModelConfig.AOTGAN_DEFAULT)
        MiganHandler.run(session, imageBitmap, maskBitmap, progress)
    }

    suspend fun runSegmentation(bitmap: Bitmap): IntArray? {
        val session = createSession(ModelConfig.DEEPLABV3_DEFAULT)
        return Deeplabv3Handler.run(session, bitmap)
    }

    fun createSession(config: ModelConfig): ModelSession {
        val m = model ?: throw IllegalStateException("Model not loaded")
        Timber.i(
            "createSession: in=%dx%d out=%dx%d ch=%d",
            config.inputWidth, config.inputHeight,
            config.outputWidth, config.outputHeight, config.outputChannels,
        )
        return ModelSession(model = m, config = config, parallelism = parallelism)
    }

    data class ModelSession(
        val model: CompiledModel,
        val config: ModelConfig,
        val parallelism: Int,
    ) {
        fun close() { model.close() }
    }
}
