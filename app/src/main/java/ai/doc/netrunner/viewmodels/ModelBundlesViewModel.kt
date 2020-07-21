package ai.doc.netrunner.viewmodels

import ai.doc.tensorio.TIOModel.TIOModelBundle
import ai.doc.tensorio.TIOModel.TIOModelBundleManager
import androidx.lifecycle.ViewModel

class ModelBundlesViewModel : ViewModel() {

    lateinit var manager: TIOModelBundleManager

    val modelIds: List<String> by lazy {
        manager.bundleIds.toList()
    }

    val modelBundles: List<TIOModelBundle> by lazy {
        modelIds.map { this.manager.bundleWithId(it) }.sortedBy { it.identifier }
    }
}
