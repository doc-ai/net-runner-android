package ai.doc.netrunner.viewmodels

import ai.doc.tensorio.TIOModel.TIOModelBundle
import ai.doc.tensorio.TIOModel.TIOModelBundleManager
import androidx.lifecycle.ViewModel

class ModelBundlesViewModel : ViewModel() {

    private lateinit var assetsManager: TIOModelBundleManager
    private lateinit var filesManager: TIOModelBundleManager

    /** You must provide the view model with an assetsManager and a filesManager prior to using it */

    fun setBundleManagers(assetsManager: TIOModelBundleManager, filesManager: TIOModelBundleManager) {
        this.assetsManager = assetsManager
        this.filesManager = filesManager
    }

    val modelIds: List<String> by lazy {
        assetsManager.bundleIds.toList()
    }

    val modelBundles: List<TIOModelBundle> by lazy {
        modelIds.map { this.assetsManager.bundleWithId(it) }.sortedBy { it.identifier }
    }

    fun bundleWithId(identifier: String): TIOModelBundle {
        return assetsManager.bundleWithId(identifier)
    }
}
