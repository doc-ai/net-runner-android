package ai.doc.netrunner.activities

import ai.doc.netrunner.viewmodels.ModelBundlesViewModel
import ai.doc.netrunner.R
import ai.doc.netrunner.fragments.ModelBundleListFragment
import ai.doc.tensorio.TIOModel.TIOModelBundleManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import androidx.lifecycle.ViewModelProvider

class ModelManagerActivity : AppCompatActivity() {

    private val modelBundlesViewModel: ModelBundlesViewModel by lazy {
        ViewModelProvider(this).get(ModelBundlesViewModel::class.java)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_model_manager)

        modelBundlesViewModel.manager = TIOModelBundleManager(applicationContext, "")

        if (savedInstanceState == null) {
            val fragment = ModelBundleListFragment.newInstance()
            supportFragmentManager.beginTransaction().add(R.id.container, fragment).commit()
        }
    }
}