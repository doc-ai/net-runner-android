package ai.doc.netrunner.view

import ai.doc.netrunner.ModelRunner
import ai.doc.tensorio.TIOModel.TIOModelBundleManager
import androidx.lifecycle.ViewModel
import java.util.ArrayList

class ClassificationViewModel : ViewModel() {
    enum class Tab {
        LiveVideo,
        TakePhoto,
        ChoosePhoto
    }

    lateinit var manager: TIOModelBundleManager
    lateinit var modelRunner: ModelRunner
    var currentTab = Tab.LiveVideo

    val modelIds: ArrayList<String> by lazy {
        ArrayList<String>(manager.bundleIds)
    }
}