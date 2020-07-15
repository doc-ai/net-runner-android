package ai.doc.netrunner.outputhandler

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import ai.doc.netrunner.R
import ai.doc.tensorio.TIOUtilities.TIOClassificationHelper
import android.widget.TextView

private const val CLASSIFICATION_TOP_N_COUNT = 3
private const val SMOOTHING_DECAY = 0.7f
private const val SMOOTHING_THRESHOLD = 0.02f
private const val SMOOTHING_TOP_N_COUNT = 5

/**
 * A simple [Fragment] subclass.
 * Displays the results of an ImageNet Classification model
 */

class MobileNetClassificationOutputHandler : Fragment(), OutputHandler {

    companion object {
        val type = "image.classification.imagenet"
    }

    private lateinit var predictionTextView: TextView

    override var output: Map<String, Any>? = null
        set(value) {
            processOutput(value)
            field = value
        }

    // View Management

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_image_net_classification_output_handler, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        predictionTextView = view.findViewById(R.id.predictions)
    }

    // Output Processing

    var previousTop5 = ArrayList<Map.Entry<String,Float>>()

    private fun processOutput(output: Map<String, Any>?) {
        output?.let {
            val classification = it["classification"] as? Map<String, Float>
            val top5 = TIOClassificationHelper.topN(classification, CLASSIFICATION_TOP_N_COUNT)
            val top5smoothed = TIOClassificationHelper.smoothClassification(previousTop5, top5, SMOOTHING_DECAY, SMOOTHING_THRESHOLD)
            val top5ordered = top5smoothed.take(SMOOTHING_TOP_N_COUNT).sortedWith(compareBy { it.value }).reversed()
            val top5formatted = formattedResults(top5ordered)

            predictionTextView.text = top5formatted
            previousTop5 = top5smoothed as ArrayList<Map.Entry<String, Float>>
        }
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