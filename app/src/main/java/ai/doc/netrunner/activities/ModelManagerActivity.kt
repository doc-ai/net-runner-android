package ai.doc.netrunner.activities

import ai.doc.netrunner.viewmodels.ModelBundlesViewModel
import ai.doc.netrunner.R
import ai.doc.netrunner.fragments.ImportModelBundleFragment
import ai.doc.netrunner.fragments.ModelBundleFragment
import ai.doc.netrunner.fragments.ModelBundleJsonFragment
import ai.doc.netrunner.fragments.ModelBundleListFragment
import ai.doc.netrunner.utilities.ModelManagerUtilities
import ai.doc.tensorio.TIOModel.TIOModelBundle
import ai.doc.tensorio.TIOModel.TIOModelBundleManager
import android.app.Activity
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.view.MenuItem
import android.widget.ImageButton
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.navigation.NavigationView
import java.io.File

const val IMPORT_DIALOG_TAG = "import_dialog"
const val MODEL_MANAGER_DID_UPDATE_MODELS = "ai.doc.netrunner.model_manager.did_update_models"

class ModelManagerActivity : AppCompatActivity(), ModelBundleListFragment.Callbacks, ModelBundleFragment.Callbacks, ImportModelBundleFragment.Callbacks {

    private val modelBundlesViewModel: ModelBundlesViewModel by lazy {
        ViewModelProvider(this).get(ModelBundlesViewModel::class.java)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_model_manager)

        modelBundlesViewModel.setBundleManagers(
                TIOModelBundleManager(applicationContext, ""),
                TIOModelBundleManager(ModelManagerUtilities.getModelFilesDir(this)))

        setSupportActionBar(findViewById<Toolbar>(R.id.toolbar))
        supportActionBar?.setHomeButtonEnabled(true)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        findViewById<ImageButton>(R.id.import_model).setOnClickListener {
            importModel()
        }

        if (savedInstanceState == null) {
            val fragment = ModelBundleListFragment.newInstance()
            supportFragmentManager
                    .beginTransaction()
                    .add(R.id.container, fragment)
                    .commit()
        }
    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        return when (item?.itemId) {
            android.R.id.home ->
                // Let the active fragment handle it
                false
            else ->
                super.onOptionsItemSelected(item)
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

    override fun onDeleteModelSelected(modelBundle: TIOModelBundle) {
        supportFragmentManager.popBackStack()

        ModelManagerUtilities.deleteModelBundle(modelBundle)
        modelBundlesViewModel.reloadManagers()
        setDidUpdateModels()

        Handler().postDelayed({
            (supportFragmentManager.findFragmentById(R.id.container) as? ModelBundleListFragment)?.reloadView()
        }, 100)
    }

    override fun onModelImported(file: File) {
        modelBundlesViewModel.reloadManagers()
        setDidUpdateModels()

        Handler().postDelayed({
            (supportFragmentManager.findFragmentById(R.id.container) as? ModelBundleListFragment)?.reloadView()
        }, 100)
    }

    private fun setDidUpdateModels() {
        setResult(Activity.RESULT_OK, Intent().apply {
            putExtra(MODEL_MANAGER_DID_UPDATE_MODELS, true)
        })
    }

    private fun importModel() {
        val fragment = ImportModelBundleFragment()
        fragment.show(supportFragmentManager, IMPORT_DIALOG_TAG)
    }
}