package ai.doc.netrunner.activities

import ai.doc.netrunner.viewmodels.ModelBundlesViewModel
import ai.doc.netrunner.R
import ai.doc.netrunner.fragments.ModelBundleFragment
import ai.doc.netrunner.fragments.ModelBundleJsonFragment
import ai.doc.netrunner.fragments.ModelBundleListFragment
import ai.doc.tensorio.TIOModel.TIOModelBundle
import ai.doc.tensorio.TIOModel.TIOModelBundleManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import androidx.lifecycle.ViewModelProvider

class ModelManagerActivity : AppCompatActivity(), ModelBundleListFragment.Callbacks, ModelBundleFragment.Callbacks {

    private val modelBundlesViewModel: ModelBundlesViewModel by lazy {
        ViewModelProvider(this).get(ModelBundlesViewModel::class.java)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_model_manager)

        modelBundlesViewModel.manager = TIOModelBundleManager(applicationContext, "")

        if (savedInstanceState == null) {
            val fragment = ModelBundleListFragment.newInstance()
            supportFragmentManager
                    .beginTransaction()
                    .add(R.id.container, fragment)
                    .commit()
        }
    }

    override fun onModelBundleSelected(modelBundle: TIOModelBundle) {
        val fragment = ModelBundleFragment.newInstance(modelBundle)
        supportFragmentManager
                .beginTransaction()
                .replace(R.id.container, fragment)
                .addToBackStack(null)
                .commit()
    }

    override fun onViewJsonSelected(modelBundle: TIOModelBundle) {
        val fragment = ModelBundleJsonFragment.newInstance(modelBundle)
        supportFragmentManager
                .beginTransaction()
                .replace(R.id.container, fragment)
                .addToBackStack(null)
                .commit()
    }
}