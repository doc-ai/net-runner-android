package ai.doc.netrunner.fragments

import ai.doc.netrunner.viewmodels.ModelBundlesViewModel
import ai.doc.netrunner.R
import ai.doc.tensorio.TIOModel.TIOModelBundle
import android.content.Context
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
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class ModelBundleListFragment : Fragment() {

    companion object {
        fun newInstance(): ModelBundleListFragment {
            return ModelBundleListFragment()
        }
    }

    interface Callbacks {
        fun onModelBundleSelected(modelBundle: TIOModelBundle)
    }

    private inner class ModelBundleHolder(view: View) : RecyclerView.ViewHolder(view), View.OnClickListener {
        private lateinit var modelBundle: TIOModelBundle

        private val titleTextView: TextView = itemView.findViewById(R.id.model_bundle_title)
        private val infoTextView: TextView = itemView.findViewById(R.id.model_bundle_info)

        init {
            itemView.setOnClickListener(this)
        }

        fun bind(modelBundle: TIOModelBundle) {
            this.modelBundle = modelBundle
            titleTextView.text = modelBundle.name
            infoTextView.text = modelBundle.details
        }

        override fun onClick(v: View?) {
            callbacks?.onModelBundleSelected(modelBundle)
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

    private var callbacks: Callbacks? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_model_bundle_list, container, false)

        setHasOptionsMenu(true)

        activity?.findViewById<Toolbar>(R.id.toolbar)?.title = "Manage Models"
        activity?.findViewById<ImageButton>(R.id.import_model)?.visibility = View.VISIBLE

        // import_model

        modelBundleRecyclerView = view.findViewById(R.id.model_bundle_recycler_view)
        modelBundleRecyclerView.adapter = ModelBundleAdapter(modelBundlesViewModel.modelBundles)
        modelBundleRecyclerView.layoutManager = LinearLayoutManager(context)
        modelBundleRecyclerView.addItemDecoration(DividerItemDecoration(context, DividerItemDecoration.VERTICAL))

        // TODO: Update after model download
        // TODO: Update after model deletion
        // modelBundleRecyclerView.adapter.notifyDataSetChanged()

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
                activity?.finish()
                true
            }
            else ->
                super.onOptionsItemSelected(item)
        }
    }
}