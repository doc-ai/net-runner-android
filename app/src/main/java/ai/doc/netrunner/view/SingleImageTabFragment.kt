package ai.doc.netrunner.view

import ai.doc.netrunner.MainViewModel
import ai.doc.netrunner.R

import ai.doc.tensorio.TIOUtilities.TIOClassificationHelper

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
import androidx.lifecycle.ViewModelProviders

private const val RESULTS_TO_SHOW = 3

// TODO: Assumes classification model (#24)

class SingleImageClassificationFragment : Fragment() {

    // UI

    private lateinit var imageView: ImageView
    private lateinit var predictionTextView: TextView
    private lateinit var latencyTextView: TextView

    // View Model

    // requires fragment-ktx dependency
    // val viewModel: ClassificationViewModel by activityViewModels()

    private val viewModel: MainViewModel by lazy {
        ViewModelProviders.of(requireActivity()).get(MainViewModel::class.java)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        // Inflate the layout for this fragment

        val root = inflater.inflate(R.layout.fragment_single_image_tab, container, false)

        imageView = root.findViewById(R.id.imageview)
        predictionTextView = root.findViewById(R.id.predictions)
        latencyTextView = root.findViewById(R.id.latency)

        // Use any provided bitmap

        viewModel.bitmap?.let {
            doBitmap(it)
        }

        return root
    }

    private fun doBitmap(bitmap: Bitmap) {
        imageView.setImageBitmap(bitmap)

        viewModel.modelRunner.runInferenceOnFrame( {
            bitmap
        }, { output: Map<String,Any>, l: Long ->
            val classification = output["classification"] as? Map<String, Float>
            val top5 = TIOClassificationHelper.topN(classification, RESULTS_TO_SHOW)
            val top5formatted = formattedResults(top5)

            Handler(Looper.getMainLooper()).post(Runnable {
                predictionTextView.text = top5formatted
                latencyTextView.text = "$l ms"
            })
        })
    }

    private fun formattedResults(results: List<Map.Entry<String, Float>>): String {
        val b = StringBuilder()
        for ((key, value) in results) {
            b.append(key)
            b.append(": ")
            b.append(String.format("%.2f", value))
            b.append("\n")
        }
        b.setLength(b.length - 1)
        return b.toString()
    }
}