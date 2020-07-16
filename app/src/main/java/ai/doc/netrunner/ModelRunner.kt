package ai.doc.netrunner

import ai.doc.tensorio.TIOTFLiteModel.GpuDelegateHelper
import ai.doc.tensorio.TIOTFLiteModel.TIOTFLiteModel
import ai.doc.tensorio.TIOTFLiteModel.TIOTFLiteModel.HardwareBacking.*
import android.graphics.Bitmap
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock

private const val TAG = "ModelRunner"
private const val HANDLE_THREAD_NAME = "ai.doc.netrunner.model-runner"

/** A listener is called when a step of inference has completed **/

private typealias Listener = (Map<String,Any>, Long) -> Unit

/** A bitmap provider provides a bitmap to the model runner for a single step of inference **/

private typealias BitmapProvider = () -> Bitmap?

/** The model runner callback is called after any configuration change and is needed changes happen on background threads **/

private typealias ModelRunnerCallback = () -> Unit

interface ModelRunnerWatcher {
    fun modelDidChange()
    fun stopRunning()
    fun startRunning()
}

/**
 * The ModelRunner is used to configure a model and to perform continuous or one-off inference
 * with a model.
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

    /** The bitmap provider provides a bitmap to one step of inference */

    var bitmapProvider: BitmapProvider? = null

    /** The listener is informed when one step of inference is completed */

    var listener: Listener? = null

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

    fun setNumThreads(value: Int, callback: ModelRunnerCallback?) {
        backgroundHandler.post { numThreads = value }
        backgroundHandler.post(callback)
    }

    fun setUse16Bit(value: Boolean, callback: ModelRunnerCallback?) {
        backgroundHandler.post { use16Bit = value }
        backgroundHandler.post(callback)
    }

    fun setDevice(value: Device, callback: ModelRunnerCallback?) {
        backgroundHandler.post { device = value }
        backgroundHandler.post(callback)
    }

    fun switchModel(model: TIOTFLiteModel, callback: ModelRunnerCallback?) {
        val previousModel = this.model

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
                callback?.invoke()
            } catch (e: Exception) {
                // Back up to previous model
                doSwitch(previousModel)
                throw ModelLoadingException()
            }
        }
    }

    //beginRegion Background Tasks

    /** Background thread that processes requests on the background handler */

    private lateinit var backgroundThread: HandlerThread

    /** Background queue that receives request to interact with the model */

    private lateinit var backgroundHandler: Handler

    /** Background thread exception handler */

    var uncaughtExceptionHandler: Thread.UncaughtExceptionHandler = uncaughtExceptionHandler
        private set

    /** A continuous runnable that will repeatedly execute inference on the model until running is set to false */

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

    //endRegion

    init {
        setupBackgroundHandler()
        backgroundHandler.post {
            model.load()
        }
    }

    private fun setupBackgroundHandler() {
        val thizz = this
        backgroundThread = HandlerThread(HANDLE_THREAD_NAME).apply {
            this.uncaughtExceptionHandler = thizz.uncaughtExceptionHandler
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
        }
    }

    /** Start continuous inference */

    fun startStreamingInference(bitmapProvider: BitmapProvider, listener: Listener) {
        backgroundHandler.post {
            this.bitmapProvider = bitmapProvider
            this.listener = listener
            running = true
        }
        backgroundHandler.post(periodicRunner)
    }

    /** Stop continuous inference */

    fun stopStreamingInference() {
        backgroundHandler.post {
            this.bitmapProvider = null
            this.listener = null
            running = false
        }
    }

    /** Waits for the background handler to finish processing before calling lambda */

    fun wait(lambda: ()->Unit) {
        backgroundHandler.post {
            lambda()
        }
    }
}