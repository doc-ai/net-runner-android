package ai.doc.netrunner.view

import ai.doc.netrunner.MainViewModel
import ai.doc.netrunner.R
import ai.doc.netrunner.outputhandler.MobileNetClassificationOutputHandler
import ai.doc.netrunner.outputhandler.OutputHandler

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

class SingleImageClassificationFragment : Fragment() {

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

        // TODO: Assumes classification model (#24)
        val outputHandler = MobileNetClassificationOutputHandler()
        childFragmentManager.beginTransaction().replace(R.id.outputContainer, outputHandler).commit()

        imageView = view.findViewById(R.id.imageview)
        latencyTextView = view.findViewById(R.id.latency)

        // Use any provided bitmap

        viewModel.bitmap?.let {
            doBitmap(it)
        }
    }

    private fun doBitmap(bitmap: Bitmap) {
        imageView.setImageBitmap(bitmap)

        viewModel.modelRunner.runInferenceOnFrame( {
            bitmap
        }, { output: Map<String,Any>, l: Long ->
            Handler(Looper.getMainLooper()).post(Runnable {
                latencyTextView.text = "$l ms"
                (childFragmentManager.findFragmentById(R.id.outputContainer) as? OutputHandler)?.let { handler ->
                    handler.output = output
                }
            })
        })
    }

}