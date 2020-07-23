package ai.doc.netrunner.fragments

import ai.doc.netrunner.R
import ai.doc.netrunner.retrofit.NetRunnerService
import ai.doc.netrunner.utilities.ModelManagerUtilities
import ai.doc.netrunner.utilities.unzip
import ai.doc.netrunner.viewmodels.ModelBundlesViewModel
import ai.doc.tensorio.TIOModel.TIOModelBundleValidator
import ai.doc.tensorio.TIOModel.TIOModelBundleValidatorException
import android.app.Dialog
import android.content.Context
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
import androidx.fragment.app.activityViewModels
import kotlinx.coroutines.*

import okhttp3.OkHttpClient
import okhttp3.ResponseBody

import retrofit2.Retrofit
import retrofit2.http.Url
import java.io.*
import java.lang.Exception
import java.net.ConnectException
import java.util.*

private const val TAG = "ImportModelBundleFrag"

class ImportModelBundleFragment : DialogFragment() {

    interface Callbacks {
        fun onModelImported(file: File)
    }

    lateinit var textField: EditText
    lateinit var progressBar: ProgressBar

    private val modelBundlesViewModel by activityViewModels<ModelBundlesViewModel>()

    private var callbacks: Callbacks? = null
    var downloadJob: Job? = null

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

        // Custom click listener to prevent tapping OK and Cancel from dismissing the dialog
        // so that we can perform our own actions first

        d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            if (downloadJob != null) {
                return@setOnClickListener
            }

            downloadJob = CoroutineScope(Dispatchers.Main).launch {
                downloadAndInstallModel(textField.text.toString())
            }
        }

        d.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener {
            CoroutineScope(Dispatchers.Main).launch {
                downloadJob?.cancelAndJoin()
                dialog?.cancel()
            }
        }
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        callbacks = context as Callbacks?
    }

    override fun onDetach() {
        super.onDetach()
        callbacks = null
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

    private suspend fun downloadAndInstallModel(@Url modelUrl: String) {
        progressBar.visibility = View.VISIBLE

        try {
            // Download File

            val filename = "${UUID.randomUUID().toString()}.zip"
            val fileDestination = prepFileDestination(filename)
            val downloadResponse = service.downloadModel(modelUrl)

            saveDownloadToDisk(downloadResponse, fileDestination)

            // Unzip File

            val modelBundleDir = unzip(fileDestination)

            // Validate Model Bundle

            val validator = TIOModelBundleValidator(requireContext(), modelBundleDir)

            validator.validate() {_, json ->
                // Reject models with non-unique identifiers, TODO: and non-unique filenames, currently fails on copy
                !modelBundlesViewModel.modelIds.contains(json.getString("id"))
            }

            // Copy To Models Dir

            val installedModelDir = installModelBundle(modelBundleDir)

            // Inform and Dismiss

            callbacks?.onModelImported(installedModelDir)
            dialog?.dismiss()

        } catch (e: ConnectException) {
            showNetworkErrorAlert()
        } catch (e: IOException) {
            showFileDownloadErrorAlert()
        } catch (e: ArrayIndexOutOfBoundsException) {
            showModelBundleErrorAlert()
        } catch (e: TIOModelBundleValidatorException) {
            showModelBundleErrorAlert()
        } catch (e: FileAlreadyExistsException) {
            showModelBundleErrorAlert()
        } catch (e: CancellationException) {
            // We're good
        } catch (e: Exception) {
            showOtherErrorAlert()
        }  finally {
            progressBar.visibility = View.INVISIBLE
            downloadJob = null
        }
    }

    /** Prepare the download destination: deletes the cache folder and recreates it */

    @Throws(IOException::class)
    private suspend fun prepFileDestination(filename: String): File = withContext(Dispatchers.IO) {
        val downloadsDir = File(requireActivity().cacheDir, "model_downloads")

        if (downloadsDir.exists()) {
            downloadsDir.deleteRecursively()
        }

        if (!downloadsDir.mkdir()) {
            throw IOException()
        }

        return@withContext File(downloadsDir, filename)
    }

    /** Responsible for actually writing the received stream to disk */

    @Throws(IOException::class)
    private suspend fun saveDownloadToDisk(body: ResponseBody, file: File) = withContext(Dispatchers.IO) {
        // False positive warnings about Blocking IO but the Dispatchers.IO exactly addresses that

        var inputStream: InputStream? = null
        var outputStream: FileOutputStream? = null

        try {
            inputStream = body.byteStream()
            outputStream = FileOutputStream(file)

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

    /** Unzips the downloaded file */

    @Throws(IOException::class, ArrayIndexOutOfBoundsException::class)
    private suspend fun unzip(fileDestination: File): File = withContext(Dispatchers.IO) {
        val unzipDir = fileDestination.unzip()
        return@withContext unzipDir.listFiles()[0]
    }

    /** Installs the validated model bundle */

    @Throws(IOException::class)
    private suspend fun installModelBundle(sourceFile: File): File = withContext(Dispatchers.IO) {
        val modelsDir = ModelManagerUtilities.getModelFilesDir(requireContext())
        val modelDestination = File(modelsDir, sourceFile.name)

        sourceFile.copyRecursively(modelDestination) { _, exception ->
            throw exception
        }

        return@withContext modelDestination
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

    private fun showModelBundleErrorAlert() {
        val c = context ?: return

        AlertDialog.Builder(c).apply {
            setTitle("Unable to Validate Model")
            setMessage("Ensure the zip file contains a single .tiobundle folder whose contents have been correctly packaged and whose filename and model identifier are unique.")

            setPositiveButton("OK") { dialog, _ ->
                dialog.dismiss()
            }
        }.show()
    }

}