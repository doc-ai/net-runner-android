package ai.doc.netrunner.view

import ai.doc.netrunner.R
import ai.doc.netrunner.databinding.FragmentSingleImageBinding

import ai.doc.tensorio.TIOUtilities.TIOClassificationHelper

import android.graphics.Bitmap
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView

import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModelProviders

private const val RESULTS_TO_SHOW = 3

// TODO: Assumes classification model (#24)

class SingleImageClassificationFragment : Fragment() {

    // UI

    private lateinit var imageView: ImageView

    // Live Data Variables

    private val _latency = MutableLiveData<String>()
    val latency: LiveData<String> = _latency

    private val _predictions = MutableLiveData<String>()
    val predictions: LiveData<String> = _predictions

    // View Model

    // requires fragment-ktx dependency
    // val viewModel: ClassificationViewModel by activityViewModels()

    private val viewModel: ClassificationViewModel by lazy {
        ViewModelProviders.of(requireActivity()).get(ClassificationViewModel::class.java)
    }

    private var bitmap: Bitmap? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        // Inflate the layout for this fragment

        val binding: FragmentSingleImageBinding = DataBindingUtil.inflate(inflater, R.layout.fragment_single_image, container, false)
        binding.fragment = this
        binding.lifecycleOwner = this

        val root = binding.root
        imageView = root.findViewById(R.id.imageview)

        // Use any provided bitmap

        val bitmap = this.arguments?.getParcelable("bitmap") as Bitmap?

        if (bitmap != null) {
            this.bitmap = bitmap
            doBitmap(bitmap)
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

            _predictions.postValue(top5formatted)
            _latency.postValue("$l ms")
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