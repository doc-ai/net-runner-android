package ai.doc.netrunner

import ai.doc.tensorio.TIOTFLiteModel.GpuDelegateHelper
import ai.doc.tensorio.TIOTFLiteModel.NnApiDelegateHelper
import ai.doc.tensorio.TIOTFLiteModel.TIOTFLiteModel
import ai.doc.tensorio.TIOTFLiteModel.TIOTFLiteModel.HardwareBacking.*
import android.graphics.Bitmap
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import java.util.concurrent.SynchronousQueue

private const val TAG = "ModelRunner"
private const val HANDLE_THREAD_NAME = "ai.doc.netrunner.model-runner"

/** A listener is called when a step of inference has completed */

private typealias Listener = (Map<String,Any>, Long) -> Unit

/** A bitmap provider provides a bitmap to the model runner for a single step of inference */

private typealias BitmapProvider = () -> Bitmap?

/** Implemented by objects interested in changes to the model runner, but called by [MainActivity] */

interface ModelRunnerWatcher {
    fun modelDidChange()
    fun stopRunning()
    fun startRunning()
}

/**
 * The ModelRunner is used to configure a model and to perform continuous or one-off inference
 * with a model.
 *
 * The somewhat convoluted use of the blocking queue is necessary because the model must be created
 * on the same thread it is invoked on. In truth only models that use the GPU have this requirement
 * related to the GPU context, and we support GPU backing so.
 */

class ModelRunner(model: TIOTFLiteModel, uncaughtExceptionHandler: Thread.UncaughtExceptionHandler) {
    inner class GPUUnavailableException(): RuntimeException()
    inner class ModelLoadingException(): RuntimeException()
    inner class ModelInferenceException(): RuntimeException()

    enum class Device {
        CPU,
        GPU,
        NNAPI
    }

    companion object {
        fun deviceFromString(string: String): Device {
            return when (string) {
                "CPU" -> Device.CPU
                "GPU" -> Device.GPU
                "NNAPI" -> Device.NNAPI
                else -> Device.CPU
            }
        }
    }

    var model: TIOTFLiteModel = model
        private set

    val canRunOnGPU = GpuDelegateHelper.isGpuDelegateAvailable()
    val canRunOnNnApi = NnApiDelegateHelper.isNnApiDelegateAvailable()

    /** The bitmap provider provides a bitmap to one step of inference */

    var bitmapProvider: BitmapProvider? = null

    /** The listener is informed when one step of inference is completed */

    var listener: Listener? = null

    // Configuration

    private var numThreads = 1
        set(value) {
            model.numThreads = value
            model.reload()
            field = value
        }

    private var use16Bit = false
        set(value) {
            model.setUse16BitPrecision(value)
            model.reload()
            field = value
        }

    private var device: Device = Device.CPU
        set(value) {
            when (value) {
                Device.CPU -> {
                    model.hardwareBacking = CPU
                    field = value
                }
                Device.NNAPI -> {
                    model.hardwareBacking = NNAPI
                    field = value
                }
                Device.GPU -> if (!GpuDelegateHelper.isGpuDelegateAvailable()) {
                    model.hardwareBacking = CPU
                    field = Device.CPU
                    throw GPUUnavailableException()
                } else {
                    model.hardwareBacking = GPU
                    field = Device.GPU
                }
            }

            try {
                model.reload()
            } catch (e: Exception) {
                // Back up to CPU and reload
                model.hardwareBacking = CPU
                field = Device.CPU
                model.reload()
                throw ModelLoadingException()
            }
        }

    // Set Configuration with Callback

    var block = SynchronousQueue<Boolean>()
    // backgroundHandler.post(callback)

    fun setNumThreads_temp(value: Int) {
        backgroundHandler.post {
            numThreads = value
            block.put(true) // false if fails
        }

        val succeeded = block.take()
    }

    fun setUse16Bit_temp(value: Boolean) {
        backgroundHandler.post {
            use16Bit = value
            block.put(true) // false if fails
        }

        val succeeded = block.take()
    }

    fun setDevice_temp(value: Device) {
        backgroundHandler.post {
            device = value
            block.put(true) // false if fails
        }

        val succeeded = block.take()
    }

    /** Changes the model and uses current settings, falls back to previous model if fails */

    fun switchModel(model: TIOTFLiteModel) {
        // val previousModel = this.model

        fun doSwitch(model: TIOTFLiteModel) {
            this.model.unload()
            this.model = model

            model.numThreads = numThreads
            model.setUse16BitPrecision(use16Bit)

            when(device) {
                Device.CPU -> model.hardwareBacking = CPU
                Device.GPU -> model.hardwareBacking = GPU
                Device.NNAPI -> model.hardwareBacking = NNAPI
            }
        }

        backgroundHandler.post {
            doSwitch(model)

            try {
                model.load()
                block.put(true)
            } catch (e: Exception) {
                block.put(false)
            }

//            try {
//                model.load()
//                callback?.invoke()
//            } catch (e: Exception) {
//                // Back up to previous model
//                doSwitch(previousModel)
//                throw ModelLoadingException()
//            }
        }

        val succeeded = block.take()
    }

    //region Background Tasks

    /** Background thread that processes requests on the background handler */

    private lateinit var backgroundThread: HandlerThread

    /** Background queue that receives request to interact with the model */

    private lateinit var backgroundHandler: Handler

    /** Background thread exception handler */

    var uncaughtExceptionHandler: Thread.UncaughtExceptionHandler = uncaughtExceptionHandler
        private set

    /** A continuous runnable that will repeatedly execute inference on the background thread until running is set to false */

    private val periodicRunner = object: Runnable {
        override fun run() {
            if (running) {
                runInference()
                backgroundHandler.post(this)
            }
        }
    }

    /** Determines if the periodic runner will continue to call itself */

    private var running = false

    //endregion

    init {
        setupBackgroundHandler()
        backgroundHandler.post {
            model.load()
        }
    }

    private fun setupBackgroundHandler() {
        val my = this
        backgroundThread = HandlerThread(HANDLE_THREAD_NAME).apply {
            uncaughtExceptionHandler = my.uncaughtExceptionHandler
            start()
        }
        backgroundHandler = Handler(backgroundThread.looper)
    }

    /** Restart the model runner after its background thread dies on an uncaught exception */

    fun reset() {
        setupBackgroundHandler()
    }

    /** Executes a single step of inference */

    private fun runInference() {
        val bitmap = bitmapProvider?.invoke() ?: return

        try {
            val startTime = SystemClock.uptimeMillis()
            val result = model.runOn(bitmap)
            val endTime = SystemClock.uptimeMillis()
            listener?.invoke(result, endTime - startTime)
        } catch (e: Exception) {
            throw ModelInferenceException()
        }
    }

    /** Executes inference on a single frame (non-continuous) */

    fun runInferenceOnFrame(bitmapProvider: BitmapProvider, listener: Listener) {
        backgroundHandler.post {
            this.bitmapProvider = bitmapProvider
            this.listener = listener
            runInference()
            block.put(true)
        }

        val succeeded = block.take()
    }

    /** Start continuous inference */

    fun startStreamingInference(bitmapProvider: BitmapProvider, listener: Listener) {
        backgroundHandler.post {
            this.bitmapProvider = bitmapProvider
            this.listener = listener
            running = true
            block.put(true)
        }
        backgroundHandler.post(periodicRunner)

        val succeeded = block.take()
    }

    /** Stop continuous inference */

    fun stopStreamingInference() {
        backgroundHandler.post {
            this.bitmapProvider = null
            this.listener = null
            running = false
            block.put(true)
        }

        val succeeded = block.take()
    }

    /** Waits for the background handler to finish processing before calling lambda */

    fun waitOnRunner() {
        backgroundHandler.post {
            // lambda()
            block.put(true)
        }

        val succeeded = block.take()
    }
}