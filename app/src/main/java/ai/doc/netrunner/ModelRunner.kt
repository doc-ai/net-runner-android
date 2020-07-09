package ai.doc.netrunner

import ai.doc.tensorio.TIOLayerInterface.TIOPixelBufferLayerDescription
import ai.doc.tensorio.TIOLayerInterface.TIOVectorLayerDescription
import ai.doc.tensorio.TIOModel.TIOModelException
import ai.doc.tensorio.TIOTFLiteModel.GpuDelegateHelper
import ai.doc.tensorio.TIOTFLiteModel.TIOTFLiteModel
import android.graphics.Bitmap
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.text.SpannableStringBuilder

class ModelRunner(private var model: TIOTFLiteModel) {
    enum class Device {
        CPU, GPU, NNAPI
    }

    interface ModelRunnerDataSource {
        fun getNextInput(size_x: Int, size_y: Int): Bitmap?
    }

    interface ClassificationResultListener {
        fun classificationResult(requestId: Int, prediction: Any?, latency: Long)
    }

    inner class UnsupportedConfigurationException(message: String?) : RuntimeException(message)

    companion object {
        private const val TAG = "ModelRunner"
        private const val HANDLE_THREAD_NAME = "ClassificationThread"
    }

    var labels: Array<String>
        private set
    var inputWidth: Int
        private set
    var inputHeight: Int
        private set

    var dataSource: ModelRunnerDataSource? = null
    // var listener: ClassificationResultListener? = null
    lateinit var listener: ((Int, Any, Long) -> Unit)

    private var numThreads = 1
    private var use16Bit = false
    private var device: Device? = null

    // So the background handler is a kind of queue that will receive requests to call functions (post)

    private val backgroundHandler: Handler
    private val lock = Any()
    private var running = false

    init {
        labels = (model.io.outputs[0].layerDescription as TIOVectorLayerDescription).labels
        inputWidth = (model.io.inputs[0].layerDescription as TIOPixelBufferLayerDescription).shape.width
        inputHeight = (model.io.inputs[0].layerDescription as TIOPixelBufferLayerDescription).shape.height
        val backgroundThread = HandlerThread(HANDLE_THREAD_NAME)
        backgroundThread.start()
        backgroundHandler = Handler(backgroundThread.looper)
        backgroundHandler.post {
            try {
                model.load()
            } catch (e: TIOModelException) {
                e.printStackTrace()
            }
        }
    }

    private val periodicClassify = Runnable {
        synchronized(lock) {
            if (running) {
                runInference()
            }
        }
    }

    private fun runInference() {
        val bitmap = dataSource!!.getNextInput(inputWidth, inputHeight)
        if (bitmap != null) {
            try {
                // run inference
                val startTime = SystemClock.uptimeMillis()
                val result: Any = model.runOn(bitmap)
                val endTime = SystemClock.uptimeMillis()
                listener(-1, result, endTime - startTime)
            } catch (e: TIOModelException) {
                e.printStackTrace()
            }
            bitmap.recycle()
        }
        backgroundHandler.post(periodicClassify)
    }

    // TODO: Classify frame assumes we are running a classification model
    // This whole class assumes we are running a classification model, generalize it

    // classifyFrame is called from the SingleImage Fragment whereas the startStreamClassification is called from the LiveCamera Fragmenet
    // oof

    fun classifyFrame(requestId: Int, frame: Bitmap?, listener: (Int, Any, Long) -> Unit) {
        backgroundHandler.post {
            val predictionsBuilder = SpannableStringBuilder()
            val latencyBuilder = SpannableStringBuilder()
            try {
                val startTime = SystemClock.uptimeMillis()
                val output = model.runOn(frame)
                val endTime = SystemClock.uptimeMillis()
                listener(requestId, output, endTime - startTime)
            } catch (e: TIOModelException) {
                e.printStackTrace()
            }
        }
    }

    // TODO: why periodically classify from the background handler...
    // Ah, listener should be the confirming instance rather than a lambda. can we store lambdas rather than conforming instances?

    fun startStreamClassification(dataSource: ModelRunnerDataSource?, listener: (Int, Any, Long) -> Unit) {
        synchronized(lock) {
            this@ModelRunner.dataSource = dataSource
            this@ModelRunner.listener = listener
            running = true
            backgroundHandler.post(periodicClassify)
        }
    }

    fun stopStreamClassification() {
        synchronized(lock) {
            running = false
            dataSource = null
        }
    }

    @Throws(TIOModelException::class)
    fun switchModel(newModel: TIOTFLiteModel) {
        switchModel(newModel, device == Device.GPU, device == Device.NNAPI, numThreads, use16Bit)
    }

    fun switchModel(model: TIOTFLiteModel, useGPU: Boolean, useNNAPI: Boolean, numThreads: Int, use16Bit: Boolean) {
        backgroundHandler.post {
            synchronized(lock) {
                this.model.unload()
                this.model = model
                try {
                    this.model.load()
                } catch (e: TIOModelException) {
                    e.printStackTrace()
                }
                labels = (this.model.io.outputs[0].layerDescription as TIOVectorLayerDescription).labels
                inputWidth = (this.model.io.inputs[0].layerDescription as TIOPixelBufferLayerDescription).shape.width
                inputHeight = (this.model.io.inputs[0].layerDescription as TIOPixelBufferLayerDescription).shape.height
                this@ModelRunner.use16Bit = use16Bit
                device = Device.CPU
                if (useGPU && GpuDelegateHelper.isGpuDelegateAvailable()) {
                    device = Device.GPU
                } else if (useNNAPI) {
                    device = Device.NNAPI
                }
                this@ModelRunner.numThreads = numThreads
                this.model.setOptions(use16Bit, useGPU, useNNAPI, numThreads)
            }
        }
    }

    fun useGPU() {
        if (device != Device.GPU) {
            backgroundHandler.post {
                if (!GpuDelegateHelper.isGpuDelegateAvailable()) {
                    device = Device.CPU
                    throw UnsupportedConfigurationException("GPU not supported in this build")
                } else {
                    model.useGPU()
                    device = Device.GPU
                }
            }
        }
    }

    fun useCPU() {
        if (device != Device.CPU) {
            backgroundHandler.post {
                model.useCPU()
                device = Device.CPU
            }
        }
    }

    fun useNNAPI() {
        if (device != Device.NNAPI) {
            backgroundHandler.post {
                model.useNNAPI()
                device = Device.NNAPI
            }
        }
    }

    fun setNumThreads(numThreads: Int) {
        if (this.numThreads != numThreads) {
            backgroundHandler.post {
                model.setNumThreads(numThreads)
                this.numThreads = numThreads
            }
        }
    }

    fun setUse16bit(use16Bit: Boolean) {
        if (this.use16Bit != use16Bit) {
            backgroundHandler.post {
                model.setAllow16BitPrecision(use16Bit)
                this.use16Bit = use16Bit
            }
        }
    }
}