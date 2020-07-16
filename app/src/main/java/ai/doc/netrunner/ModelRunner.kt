package ai.doc.netrunner

import ai.doc.tensorio.TIOModel.TIOModelException
import ai.doc.tensorio.TIOTFLiteModel.GpuDelegateHelper
import ai.doc.tensorio.TIOTFLiteModel.TIOTFLiteModel
import ai.doc.tensorio.TIOTFLiteModel.TIOTFLiteModel.HardwareBacking.*

import android.graphics.Bitmap
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock

private const val TAG = "ModelRunner"
private const val HANDLE_THREAD_NAME = "ai.doc.netrunner.model-runner"

private typealias Listener = (Map<String,Any>, Long) -> Unit
private typealias BitmapProvider = () -> Bitmap?

interface ModelRunnerWatcher {
    fun modelDidChange()
}

/**
 * The ModelRunner is used to configure a model and to perform continuous or one-off inference
 * with a model.
 */

class ModelRunner(model: TIOTFLiteModel) {
    inner class UnsupportedConfigurationException(message: String?) : RuntimeException(message)

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

    lateinit var bitmapProvider: BitmapProvider

    /** The listener is informed when one step of inference is completed */

    lateinit var listener: Listener

    var numThreads = 1
        set(value) {
            backgroundHandler.post {
                model.numThreads = value
                model.reload()
                field = value
            }
        }

    var use16Bit = false
        set(value) {
            backgroundHandler.post {
                model.setUse16BitPrecision(value)
                model.reload()
                field = value
            }
        }

    var device: Device = Device.CPU
        set(value) {
            backgroundHandler.post {
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
                        throw UnsupportedConfigurationException("GPU not supported in this build")
                    } else {
                        model.hardwareBacking = GPU
                        field = Device.GPU
                    }
                }
                model.reload()
            }
        }

    @Throws(TIOModelException::class)
    fun switchModel(model: TIOTFLiteModel) {
        backgroundHandler.post {
            this.model.unload()
            this.model = model

            model.numThreads = numThreads
            model.setUse16BitPrecision(use16Bit)

            when(device) {
                Device.CPU -> model.hardwareBacking = CPU
                Device.GPU -> model.hardwareBacking = GPU
                Device.NNAPI -> model.hardwareBacking = NNAPI
            }

            model.load()
        }
    }

    //beginRegion Background Tasks

    /** Background thread that processes requests on the background handler */

    private val backgroundThread: HandlerThread by lazy {
        HandlerThread(HANDLE_THREAD_NAME).apply {
            start()
        }
    }

    /** Background queue that receives request to interact with the model */

    private val backgroundHandler: Handler by lazy {
        Handler(backgroundThread.looper)
    }

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
        backgroundHandler.post {
            try {
                model.load()
            } catch (e: TIOModelException) {
                e.printStackTrace()
            }
        }
    }

    /** Executes a single step of inference */

    private fun runInference() {
        val bitmap = bitmapProvider() ?: return

        try {
            val startTime = SystemClock.uptimeMillis()
            val result = model.runOn(bitmap)
            val endTime = SystemClock.uptimeMillis()
            listener(result, endTime - startTime)
        } catch (e: TIOModelException) {
            e.printStackTrace()
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