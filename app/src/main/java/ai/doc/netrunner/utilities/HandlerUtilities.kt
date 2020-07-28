package ai.doc.netrunner.utilities

import android.os.Handler
import android.os.Looper

object HandlerUtilities {
    fun main(r: Runnable) {
        Handler(Looper.getMainLooper()).post(r)
    }
}