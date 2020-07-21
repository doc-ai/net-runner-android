package ai.doc.netrunner.fragments

import ai.doc.netrunner.viewmodels.ModelBundlesViewModel
import ai.doc.netrunner.R
import ai.doc.tensorio.TIOModel.TIOModelBundle
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class ModelBundleListFragment : Fragment() {

    companion object {
        fun newInstance(): ModelBundleListFragment {
            return ModelBundleListFragment()
        }
    }

    private inner class ModelBundleHolder(view: View) : RecyclerView.ViewHolder(view) {
        val titleTextView: TextView = itemView.findViewById(R.id.model_bundle_title)
        val infoTextView: TextView = itemView.findViewById(R.id.model_bundle_info)

        fun bind(modelBundle: TIOModelBundle) {
            titleTextView.text = modelBundle.name
            infoTextView.text = modelBundle.details
        }
    }

    private inner class ModelBundleAdapter(var modelBundles: List<TIOModelBundle>) : RecyclerView.Adapter<ModelBundleHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ModelBundleHolder {
            return ModelBundleHolder( layoutInflater.inflate(R.layout.list_item_model_bundle, parent, false) )
        }

        override fun getItemCount(): Int = modelBundles.size

        override fun onBindViewHolder(holder: ModelBundleHolder, position: Int) {
            holder.bind(modelBundles[position])
        }
    }

    private val modelBundlesViewModel by activityViewModels<ModelBundlesViewModel>()

    private lateinit var modelBundleRecyclerView: RecyclerView

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_model_bundle_list, container, false)

        modelBundleRecyclerView = view.findViewById(R.id.model_bundle_recycler_view)
        modelBundleRecyclerView.adapter = ModelBundleAdapter(modelBundlesViewModel.modelBundles)
        modelBundleRecyclerView.layoutManager = LinearLayoutManager(context)
        modelBundleRecyclerView.addItemDecoration(DividerItemDecoration(context, DividerItemDecoration.VERTICAL))

        return view
    }
}