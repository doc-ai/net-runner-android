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
import android.widget.ImageView

import androidx.core.app.ActivityCompat
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModelProviders

private const val READ_EXTERNAL_STORAGE_REQUEST_CODE = 123
private const val RESULTS_TO_SHOW = 3

// TODO: Use selects image and *then* this fragment is shown

class SingleImageClassificationFragment : Fragment() {

    // UI

    private lateinit var imageView: ImageView

    // Live Data Variables

    private val _latency = MutableLiveData<String>()
    val latency: LiveData<String> = _latency

    private val _predictions = MutableLiveData<String>()
    val predictions: LiveData<String> = _predictions

    // View Model

    // requires fragment-ktx dependency
    // val viewModel: ClassificationViewModel by activityViewModels()

    private val viewModel: ClassificationViewModel by lazy {
        ViewModelProviders.of(requireActivity()).get(ClassificationViewModel::class.java)
    }

    private var selected: Bitmap? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        // Inflate the layout for this fragment
        val binding: FragmentSingleImageBinding = DataBindingUtil.inflate(inflater, R.layout.fragment_single_image, container, false)
        binding.fragment = this
        binding.lifecycleOwner = this

        val root = binding.root
        imageView = root.findViewById(R.id.imageview)

        return root
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (resultCode != Activity.RESULT_OK) {
            return
        }
        if (requestCode != 1) {
            return
        }

        val pickedImage = data?.data
        // Let's read picked image path using content resolver
        val filePath = arrayOf(MediaStore.Images.Media.DATA)
        val cursor = activity!!.contentResolver.query(pickedImage, filePath, null, null, null)
        cursor.moveToFirst()

        val imagePath = cursor.getString(cursor.getColumnIndex(filePath[0]))
        val options = BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }

        val bitmap = BitmapFactory.decodeFile(imagePath, options)
        imageView.setImageBitmap(bitmap)

        viewModel.modelRunner.runInferenceOnFrame( {
            bitmap
        }, { output: Map<String,Any>, l: Long ->
            val classification = output["classification"] as? Map<String, Float>
            val top5 = TIOClassificationHelper.topN(classification, RESULTS_TO_SHOW)
            val top5formatted = formattedResults(top5)

            _predictions.postValue(top5formatted)
            _latency.postValue("$l ms")
        })
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == READ_EXTERNAL_STORAGE_REQUEST_CODE) {
            if (ActivityCompat.checkSelfPermission(activity!!, permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
                pickImage()
            }
        }
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
}