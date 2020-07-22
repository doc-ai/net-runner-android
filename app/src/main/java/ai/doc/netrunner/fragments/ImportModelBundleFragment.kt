package ai.doc.netrunner.fragments

import ai.doc.netrunner.R
import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import androidx.fragment.app.DialogFragment

class ImportModelBundleFragment : DialogFragment() {

//    interface Callbacks {
//        fun onEnterText(text: String)
//    }
//
//    private lateinit var callbacks: Callbacks
//
//    override fun onAttach(context: Context) {
//        super.onAttach(context)
//        callbacks = context as ModelBundleFragment.Callbacks?
//    }

    lateinit var progressBar: ProgressBar

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return AlertDialog.Builder(activity).apply {
            setTitle("Import Model")

            val inflater = activity?.layoutInflater
            val view = inflater?.inflate(R.layout.fragment_import_model_bundle, null, false)

            view?.apply {
                progressBar = findViewById(R.id.progress_bar)
            }

            setView(view)

            setPositiveButton("OK", null)
            setNegativeButton(android.R.string.cancel) { dialog, which ->
                dialog.cancel()
            }

        }.create()
    }

    override fun onResume() {
        super.onResume()

        val d = dialog as? AlertDialog ?: return

        d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            downloadModel()
        }
    }

    private fun downloadModel() {
        progressBar.visibility = View.VISIBLE
    }
}