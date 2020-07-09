package ai.doc.netrunner.view

import ai.doc.netrunner.ModelRunner
import ai.doc.tensorio.TIOModel.TIOModelBundleManager
import androidx.lifecycle.ViewModel

class ClassificationViewModel : ViewModel() {
    var currentTab = -1
    var modelRunner: ModelRunner? = null
    var manager: TIOModelBundleManager? = null
}