package ai.doc.netrunner.utilities

import java.io.File
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

/** File extension for unzipping a file */

// https://stackoverflow.com/questions/3382996/how-to-unzip-files-programmatically-in-android/40125872

data class ZipIO (val entry: ZipEntry, val output: File)

@Throws(IOException::class)
fun File.unzip(unzipLocationRoot: File? = null): File {

    val rootFolder = unzipLocationRoot ?: File(parentFile.absolutePath + File.separator + nameWithoutExtension)
    if (!rootFolder.exists()) {
        rootFolder.mkdirs()
    }

    ZipFile(this).use { zip ->
        zip
            .entries()
            .asSequence()
            .map {
                val outputFile = File(rootFolder.absolutePath + File.separator + it.name)
                ZipIO(it, outputFile)
            }
            .map {
                it.output.parentFile?.run {
                    if (!exists()) mkdirs()
                }
                it
            }
            .filter { !it.entry.isDirectory }
            .forEach { (entry, output) ->
                zip.getInputStream(entry).use { input ->
                    output.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            }
    }

    return rootFolder
}