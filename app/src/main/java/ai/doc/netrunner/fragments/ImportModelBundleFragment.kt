package ai.doc.netrunner.fragments

import ai.doc.netrunner.R
import ai.doc.netrunner.retrofit.NetRunnerService
import android.app.Dialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import okhttp3.OkHttpClient
import okhttp3.ResponseBody

import retrofit2.Retrofit
import retrofit2.http.Url
import java.io.*
import java.lang.Exception
import java.net.ConnectException
import java.util.*

private const val TAG = "ImportModelBundleFrag"

// TODO: Store in cacheDir/model_downloads that is deleted and recreated every time
// TODO: Use filename for model bundle instead of UUID
// TODO: Disable ok button
// TODO: Unzip, validate, move to filesDir/models, update view model

class ImportModelBundleFragment : DialogFragment() {

    lateinit var textField: EditText
    lateinit var progressBar: ProgressBar

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return AlertDialog.Builder(requireActivity()).apply {
            setTitle("Import Model")

            val inflater = requireActivity().layoutInflater
            val view = inflater.inflate(R.layout.fragment_import_model_bundle, null, false)

            progressBar = view.findViewById(R.id.progress_bar)
            textField = view.findViewById(R.id.url_text)

            view.findViewById<TextView>(R.id.tensorio_info).setOnClickListener {
                startActivity(Intent(Intent.ACTION_VIEW).apply {
                    data = Uri.parse("https://github.com/doc-ai/tensorio")
                })
            }

            setView(view)

            setPositiveButton("OK", null)
            setNegativeButton(android.R.string.cancel) { dialog, which ->
                dialog.cancel()
            }

        }.create()
    }

    override fun onResume() {
        super.onResume()

        val d = dialog as? AlertDialog ?: return

        // Custom click listener to prevent tapping OK from dismissing the dialog

        d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            viewLifecycleOwner.lifecycleScope.launch {
                CoroutineScope(Dispatchers.Main).launch {
                    downloadModel(textField.text.toString())
                }
            }
        }
    }

    /** The retrofit service, http://localhost will be overwritten by the full url */

    private val service: NetRunnerService by lazy {
        Retrofit.Builder()
                .baseUrl("http://localhost")
                .client(OkHttpClient.Builder().build())
                .build()
                .create(NetRunnerService::class.java)
    }

    /** Download and install the model bundle zip file */

    private suspend fun downloadModel(@Url modelUrl: String) {
        progressBar.visibility = View.VISIBLE

        val filename = "${UUID.randomUUID().toString()}.zip"

        try {
            saveDownloadToDisk(service.downloadModel(modelUrl), filename)
        } catch (e: ConnectException) {
            showNetworkErrorAlert()
        } catch (e: IOException) {
            showFileDownloadErrorAlert()
        } catch (e: Exception) {
            showOtherErrorAlert()
        } finally {
            progressBar.visibility = View.INVISIBLE
        }
    }

    /** Responsible for actually writing the received stream to disk */

    private suspend fun saveDownloadToDisk(body: ResponseBody, filename: String) = withContext(Dispatchers.IO) {
        // False positive warnings about Blocking IO but the Dispatchers.IO exactly addresses that

        var inputStream: InputStream? = null
        var outputStream: FileOutputStream? = null

        try {
            val destination = File(requireActivity().cacheDir, filename)

            inputStream = body.byteStream()
            outputStream = FileOutputStream(destination)

            val fileReader = ByteArray(4096)
            val fileSize = body.contentLength()
            var fileSizeDownloaded: Long = 0

            while (true) {
                val read = inputStream.read(fileReader)
                if (read == -1) {
                    break
                }

                outputStream.write(fileReader, 0, read)
                fileSizeDownloaded += read.toLong()

                val progress = calculateProgress(fileSize.toDouble(), fileSizeDownloaded.toDouble())
                withContext(Dispatchers.Main) {
                    onDownloadProgressUpdate(progress)
                }
            }

            outputStream.flush();

            withContext(Dispatchers.Main) {
                onDownloadProgressUpdate(100.0)
            }

        } catch (e: IOException) {
            Log.e(TAG, "Failed to save the file!");
            throw(e)
        } finally {
            inputStream?.close()
            outputStream?.close()
        }
    }

    private fun calculateProgress(totalSize:Double,downloadSize:Double):Double{
        return ((downloadSize/totalSize)*100)
    }

    private fun onDownloadProgressUpdate(progress: Double) {
        // Log.d(TAG, "Download progress: $progress")
        progressBar.progress = progress.toInt()
    }

    // Alert Dialogs

    private fun showNetworkErrorAlert() {
        val c = context ?: return

        AlertDialog.Builder(c).apply {
            setTitle("Unable to Download Model")
            setMessage("A network error occurred, check the url and try again.")

            setPositiveButton("OK") { dialog, _ ->
                dialog.dismiss()
            }
        }.show()
    }

    private fun showFileDownloadErrorAlert() {
        val c = context ?: return

        AlertDialog.Builder(c).apply {
            setTitle("Unable to Download Model")
            setMessage("An error occurred while downloading the model, check the url or wait a few moments and try again.")

            setPositiveButton("OK") { dialog, _ ->
                dialog.dismiss()
            }
        }.show()
    }

    private fun showOtherErrorAlert() {
        val c = context ?: return

        AlertDialog.Builder(c).apply {
            setTitle("Unable to Download Model")
            setMessage("An error occurred while downloading the model, check the url or wait a few moments and try again.")

            setPositiveButton("OK") { dialog, _ ->
                dialog.dismiss()
            }
        }.show()
    }

}