package ai.doc.netrunner.view

import ai.doc.netrunner.R
import ai.doc.netrunner.databinding.FragmentSingleImageBinding
import ai.doc.tensorio.TIOUtilities.TIOClassificationHelper
import android.Manifest.permission
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import androidx.core.app.ActivityCompat
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModelProviders

class SingleImageClassificationFragment : Fragment() {
    private var imageView: ImageView? = null
    private var selected: Bitmap? = null
    private var btnClassify: Button? = null
    private val latency = MutableLiveData<String>()
    private val predictions = MutableLiveData<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        // Inflate the layout for this fragment
        val binding: FragmentSingleImageBinding = DataBindingUtil.inflate(inflater, R.layout.fragment_single_image, container, false)
        binding.fragment = this
        binding.lifecycleOwner = this
        val root = binding.root
        imageView = root.findViewById(R.id.imageview)
        btnClassify = root.findViewById(R.id.btn_classify)
        return root
    }

    fun pickImage() {
        if (ActivityCompat.checkSelfPermission(activity!!, permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                    activity!!, arrayOf(permission.READ_EXTERNAL_STORAGE),
                    READ_EXTERNAL_STORAGE_REQUEST_CODE
            )
        } else {
            val intent = Intent(Intent.ACTION_PICK)
            intent.type = "image/*"
            startActivityForResult(intent, 1)
        }
    }

    fun classify() {
        val vm = ViewModelProviders.of(activity!!).get(ClassificationViewModel::class.java)
        val modelRunner = vm.modelRunner
        val small = Bitmap.createScaledBitmap(selected, modelRunner!!.inputWidth, modelRunner.inputHeight, false)

        modelRunner.classifyFrame(0, small) { requestId: Int, output: Any, l: Long ->
            val classification = (output as Map<String?, Any?>)["classification"] as Map<String, Float>?
            val top5 = TIOClassificationHelper.topN(classification, RESULTS_TO_SHOW)
            val top5formatted = formattedResults(top5)
            predictions.postValue(top5formatted)
            latency.postValue("$l ms")
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent) {
        if (resultCode != Activity.RESULT_OK) {
            return
        }
        if (requestCode == 1) {
            val pickedImage = data.data
            // Let's read picked image path using content resolver
            val filePath = arrayOf(MediaStore.Images.Media.DATA)
            val cursor = activity!!.contentResolver.query(pickedImage, filePath, null, null, null)
            cursor.moveToFirst()
            val imagePath = cursor.getString(cursor.getColumnIndex(filePath[0]))
            val options = BitmapFactory.Options()
            options.inPreferredConfig = Bitmap.Config.ARGB_8888
            selected = BitmapFactory.decodeFile(imagePath, options)
            imageView!!.setImageBitmap(selected)
            btnClassify!!.isEnabled = true
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == READ_EXTERNAL_STORAGE_REQUEST_CODE) {
            if (ActivityCompat.checkSelfPermission(activity!!, permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
                pickImage()
            }
        }
    }

    private fun formattedResults(results: List<Map.Entry<String, Float>>): String {
        val b = StringBuilder()
        for ((key, value) in results) {
            b.append(key)
            b.append(": ")
            b.append(String.format("%.2f", value))
            b.append("\n")
        }
        b.setLength(b.length - 1)
        return b.toString()
    }

    fun getLatency(): LiveData<String> {
        return latency
    }

    fun getPredictions(): LiveData<String> {
        return predictions
    }

    companion object {
        private const val READ_EXTERNAL_STORAGE_REQUEST_CODE = 123
        private const val RESULTS_TO_SHOW = 3
    }
}