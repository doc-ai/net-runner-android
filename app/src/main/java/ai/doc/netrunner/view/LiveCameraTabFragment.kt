package ai.doc.netrunner.view

import ai.doc.netrunner.MainViewModel
import ai.doc.netrunner.R

import ai.doc.tensorio.TIOUtilities.TIOClassificationHelper

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

private const val CLASSIFICATION_TOP_N_COUNT = 3
private const val SMOOTHING_DECAY = 0.7f
private const val SMOOTHING_THRESHOLD = 0.02f
private const val SMOOTHING_TOP_N_COUNT = 5

// TODO: Assumes classification model (#24)

/**
 * A simple [Fragment] subclass.
 */

class LiveCameraClassificationFragment : LiveCameraFragment() {

    // UI

    private lateinit var textureView: TextureView
    private lateinit var predictionTextView: TextView
    private lateinit var latencyTextView: TextView

    // View Model

    private val viewModel by activityViewModels<MainViewModel>()

    // Creation

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_live_camera_tab, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        textureView = view.findViewById(R.id.texture)
        predictionTextView = view.findViewById(R.id.predictions)
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
        var previousTop5 = ArrayList<Map.Entry<String,Float>>()

        viewModel.modelRunner.startStreamingInference( {
            textureView.bitmap
        }, { output: Map<String,Any>, l: Long ->
            val classification = output["classification"] as? Map<String, Float>
            val top5 = TIOClassificationHelper.topN(classification, CLASSIFICATION_TOP_N_COUNT)
            val top5smoothed = TIOClassificationHelper.smoothClassification(previousTop5, top5, SMOOTHING_DECAY, SMOOTHING_THRESHOLD)
            val top5ordered = top5smoothed.take(SMOOTHING_TOP_N_COUNT).sortedWith(compareBy { it.value }).reversed()
            val top5formatted = formattedResults(top5ordered)

            Handler(Looper.getMainLooper()).post(Runnable {
                predictionTextView.text = top5formatted
                latencyTextView.text = "$l ms"
            })

            previousTop5 = top5smoothed as ArrayList<Map.Entry<String, Float>>
        })
    }

    fun stopClassification() {
        viewModel.modelRunner.stopStreamingInference()
    }

    private fun formattedResults(results: List<Map.Entry<String, Float>>): String {
        val b = StringBuilder()
        for ((key, value) in results) {
            b.append(key)
            b.append(": ")
            b.append(String.format("%.2f", value))
            b.append("\n")
        }

        if (b.isNotEmpty()) {
            b.setLength(b.length - 1)
        }

        return b.toString()
    }

}