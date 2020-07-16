package ai.doc.netrunner.view

import ai.doc.netrunner.MainViewModel
import ai.doc.netrunner.ModelRunnerWatcher
import ai.doc.netrunner.R
import ai.doc.netrunner.outputhandler.OutputHandler
import ai.doc.netrunner.outputhandler.OutputHandlerManager
import ai.doc.tensorio.TIOModel.TIOModel

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.*
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.GestureDetectorCompat

import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.google.android.material.floatingactionbutton.FloatingActionButton
import java.lang.ref.WeakReference

/**
 * A simple [Fragment] subclass.
 */

class LiveCameraClassificationFragment : LiveCameraFragment(), ModelRunnerWatcher /*, View.OnTouchListener */ {

    /** Captures gestures on behalf of the fragment and forwards them back to the fragment */

//    private class GestureListener(): GestureDetector.SimpleOnGestureListener() {
//
//        private var weakHandler: WeakReference<LiveCameraClassificationFragment>? = null
//
//        var handler: LiveCameraClassificationFragment?
//            get() = weakHandler?.get()
//            set(value) {
//                if (value == null) {
//                    weakHandler?.clear()
//                } else {
//                    weakHandler = WeakReference(value)
//                }
//            }
//
//
//        override fun onDown(event: MotionEvent): Boolean {
//            return true
//        }
//
//        override fun onSingleTapConfirmed(event: MotionEvent): Boolean {
//            return handler?.onSingleTapConfirmed(event) ?: super.onSingleTapConfirmed(event)
//        }
//
//        override fun onFling(e1: MotionEvent?, e2: MotionEvent?, velocityX: Float, velocityY: Float): Boolean {
//            return handler?.onFling(e1, e2, velocityX, velocityY) ?: super.onFling(e1, e2, velocityX, velocityY)
//        }
//
//    }

    // UI

    private lateinit var textureView: TextureView
    private lateinit var latencyTextView: TextView
    // private lateinit var gestureDetector: GestureDetectorCompat

    private var isPaused = false

    // View Model

    private val viewModel by activityViewModels<MainViewModel>()

    // Creation

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_live_camera_tab, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        loadFragmentForModel(viewModel.modelRunner.model)

        textureView = view.findViewById(R.id.texture)
        latencyTextView = view.findViewById(R.id.latency)

        // Gestures for Camera Control

        // val me = this
        // gestureDetector = GestureDetectorCompat(activity, GestureListener().apply { handler = me })
        // view.setOnTouchListener(this)

        // Button for Camera Control

        view.findViewById<FloatingActionButton>(R.id.toggle_facing_button).setOnClickListener {
            toggleCameraFacing()
        }

        view.findViewById<FloatingActionButton>(R.id.toggle_pause_button).setOnClickListener {
            toggleCameraPaused()
        }
    }

    // Gestures for Camera Control

//    override fun onTouch(v: View?, event: MotionEvent?): Boolean {
//        return gestureDetector.onTouchEvent(event)
//    }
//
//    fun onSingleTapConfirmed(event: MotionEvent): Boolean {
//        toggleCameraPaused()
//        return true
//    }
//
//    fun onFling(e1: MotionEvent?, e2: MotionEvent?, velocityX: Float, velocityY: Float): Boolean {
//        toggleCameraFacing()
//        return true
//    }

    // Camera Control

    private fun toggleCameraPaused() {
        if (isPaused) {
            child<OutputHandler>(R.id.outputContainer)?.output = null
            startClassification()
            resumeCamera()
        } else {
            Toast.makeText(activity, R.string.camera_paused, Toast.LENGTH_SHORT).apply { setGravity(Gravity.CENTER,0,0) }.show()
            stopClassification()
            pauseCamera()
        }
        isPaused = !isPaused
    }

    private fun toggleCameraFacing() {
        stopClassification()
        child<OutputHandler>(R.id.outputContainer)?.output = null
        flipCamera()
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
        startClassification()
    }

    override fun onPause() {
        super.closeCamera()
        stopClassification()
        super.onPause()
    }

    //endregion

    private fun startClassification() {
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
        @Suppress("UNCHECKED_CAST")
        return childFragmentManager.findFragmentById(id) as? T
    }

}