package ai.doc.netrunner.fragments

import ai.doc.netrunner.viewmodels.MainViewModel
import ai.doc.netrunner.utilities.ModelRunnerWatcher
import ai.doc.netrunner.R
import ai.doc.netrunner.outputhandler.OutputHandler
import ai.doc.netrunner.outputhandler.OutputHandlerManager
import ai.doc.netrunner.utilities.HandlerUtilities
import ai.doc.tensorio.TIOModel.TIOModel
import android.content.Context
import android.content.SharedPreferences
import android.hardware.camera2.CameraCharacteristics

import android.os.Bundle
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
    private lateinit var pauseButton: FloatingActionButton
    private lateinit var flipButton: FloatingActionButton

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
        pauseButton = view.findViewById(R.id.toggle_pause_button)
        flipButton = view.findViewById(R.id.toggle_facing_button)

        // Camera Settings

        cameraFacing = prefs?.getInt(getString(R.string.prefs_camera_facing), CameraCharacteristics.LENS_FACING_BACK)
                ?: CameraCharacteristics.LENS_FACING_BACK

        // Buttons for Camera Control

        flipButton.setOnClickListener {
            toggleCameraFacing()
        }

        pauseButton.setOnClickListener {
            toggleCameraPaused()
            val resId = if (isCameraPaused) android.R.drawable.ic_media_play else android.R.drawable.ic_media_pause
            pauseButton.setImageResource(resId)
        }

        // Update Pause|Play Button

        val resId = if (isCameraPaused) android.R.drawable.ic_media_play else android.R.drawable.ic_media_pause
        pauseButton.setImageResource(resId)
    }

    // Camera Control

    private fun toggleCameraPaused() {
        if (isCameraPaused) {
            child<OutputHandler>(R.id.outputContainer)?.output = null
            resumeCamera()
            startContinuousInference()
        } else {
            stopContinuousInference()
            pauseCamera()
        }
    }

    private fun toggleCameraFacing() {
        stopContinuousInference()
        pauseCamera()

        child<OutputHandler>(R.id.outputContainer)?.output = null

        flipCamera()
        prefs?.edit(true) { putInt(getString(R.string.prefs_camera_facing), cameraFacing) }

        pauseButton.setImageResource(android.R.drawable.ic_media_pause)

        resumeCamera()
        startContinuousInference()
    }

    private fun loadFragmentForModel(model: TIOModel) {
        val outputHandler = OutputHandlerManager.handlerForType(model.type).newInstance() as Fragment
        childFragmentManager.beginTransaction().replace(R.id.outputContainer, outputHandler).commit()
    }

    //region Model Runner Watcher

    /** Replaces the output handler but waits for the model runner to finish **/

    override fun modelDidChange() {
        viewModel.modelRunner.waitOnRunner()
        loadFragmentForModel(viewModel.modelRunner.model)
    }

    override fun stopRunning() {
        stopContinuousInference()
    }

    override fun startRunning() {
        if (isCameraPaused) {
            runSingleFrameOfInference()
        } else {
            startContinuousInference()
        }
    }

    //endregion

    //region Lifecycle

    override fun onResume() {
        super.onResume()

        if (!isCameraPaused) {
            startContinuousInference()
        }
    }

    override fun onPause() {
        super.closeCamera()
        stopContinuousInference()
        super.onPause()
    }

    //endregion

    /** Instructs the model runner to run continuous inference with the model */

    private fun startContinuousInference() {
        if (isDetached || !isAdded) {
            return
        }

        viewModel.modelRunner.startStreamingInference( {
            textureView.bitmap
        }, { output: Map<String,Any>, l: Long ->
            HandlerUtilities.main(Runnable {
                child<OutputHandler>(R.id.outputContainer)?.output = output
                latencyTextView.text = "$l ms"
            })
        })
    }

    /** Halts continuous inference with the model */

    private fun stopContinuousInference() {
        viewModel.modelRunner.stopStreamingInference()
    }

    /** Runs a single frame of inference, used for example when the camera is paused but the model changes */

    private fun runSingleFrameOfInference() {
        if (isDetached || !isAdded) {
            return
        }

        viewModel.modelRunner.runInferenceOnFrame( {
            textureView.bitmap
        }, { output: Map<String,Any>, l: Long ->
            HandlerUtilities.main(Runnable {
                child<OutputHandler>(R.id.outputContainer)?.output = null
                child<OutputHandler>(R.id.outputContainer)?.output = output
                latencyTextView.text = "$l ms"
            })
        })
    }

    /** Returns a child fragment safely cast to type */

    private fun <T>child(id: Int): T? {
        if (isDetached || !isAdded) {
            return null
        }

        @Suppress("UNCHECKED_CAST")
        return childFragmentManager.findFragmentById(id) as? T
    }

}