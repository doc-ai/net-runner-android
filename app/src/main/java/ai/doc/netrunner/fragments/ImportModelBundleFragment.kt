package ai.doc.netrunner.fragments

import ai.doc.netrunner.R
import ai.doc.netrunner.retrofit.NetRunnerService
import android.app.AlertDialog
import android.app.Dialog
import android.os.AsyncTask
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.EditText
import android.widget.ProgressBar
import androidx.fragment.app.DialogFragment

import okhttp3.OkHttpClient
import okhttp3.ResponseBody

import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.http.Url
import java.io.*
import java.lang.ref.WeakReference
import java.util.*

private const val TAG = "ImportModelBundleFrag"

// TODO: Unzip, validate, move to filesDir/models, update view model

class ImportModelBundleFragment : DialogFragment() {

    lateinit var textField: EditText
    lateinit var progressBar: ProgressBar

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return AlertDialog.Builder(activity).apply {
            setTitle("Import Model")

            val inflater = activity?.layoutInflater
            val view = inflater?.inflate(R.layout.fragment_import_model_bundle, null, false)

            view?.apply {
                progressBar = findViewById(R.id.progress_bar)
                textField = findViewById(R.id.url_text)
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

        d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            downloadModel(textField.text.toString())
        }
    }

    /** Downloads the zip file, http://localhost is overwritten by full url */

    private fun downloadModel(@Url modelUrl: String) {
        progressBar.visibility = View.VISIBLE

        val service = Retrofit.Builder()
                .baseUrl("http://localhost")
                .client(OkHttpClient.Builder().build())
                .build()
                .create(NetRunnerService::class.java)

        val call = service.downloadModel(modelUrl)

        call.enqueue(object: Callback<ResponseBody> {
            override fun onFailure(call: Call<ResponseBody>?, t: Throwable?) {
                Log.e(TAG, t?.message)
                progressBar.visibility = View.INVISIBLE
                // TODO: Show failure
            }
            override fun onResponse(call: Call<ResponseBody>?, response: Response<ResponseBody>?) {
                Log.d(TAG, "Got response")
                DownloadModelZipFileTask(this@ImportModelBundleFragment).execute(response?.body())
            }
        })
    }

    private class DownloadModelZipFileTask(context: ImportModelBundleFragment) : AsyncTask<ResponseBody, Double, String>() {

        private val fragmentReference: WeakReference<ImportModelBundleFragment> = WeakReference(context)

        override fun doInBackground(vararg params: ResponseBody?): String {
            val body = params[0] ?: return ""
            saveToDisk(body, "${UUID.randomUUID().toString()}.zip")
            return ""
        }

        override fun onProgressUpdate(vararg values: Double?) {
            val progress = values[0] ?: return
            Log.d(TAG, "Download progress: $progress")
            fragmentReference.get()?.progressBar?.progress = progress.toInt()
        }

        private fun saveToDisk(body: ResponseBody, filename: String) {
            val myActivity = fragmentReference.get()?.activity ?: return

            var inputStream: InputStream? = null
            var outputStream: FileOutputStream? = null

            try {
                val destination = File(myActivity.cacheDir, filename)

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

                    outputStream?.write(fileReader, 0, read)
                    fileSizeDownloaded += read.toLong()

                    val progress = calculateProgress(fileSize.toDouble(), fileSizeDownloaded.toDouble())
                    publishProgress(progress)
                }

                outputStream?.flush();
                publishProgress(100.0)

            } catch (e: IOException) {
                Log.e(TAG, "Failed to save the file!");
                // TODO: Show error
            } finally {
                inputStream?.close()
                outputStream?.close()
            }
        }

        private fun calculateProgress(totalSize:Double,downloadSize:Double):Double{
            return ((downloadSize/totalSize)*100)
        }
    }

}