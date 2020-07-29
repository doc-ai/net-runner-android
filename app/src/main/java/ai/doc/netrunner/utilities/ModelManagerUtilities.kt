package ai.doc.netrunner.utilities

import ai.doc.tensorio.core.modelbundle.FileModelBundle
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

    fun deleteModelBundle(modelBundle: FileModelBundle) {
        val file = modelBundle.file ?: return
        file.deleteRecursively()
    }

}