package ai.doc.netrunner.viewmodels

import ai.doc.netrunner.utilities.ModelRunner
import ai.doc.tensorio.TIOModel.TIOModelBundle
import ai.doc.tensorio.TIOModel.TIOModelBundleManager
import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import java.util.ArrayList

class MainViewModel : ViewModel() {

    // Selected Tab

    enum class Tab {
        LiveVideo,
        SinglePhoto
    }

    var currentTab = Tab.LiveVideo

    // Model Management

    lateinit var manager: TIOModelBundleManager
    lateinit var modelRunner: ModelRunner

//    val modelIds: ArrayList<String> by lazy {
//        ArrayList<String>(manager.bundleIds)
//    }

    val modelIds: List<String> by lazy {
        manager.bundleIds.toList()
    }

    val modelBundles: List<TIOModelBundle> by lazy {
        modelIds.map { this.manager.bundleWithId(it) }.sortedBy { it.identifier }
    }

    // Last Bitmap Used For Inference

    var bitmap: Bitmap? = null
}