package ai.doc.netrunner.view

import ai.doc.netrunner.MainViewModel
import ai.doc.netrunner.R
import ai.doc.netrunner.databinding.FragmentLiveCameraTabBinding

import ai.doc.tensorio.TIOUtilities.TIOClassificationHelper

import android.os.Bundle
import android.view.LayoutInflater
import android.view.TextureView
import android.view.View
import android.view.ViewGroup

import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModelProviders

private const val RESULTS_TO_SHOW = 3
private const val FILTER_STAGES = 3
private const val FILTER_FACTOR = 0.4f

// TODO: Assumes classification model (#24)

/**
 * A simple [Fragment] subclass.
 */

class LiveCameraClassificationFragment : LiveCameraFragment() {

    // UI

    private lateinit var textureView: TextureView

    // Live Data Variables

    private val _latency = MutableLiveData<String>()
    val latency: LiveData<String> = _latency

    private val _predictions = MutableLiveData<String>()
    val predictions: LiveData<String> = _predictions

    // View Model

    private val viewModel by activityViewModels<MainViewModel>()

    // Creation

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        // Inflate the layout for this fragment
        val binding = DataBindingUtil.inflate(inflater, R.layout.fragment_live_camera_tab, container, false) as FragmentLiveCameraTabBinding
        binding.fragment = this
        binding.lifecycleOwner = this
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        textureView = view.findViewById(R.id.texture)
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
            val top5 = TIOClassificationHelper.topN(classification, RESULTS_TO_SHOW)
            val top5smoothed = TIOClassificationHelper.smoothClassification(previousTop5, top5, 0.7f, 0.02f)
            val top5ordered = top5smoothed.take(5).sortedWith(compareBy { it.value }).reversed()
            val top5formatted = formattedResults(top5ordered)

            _predictions.postValue(top5formatted)
            _latency.postValue("$l ms")

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