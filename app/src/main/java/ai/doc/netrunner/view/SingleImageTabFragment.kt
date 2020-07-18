package ai.doc.netrunner.view

import ai.doc.netrunner.MainViewModel
import ai.doc.netrunner.ModelRunnerWatcher
import ai.doc.netrunner.R
import ai.doc.netrunner.outputhandler.OutputHandler
import ai.doc.netrunner.outputhandler.OutputHandlerManager
import ai.doc.tensorio.TIOModel.TIOModel

import android.graphics.Bitmap
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView

import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels

class SingleImageTabFragment : Fragment(), ModelRunnerWatcher {

    // UI

    private lateinit var imageView: ImageView
    private lateinit var latencyTextView: TextView

    // View Model

    private val viewModel by activityViewModels<MainViewModel>()

    // Creation

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_single_image_tab, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (savedInstanceState == null) {
            loadFragmentForModel(viewModel.modelRunner.model)
        }

        imageView = view.findViewById(R.id.imageview)
        latencyTextView = view.findViewById(R.id.latency)

        // Use any provided bitmap

        viewModel.bitmap?.let {
            doBitmap(it)
        }
    }

    private fun loadFragmentForModel(model: TIOModel) {
        val outputHandler = OutputHandlerManager.handlerForType(model.type).newInstance() as Fragment
        childFragmentManager.beginTransaction().replace(R.id.outputContainer, outputHandler).commit()
    }

    /** Replaces the output handler but waits for the model runner to finish  */

    override fun modelDidChange() {
        viewModel.modelRunner.waitOnRunner()
        loadFragmentForModel(viewModel.modelRunner.model)

//        viewModel.modelRunner.wait {
//            Handler(Looper.getMainLooper()).post(Runnable {
//                loadFragmentForModel(viewModel.modelRunner.model)
//            })
//        }

        viewModel.bitmap?.let {
            doBitmap(it)
        }
    }

    override fun stopRunning() {

    }

    override fun startRunning() {

    }

    /** Executes inference on the provided bitmap */

    private fun doBitmap(bitmap: Bitmap) {
        if (isDetached || !isAdded) {
            return
        }

        imageView.setImageBitmap(bitmap)

        viewModel.modelRunner.runInferenceOnFrame( {
            bitmap
        }, { output: Map<String,Any>, l: Long ->
            Handler(Looper.getMainLooper()).post(Runnable {
                child<OutputHandler>(R.id.outputContainer)?.output = output
                latencyTextView.text = "$l ms"
            })
        })
    }

    private fun <T>child(id: Int): T? {
        if (isDetached || !isAdded) {
            return null
        }

        @Suppress("UNCHECKED_CAST")
        return childFragmentManager.findFragmentById(id) as? T
    }

}