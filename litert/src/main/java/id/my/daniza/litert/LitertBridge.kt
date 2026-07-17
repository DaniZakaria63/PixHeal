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

    private sealed interface ModelSource {
        data class Asset(val path: String) : ModelSource
        data class FilePath(val path: String) : ModelSource
    }

    @Volatile
    private var modelSource: ModelSource? = null

    var parallelism: Int = 4
        private set

    val isLoaded get() = model != null

    fun loadModel(assetPath: String) {
        close()
        model = CompiledModel.create(context.assets, assetPath, CompiledModel.Options(Accelerator.CPU))
        modelSource = ModelSource.Asset(assetPath)
        parallelism = Runtime.getRuntime().availableProcessors().coerceIn(2, 4)
        Timber.i("loadModel: loaded %s (parallelism=%d)", assetPath, parallelism)
    }

    fun loadModelFromFile(file: File) {
        close()
        model = CompiledModel.create(file.absolutePath, CompiledModel.Options(Accelerator.CPU))
        modelSource = ModelSource.FilePath(file.absolutePath)
        parallelism = Runtime.getRuntime().availableProcessors().coerceIn(2, 4)
        Timber.i("loadModelFromFile: loaded %s (parallelism=%d)", file.absolutePath, parallelism)
    }

    fun close() {
        Timber.i("close")
        model?.close()
        model = null
        modelSource = null
    }

    /**
     * Creates a fresh [CompiledModel] instance from the loaded source.
     *
     * LiteRT's [CompiledModel] is not safe for concurrent [CompiledModel.run] /
     * buffer creation across threads. Tiled handlers fan tiles out across several
     * worker threads, so each worker must own its own model instance to avoid
     * native SIGSEGV crashes in `nativeCreateOutputBuffers`.
     */
    private fun createWorkerModel(): CompiledModel {
        val src = modelSource ?: throw IllegalStateException("Model not loaded")
        return when (src) {
            is ModelSource.Asset ->
                CompiledModel.create(context.assets, src.path, CompiledModel.Options(Accelerator.CPU))
            is ModelSource.FilePath ->
                CompiledModel.create(src.path, CompiledModel.Options(Accelerator.CPU))
        }
    }

    /**
     * A session bound to a dedicated [CompiledModel] instance. The caller owns the
     * model and must call [close] when done. Safe to use from a single worker thread.
     */
    data class ModelSession(
        val model: CompiledModel,
        val config: ModelConfig,
        val parallelism: Int,
    ) {
        fun close() { model.close() }
    }

    /**
     * Creates a worker-local session holding its own [CompiledModel] instance.
     * Use this for tiled handlers that run tiles in parallel.
     */
    fun createWorkerSession(config: ModelConfig): ModelSession {
        val m = createWorkerModel()
        Timber.i(
            "createWorkerSession: in=%dx%d out=%dx%d ch=%d",
            config.inputWidth, config.inputHeight,
            config.outputWidth, config.outputHeight, config.outputChannels,
        )
        return ModelSession(model = m, config = config, parallelism = parallelism)
    }

    suspend fun runSuperRes(
        bitmap: Bitmap,
        scaleTarget: Int = BitmapOps.MODE_FAST,
        progress: ((Int, Int) -> Unit)? = null,
    ): Bitmap? = coroutineScope {
        EsrganHandler.run(this@LitertBridge, bitmap, scaleTarget, progress)
    }

    suspend fun runInpainting(
        imageBitmap: Bitmap,
        maskBitmap: Bitmap,
        progress: ((Int, Int) -> Unit)? = null,
    ): Bitmap? = coroutineScope {
        MiganHandler.run(this@LitertBridge, imageBitmap, maskBitmap, progress)
    }

    suspend fun runSegmentation(bitmap: Bitmap): IntArray? {
        val session = createWorkerSession(ModelConfig.DEEPLABV3_DEFAULT)
        try {
            return Deeplabv3Handler.run(session, bitmap)
        } finally {
            session.close()
        }
    }
}
