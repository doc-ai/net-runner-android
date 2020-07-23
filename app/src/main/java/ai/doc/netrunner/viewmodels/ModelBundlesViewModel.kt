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
        assetsManager.bundleIds.toList() + filesManager.bundleIds.toList()
    }

    /** Force unwrapping the optional bundle values because ids will only every come from the available bundles */

    val modelBundles: List<TIOModelBundle> by lazy {
        assetsManager.bundleIds.toList().map { this.assetsManager.bundleWithId(it)!! }.sortedBy { it.identifier } +
                filesManager.bundleIds.toList().map { this.filesManager.bundleWithId(it)!! }.sortedBy { it.identifier }
    }

    /** Prefers a downloaded model over one packaged with the application */

    fun bundleWithId(identifier: String): TIOModelBundle {
        return filesManager.bundleWithId(identifier) ?: assetsManager.bundleWithId(identifier)!!
    }

    /** Returns true if the model bundle is one packaged with the application */

    fun isAsset(bundle: TIOModelBundle): Boolean {
        return !filesManager.bundleIds.toList().contains(bundle.identifier)
    }

    /** Returns true if the model bundle is one the user has downloaded */

    fun isDownloaded(bundle: TIOModelBundle): Boolean {
        return filesManager.bundleIds.toList().contains(bundle.identifier)
    }

    // TODO: Val's can't be lazy if we're reloading the model manager

    fun reloadManagers() {
        assetsManager.reload()
        filesManager.reload()
    }
}
