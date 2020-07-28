package ai.doc.netrunner.viewmodels

import ai.doc.tensorio.core.modelbundle.ModelBundle
import ai.doc.tensorio.core.modelbundle.Manager
import androidx.lifecycle.ViewModel

class ModelBundlesViewModel : ViewModel() {

    private lateinit var assetsManager: Manager
    private lateinit var filesManager: Manager

    lateinit var modelIds: List<String>
        private set

    lateinit var modelBundles: List<ModelBundle>
        private set

    /** You must provide the view model with an assetsManager and a filesManager prior to using it */

    fun setBundleManagers(assetsManager: Manager, filesManager: Manager) {
        this.assetsManager = assetsManager
        this.filesManager = filesManager
        loadIdsAndBundles()
    }

    /** Reload the managers when user models have changed (downloaded or deleted) */

    fun reloadManagers() {
        assetsManager.reload()
        filesManager.reload()
        loadIdsAndBundles()
    }

    /** Force unwrapping the optional bundle values because ids will only every come from the available bundles */

    private fun loadIdsAndBundles() {
        val assetIds = assetsManager.bundleIds.toList()
        val fileIds = filesManager.bundleIds.toList()

        modelIds = assetIds + fileIds

        val assetBundles = assetIds
                .map { this.assetsManager.bundleWithId(it)!! }
                .sortedBy { it.identifier }
        val fileBundles = fileIds
                .map { this.filesManager.bundleWithId(it)!! }
                .sortedBy { it.identifier }

        modelBundles = assetBundles + fileBundles
    }

    /** Prefers a downloaded model over one packaged with the application */

    // TODO: Do not force unwrap here and return an option

    fun bundleWithId(identifier: String): ModelBundle {
        return filesManager.bundleWithId(identifier) ?: assetsManager.bundleWithId(identifier)!!
    }

    /** Returns true if the model bundle is one packaged with the application */

    fun isAsset(bundle: ModelBundle): Boolean {
        return !filesManager.bundleIds.toList().contains(bundle.identifier)
    }

    /** Returns true if the model bundle is one the user has downloaded */

    fun isDownloaded(bundle: ModelBundle): Boolean {
        return filesManager.bundleIds.toList().contains(bundle.identifier)
    }

}
