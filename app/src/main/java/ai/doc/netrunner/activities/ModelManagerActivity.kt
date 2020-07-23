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
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.MenuItem
import android.widget.ImageButton
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.ViewModelProvider

const val IMPORT_DIALOG_TAG = "import_dialog"

class ModelManagerActivity : AppCompatActivity(), ModelBundleListFragment.Callbacks, ModelBundleFragment.Callbacks {

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

    private fun importModel() {
        val fragment = ImportModelBundleFragment()
        fragment.show(supportFragmentManager, IMPORT_DIALOG_TAG)
    }
}