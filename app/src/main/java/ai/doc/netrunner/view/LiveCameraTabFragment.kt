package ai.doc.netrunner.view

import ai.doc.netrunner.MainViewModel
import ai.doc.netrunner.R
import ai.doc.netrunner.outputhandler.MobileNetClassificationOutputHandler
import ai.doc.netrunner.outputhandler.OutputHandler

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import android.widget.TextView

import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels

/**
 * A simple [Fragment] subclass.
 */

class LiveCameraClassificationFragment : LiveCameraFragment() {

    // UI

    private lateinit var textureView: TextureView
    private lateinit var latencyTextView: TextView

    // View Model

    private val viewModel by activityViewModels<MainViewModel>()

    // Creation

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_live_camera_tab, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // TODO: Assumes classification model (#24)
        val outputHandler = MobileNetClassificationOutputHandler()
        childFragmentManager.beginTransaction().replace(R.id.outputContainer, outputHandler).commit()

        textureView = view.findViewById(R.id.texture)
        latencyTextView = view.findViewById(R.id.latency)
    }

    //beginRegion Lifecycle

    override fun onResume() {
        super.onResume()
        startClassification()
    }

    override fun onPause() {
        super.closeCamera()
        stopClassification()
        super.onPause()
    }

    //endRegion

    private fun startClassification() {
        viewModel.modelRunner.startStreamingInference( {
            textureView.bitmap
        }, { output: Map<String,Any>, l: Long ->
            Handler(Looper.getMainLooper()).post(Runnable {
                latencyTextView.text = "$l ms"
                (childFragmentManager.findFragmentById(R.id.outputContainer) as? OutputHandler)?.let { handler ->
                    handler.output = output
                }
            })
        })
    }

    fun stopClassification() {
        viewModel.modelRunner.stopStreamingInference()
    }

}