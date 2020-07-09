package ai.doc.netrunner.view

import ai.doc.netrunner.ModelRunner
import ai.doc.netrunner.ModelRunner.ModelRunnerDataSource
import ai.doc.netrunner.ModelRunner.ClassificationResultListener
import ai.doc.netrunner.R
import ai.doc.netrunner.databinding.FragmentLiveCameraClassificationBinding
import ai.doc.tensorio.TIOUtilities.TIOClassificationHelper
import android.graphics.Bitmap
import android.os.Bundle
import android.view.LayoutInflater
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModelProviders

/**
 * A simple [Fragment] subclass.
 */

class LiveCameraClassificationFragment : LiveCameraFragment(), ModelRunnerDataSource, ClassificationResultListener {

    companion object {
        private const val RESULTS_TO_SHOW = 3
        private const val FILTER_STAGES = 3
        private const val FILTER_FACTOR = 0.4f
    }

    private val latency = MutableLiveData<String>()
    private val predictions = MutableLiveData<String>()
    private var textureView: TextureView? = null
    private var filterLabelProbArray: Array<FloatArray>? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        // Inflate the layout for this fragment
        val binding: FragmentLiveCameraClassificationBinding = DataBindingUtil.inflate(inflater, R.layout.fragment_live_camera_classification, container, false)
        binding.fragment = this
        binding.lifecycleOwner = this
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        textureView = view.findViewById(R.id.texture)
    }

    override fun getNextInput(size_x: Int, size_y: Int): Bitmap? {
        return textureView!!.getBitmap(size_x, size_y)
    }

    // TODO: why a listener callback as well as a listener lambda in startClassification

    override fun classificationResult(requestId: Int, output: Any?, l: Long) {
        val classification = (output as Map<String?, Any?>)["classification"] as Map<String, Float>?
        val top5 = TIOClassificationHelper.topN(classification, RESULTS_TO_SHOW)
        val top5formatted = formattedResults(top5)

        // TODO: Apply smoothing filter
        predictions.postValue(top5formatted)
        latency.postValue("$l ms")
    }

    override fun onResume() {
        super.onResume()
        startClassification()
    }

    override fun onPause() {
        super.closeCamera()
        stopClassification()
        super.onPause()
    }

    fun startClassification() {
        val vm = ViewModelProviders.of(activity!!).get(ClassificationViewModel::class.java)

        // vm.modelRunner!!.dataSource = this
        // vm.modelRunner!!.listener = this

        vm.modelRunner!!.startStreamClassification(this) { requestId: Int, output: Any, l: Long ->
            val classification = (output as Map<String?, Any?>)["classification"] as Map<String, Float>?
            val top5 = TIOClassificationHelper.topN(classification, RESULTS_TO_SHOW)
            val top5formatted = formattedResults(top5)

            // TODO: Apply smoothing filter
            predictions.postValue(top5formatted)
            latency.postValue("$l ms")
        }
    }

    fun stopClassification() {
        val vm = ViewModelProviders.of(activity!!).get(ClassificationViewModel::class.java)
        vm.modelRunner!!.stopStreamClassification()
    }

    fun getLatency(): LiveData<String> {
        return latency
    }

    fun getPredictions(): LiveData<String> {
        return predictions
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

    // TODO: Update exponential decay function

//    private fun applyFilter(result: FloatArray, numLabels: Int) {
//        if (filterLabelProbArray == null || filterLabelProbArray!![0].length != numLabels) {
//            filterLabelProbArray = Array(FILTER_STAGES) { FloatArray(numLabels) }
//        }
//
//        // Low pass filter `labelProbArray` into the first stage of the filter.
//        for (j in 0 until numLabels) {
//            filterLabelProbArray!![0][j] += FILTER_FACTOR * (result[j] - filterLabelProbArray!![0][j])
//        }
//        // Low pass filter each stage into the next.
//        for (i in 1 until FILTER_STAGES) {
//            for (j in 0 until numLabels) {
//                filterLabelProbArray!![i][j] += FILTER_FACTOR * (filterLabelProbArray!![i - 1][j] - filterLabelProbArray!![i][j])
//            }
//        }
//
//        // Copy the last stage filter output back to `labelProbArray`.
//        System.arraycopy(filterLabelProbArray!![FILTER_STAGES - 1], 0, result, 0, numLabels)
//    }
}