package ai.doc.netrunner.viewmodels

import ai.doc.netrunner.utilities.ModelRunner
import android.graphics.Bitmap
import androidx.lifecycle.ViewModel

class MainViewModel : ViewModel() {

    // Selected Tab

    enum class Tab {
        LiveVideo,
        SinglePhoto
    }

    var currentTab = Tab.LiveVideo

    // Model Runner

    lateinit var modelRunner: ModelRunner

    // Last Bitmap Used For Inference

    var bitmap: Bitmap? = null
}