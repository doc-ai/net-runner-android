package ai.doc.netrunner.view

import ai.doc.netrunner.MainViewModel
import ai.doc.netrunner.ModelRunnerWatcher
import ai.doc.netrunner.R
import ai.doc.netrunner.outputhandler.OutputHandler
import ai.doc.netrunner.outputhandler.OutputHandlerManager
import ai.doc.tensorio.TIOModel.TIOModel
import android.content.Context
import android.content.SharedPreferences
import android.hardware.camera2.CameraCharacteristics

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.*
import android.widget.TextView
import androidx.core.content.edit

import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.google.android.material.floatingactionbutton.FloatingActionButton

/**
 * A simple [Fragment] subclass.
 */

class LiveCameraTabFragment : LiveCameraFragment(), ModelRunnerWatcher /*, View.OnTouchListener */ {

    // UI

    private lateinit var textureView: TextureView
    private lateinit var latencyTextView: TextView
    // private lateinit var gestureDetector: GestureDetectorCompat

    // View Model

    private val viewModel by activityViewModels<MainViewModel>()

    private val prefs: SharedPreferences? by lazy {
        activity?.getSharedPreferences("Settings", Context.MODE_PRIVATE)
    }

    // Creation

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_live_camera_tab, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (savedInstanceState == null) {
            loadFragmentForModel(viewModel.modelRunner.model)
        }

        textureView = view.findViewById(R.id.texture)
        latencyTextView = view.findViewById(R.id.latency)

        // Camera Settings

        cameraFacing = prefs?.getInt(getString(R.string.prefs_camera_facing), CameraCharacteristics.LENS_FACING_BACK)
                ?: CameraCharacteristics.LENS_FACING_BACK

        // Buttons for Camera Control

        view.findViewById<FloatingActionButton>(R.id.toggle_facing_button).setOnClickListener {
            toggleCameraFacing()
        }

        view.findViewById<FloatingActionButton>(R.id.toggle_pause_button).setOnClickListener {
            toggleCameraPaused()
            val resId = if (isCameraPaused) android.R.drawable.ic_media_play else android.R.drawable.ic_media_pause
            (it as FloatingActionButton).setImageResource(resId)
        }

        // Update Pause|Play Button

        val resId = if (isCameraPaused) android.R.drawable.ic_media_play else android.R.drawable.ic_media_pause
        val pauseButton = view.findViewById<FloatingActionButton>(R.id.toggle_pause_button)
        (pauseButton as FloatingActionButton).setImageResource(resId)
    }

    // Camera Control

    private fun toggleCameraPaused() {
        if (isCameraPaused) {
            child<OutputHandler>(R.id.outputContainer)?.output = null
            startClassification()
            resumeCamera()
        } else {
            stopClassification()
            pauseCamera()
        }
        isCameraPaused = !isCameraPaused
    }

    private fun toggleCameraFacing() {
        stopClassification()

        child<OutputHandler>(R.id.outputContainer)?.output = null

        flipCamera()
        prefs?.edit(true) { putInt(getString(R.string.prefs_camera_facing), cameraFacing) }

        startClassification()
    }

    private fun loadFragmentForModel(model: TIOModel) {
        val outputHandler = OutputHandlerManager.handlerForType(model.type).newInstance() as Fragment
        childFragmentManager.beginTransaction().replace(R.id.outputContainer, outputHandler).commit()
    }

    //region Model Runner Watcher

    /** Replaces the output handler but waits for the model runner to finish **/

    override fun modelDidChange() {
        viewModel.modelRunner.wait {
            Handler(Looper.getMainLooper()).post(Runnable {
                loadFragmentForModel(viewModel.modelRunner.model)
            })
        }
    }

    override fun stopRunning() {
        stopClassification()
    }

    override fun startRunning() {
        startClassification()
    }

    //endregion

    //region Lifecycle

    override fun onResume() {
        super.onResume()

        if (!isCameraPaused) {
            startClassification()
        }
    }

    override fun onPause() {
        super.closeCamera()
        stopClassification()
        super.onPause()
    }

    //endregion

    private fun startClassification() {
        if (isDetached || !isAdded) {
            return
        }

        viewModel.modelRunner.startStreamingInference( {
            textureView.bitmap
        }, { output: Map<String,Any>, l: Long ->
            Handler(Looper.getMainLooper()).post(Runnable {
                child<OutputHandler>(R.id.outputContainer)?.output = output
                latencyTextView.text = "$l ms"
            })
        })
    }

    fun stopClassification() {
        viewModel.modelRunner.stopStreamingInference()
    }

    private fun <T>child(id: Int): T? {
        if (isDetached || !isAdded) {
            return null
        }

        @Suppress("UNCHECKED_CAST")
        return childFragmentManager.findFragmentById(id) as? T
    }

}