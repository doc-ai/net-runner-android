package ai.doc.netrunner.utilities

import ai.doc.netrunner.BuildConfig
import ai.doc.tensorio.core.modelbundle.ModelBundle
import android.content.Context
import java.io.File
import java.io.IOException

object ModelManagerUtilities {

    /** Returns the File where downloaded models are permanently stored, creating it if necessary */

    @Throws(IOException::class)
    fun getModelFilesDir(context: Context): File {
        val modelsDir = File(context.filesDir, "models")

        if (!modelsDir.exists() && !modelsDir.mkdir()) {
           throw IOException()
        }

        return modelsDir
    }

    /** Deletes the model bundle represented by a File */

    fun deleteModelBundle(modelBundle: ModelBundle) {
        if (BuildConfig.DEBUG && modelBundle.file == null) {
            error("Only file model bundles can ever be deleted")
        }

        val file = modelBundle.file ?: return
        file.deleteRecursively()
    }

}