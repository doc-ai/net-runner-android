package ai.doc.netrunner.view

import ai.doc.netrunner.ModelRunner
import ai.doc.tensorio.TIOModel.TIOModelBundleManager
import androidx.lifecycle.ViewModel
import java.util.ArrayList

class ClassificationViewModel : ViewModel() {
    lateinit var manager: TIOModelBundleManager
    lateinit var modelRunner: ModelRunner
    var currentTab = -1

    val modelIds: ArrayList<String> by lazy {
        ArrayList<String>(manager.bundleIds)
    }
}