package ai.doc.netrunner

import ai.doc.tensorio.TIOModel.TIOModelException
import ai.doc.tensorio.TIOTFLiteModel.GpuDelegateHelper
import ai.doc.tensorio.TIOTFLiteModel.TIOTFLiteModel
import android.graphics.Bitmap
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock

private const val TAG = "ModelRunner"
private const val HANDLE_THREAD_NAME = "ai.doc.netrunner.model-runner"

private typealias Listener = (Map<String,Any>, Long) -> Unit
private typealias BitmapProvider = () -> Bitmap?

/**
 * The ModelRunner is used to configure a model and to perform continuous or one-off inference
 * with a model.
 */

class ModelRunner(private var model: TIOTFLiteModel) {
    inner class UnsupportedConfigurationException(message: String?) : RuntimeException(message)

    // TODO: Move Device to TIOTFliteModel

    enum class Device {
        CPU,
        GPU,
        NNAPI
    }

    /** The bitmap provider provides a bitmap to one step of inference */

    lateinit var bitmapProvider: BitmapProvider

    /** The listener is informed when one step of inference is completed */

    lateinit var listener: Listener

    var numThreads = 1
        set(value) {
            backgroundHandler.post {
                model.setNumThreads(value)
                field = value
            }
        }

    var use16Bit = false
        set(value) {
            backgroundHandler.post {
                model.setAllow16BitPrecision(value)
                field = value
            }
        }

    var device: Device = Device.CPU
        set(value) {
            backgroundHandler.post {
                when (value) {
                    Device.CPU -> {
                        model.useCPU()
                        field = value
                    }
                    Device.NNAPI -> {
                        model.useNNAPI()
                        field = value
                    }
                    Device.GPU -> if (!GpuDelegateHelper.isGpuDelegateAvailable()) {
                        throw UnsupportedConfigurationException("GPU not supported in this build")
                    } else {
                        model.useGPU()
                        field = Device.CPU
                    }
                }
            }
        }

    // TODO: Switching model currently recreates interpreter multiple times, see TIOTFLiteModel

    @Throws(TIOModelException::class)
    fun switchModel(model: TIOTFLiteModel) {
        backgroundHandler.post {
            this.model.unload()
            this.model = model

            model.load()

            model.setNumThreads(numThreads)
            model.setAllow16BitPrecision(use16Bit)

            when(device) {
                Device.CPU -> model.useCPU()
                Device.GPU -> model.useGPU()
                Device.NNAPI -> model.useNNAPI()
            }
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

    /** A continuous runnable that will repeatedly call itself until running is set to false */

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
}