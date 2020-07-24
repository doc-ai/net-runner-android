package ai.doc.netrunner.fragments

import ai.doc.netrunner.R
import ai.doc.netrunner.viewmodels.ModelBundlesViewModel
import ai.doc.tensorio.TIOModel.TIOModelBundle
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels

private const val ARG_MODEL_BUNDLE_ID = "model_bundle_id"

class ModelBundleFragment : Fragment() {

    companion object {
        fun newInstance(modelBundle: TIOModelBundle): ModelBundleFragment {
            val args = Bundle().apply {
                putString(ARG_MODEL_BUNDLE_ID, modelBundle.identifier)
            }
            return ModelBundleFragment().apply {
                arguments = args
            }
        }
    }

    interface Callbacks {
        fun onViewJsonSelected(modelBundle: TIOModelBundle)
        fun onDeleteModelSelected(modelBundle: TIOModelBundle)
    }

    private val modelBundlesViewModel by activityViewModels<ModelBundlesViewModel>()

    private lateinit var modelBundle: TIOModelBundle

    private var callbacks: Callbacks? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        arguments?.getString(ARG_MODEL_BUNDLE_ID)?.let {
            modelBundle = modelBundlesViewModel.bundleWithId(it)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_model_bundle, container, false)

        setHasOptionsMenu(true)

        activity?.findViewById<Toolbar>(R.id.toolbar)?.title = modelBundle.name
        activity?.findViewById<ImageButton>(R.id.import_model)?.visibility = View.GONE

        view.findViewById<TextView>(R.id.title).text = modelBundle.name
        view.findViewById<TextView>(R.id.authors).text = modelBundle.author
        view.findViewById<TextView>(R.id.info).text = modelBundle.details
        view.findViewById<TextView>(R.id.license).text = modelBundle.license

        view.findViewById<Button>(R.id.view_json_button).setOnClickListener {
            callbacks?.onViewJsonSelected(modelBundle)
        }

        activity?.findViewById<ImageButton>(R.id.delete_model)?.visibility = if (modelBundlesViewModel.isDownloaded(modelBundle)) View.VISIBLE else View.GONE
        activity?.findViewById<ImageButton>(R.id.delete_model)?.setOnClickListener {
            requestDelete()
        }

        return view
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        callbacks = context as Callbacks?
    }

    override fun onDetach() {
        super.onDetach()
        callbacks = null
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

    private fun requestDelete() {
        AlertDialog.Builder(requireContext()).apply {
            setTitle(R.string.delete_model_dialog_title)
            setMessage(R.string.delete_model_dialog_message)

            setPositiveButton(R.string.delete_model_dialog_delete_button) { dialog, _ ->
                callbacks?.onDeleteModelSelected(modelBundle)
                dialog.dismiss()
            }
            setNegativeButton(R.string.delete_model_dialog_cancel_button) { dialog, _ ->
                dialog.cancel()
            }

        }.show()
    }

}