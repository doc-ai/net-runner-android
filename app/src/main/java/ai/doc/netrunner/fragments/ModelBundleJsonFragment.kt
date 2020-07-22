package ai.doc.netrunner.fragments

import ai.doc.netrunner.R
import ai.doc.netrunner.viewmodels.ModelBundlesViewModel
import ai.doc.tensorio.TIOModel.TIOModelBundle
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels

private const val ARG_MODEL_BUNDLE_ID = "model_bundle_id"

class ModelBundleJsonFragment : Fragment() {

    companion object {
        fun newInstance(modelBundle: TIOModelBundle): ModelBundleJsonFragment {
            val args = Bundle().apply {
                putString(ARG_MODEL_BUNDLE_ID, modelBundle.identifier)
            }
            return ModelBundleJsonFragment().apply {
                arguments = args
            }
        }
    }

    private val modelBundlesViewModel by activityViewModels<ModelBundlesViewModel>()

    private lateinit var modelBundle: TIOModelBundle

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        arguments?.getString(ARG_MODEL_BUNDLE_ID)?.let {
            modelBundle = modelBundlesViewModel.bundleWithId(it)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_model_bundle_json, container, false)

        setHasOptionsMenu(true)

        activity?.findViewById<Toolbar>(R.id.toolbar)?.title = modelBundle.name
        activity?.findViewById<ImageButton>(R.id.import_model)?.visibility = View.INVISIBLE

        view.findViewById<TextView>(R.id.json_textview).text = modelBundle.info.toString(2)

        return view
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item?.itemId) {
            android.R.id.home -> {
                activity?.supportFragmentManager?.popBackStack()
                true
            }
            else ->
                super.onOptionsItemSelected(item)
        }
    }
}