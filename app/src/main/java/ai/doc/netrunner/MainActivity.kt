package ai.doc.netrunner

import ai.doc.netrunner.view.*
import ai.doc.netrunner.MainViewModel.Tab

import ai.doc.tensorio.TIOModel.TIOModelBundleException
import ai.doc.tensorio.TIOModel.TIOModelBundleManager
import ai.doc.tensorio.TIOModel.TIOModelException
import ai.doc.tensorio.TIOTFLiteModel.TIOTFLiteModel
import android.Manifest
import android.app.Activity
import android.content.Context

import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.provider.MediaStore
import android.view.MenuItem
import android.view.View
import android.widget.*
import android.widget.AdapterView.OnItemSelectedListener

import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.appcompat.widget.Toolbar
import androidx.core.app.ActivityCompat
import androidx.core.content.FileProvider
import androidx.core.content.edit
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.ViewModelProvider

import com.google.android.material.navigation.NavigationView
import java.io.File

import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList

private const val READ_EXTERNAL_STORAGE_REQUEST_CODE = 123
private const val REQUEST_CODE_PICK_IMAGE = 1
private const val REQUEST_IMAGE_CAPTURE = 2

class MainActivity : AppCompatActivity() {

    private val numThreadsOptions = arrayOf(1, 2, 4, 8)

    private val deviceOptions: ArrayList<String> by lazy {
        arrayListOf(
                getString(R.string.cpu),
                getString(R.string.gpu),
                getString(R.string.nnapi)
        ).apply {
            if (isEmulator || !viewModel.modelRunner.canRunOnGPU) {
                remove(getString(R.string.gpu))
            }
        }
    }

    private val viewModel: MainViewModel by lazy {
        ViewModelProvider(this).get(MainViewModel::class.java)
    }

    private val prefs: SharedPreferences by lazy {
        getSharedPreferences("Settings", Context.MODE_PRIVATE)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Acquire Saved Settings

        val selectedModel = prefs.getString(getString(R.string.prefs_selected_model), getString(R.string.prefs_default_selected_model))
        val device = prefs.getString(getString(R.string.prefs_run_on_device), getString(R.string.prefs_default_device))
        val numThreads = prefs.getInt(getString(R.string.prefs_num_threads), 1)
        val use16Bit = prefs.getBoolean(getString(R.string.prefs_use_16_bit), false)

        // Load the Model

        viewModel.manager = TIOModelBundleManager(applicationContext, "")

        try {
            val bundle = viewModel.manager.bundleWithId(selectedModel)
            val model = bundle.newModel()
            model.load()

            val modelRunner = ModelRunner((model as TIOTFLiteModel))
            viewModel.modelRunner = modelRunner
            viewModel.modelRunner.device = ModelRunner.deviceFromString(device)
            viewModel.modelRunner.numThreads = numThreads
            viewModel.modelRunner.use16Bit = use16Bit
        } catch (e: IOException) {
            e.printStackTrace()
        } catch (e: TIOModelException) {
            e.printStackTrace()
        } catch (e: TIOModelBundleException) {
            e.printStackTrace()
        }

        // UI

        setupInputSourceButton()
        setupDrawer()

        setupFragment(viewModel.currentTab)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (resultCode != Activity.RESULT_OK) {
            return
        }

        when (requestCode) {
            REQUEST_CODE_PICK_IMAGE ->
                showImageResults(data)
            REQUEST_IMAGE_CAPTURE -> {
                revokeCameraActivityPermissions(takePhotoUri)
                showTakenPhotoResults()
            }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> {
                (findViewById<View>(R.id.drawer_layout) as DrawerLayout).openDrawer(GravityCompat.START)
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    private fun setupInputSourceButton() {
        val button = findViewById<ImageButton>(R.id.actionbar_camera_button)
        val items = arrayOf(
                getString(R.string.input_source_dialog_choice_live_video),
                getString(R.string.input_source_dialog_take_picture),
                getString(R.string.input_source_dialog_choose_photo))

        button.setOnClickListener {
            AlertDialog.Builder(this).apply {
                setTitle(R.string.input_source_dialog_title)
                //setIcon(android.R.drawable.ic_menu_camera)

                setNegativeButton(android.R.string.cancel) { dialog, which ->
                    dialog.cancel()
                }

                setItems(items) { dialog, which ->
                    dialog.dismiss()
                    when (which) {
                        0 -> changeTab(Tab.LiveVideo)
                        1 -> takePhoto()
                        2 -> pickImage()
                    }
                }
            }.show()
        }
    }

    private fun setupDrawer() {

        // Saved Preferences

        val selectedModel = prefs.getString(getString(R.string.prefs_selected_model), getString(R.string.prefs_default_selected_model))
        val device = prefs.getString(getString(R.string.prefs_run_on_device), getString(R.string.prefs_default_device))
        val numThreads = prefs.getInt(getString(R.string.prefs_num_threads), 0)
        val use16Bit = prefs.getBoolean(getString(R.string.prefs_use_16_bit), false)

        // Setup

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)

        val actionbar = supportActionBar
        if (actionbar != null) {
            actionbar.setDisplayHomeAsUpEnabled(true)
            actionbar.setHomeAsUpIndicator(R.drawable.ic_menu_black_24dp)
        }

        val nav = findViewById<NavigationView>(R.id.nav_view)

        val deviceSpinner = nav.menu.findItem(R.id.nav_select_accelerator).actionView.findViewById(R.id.spinner) as Spinner
        val modelSpinner = nav.menu.findItem(R.id.nav_select_model).actionView.findViewById(R.id.spinner) as Spinner
        val threadsSpinner = nav.menu.findItem(R.id.nav_select_threads).actionView.findViewById(R.id.spinner) as Spinner
        val precisionSwitch = nav.menu.findItem(R.id.nav_switch_precision).actionView as SwitchCompat

        // Device Selection

        (nav.menu.findItem(R.id.nav_select_accelerator).actionView.findViewById<View>(R.id.menu_title) as TextView).setText(R.string.device_menu_item_title)

        deviceSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, deviceOptions)
        deviceSpinner.setSelection(deviceOptions.indexOf(device), false)

        deviceSpinner.onItemSelectedListener = object: OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) {
            }
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val device = deviceOptions[position]
                prefs.edit(true) { putString(getString(R.string.prefs_run_on_device), device) }
                viewModel.modelRunner.device = ModelRunner.deviceFromString(device)
            }
        }

        // Model Selection

        (nav.menu.findItem(R.id.nav_select_model).actionView.findViewById<View>(R.id.menu_title) as TextView).setText(R.string.model_menu_item_title)

        modelSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, viewModel.modelIds)
        modelSpinner.setSelection(viewModel.modelIds.indexOf(selectedModel), false)

        modelSpinner.onItemSelectedListener = object: OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) {
            }
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val modelId = viewModel.modelIds[position]
                val bundle = viewModel.manager.bundleWithId(modelId)
                try {
                    val model = bundle.newModel() as TIOTFLiteModel
                    prefs.edit(true) { putString(getString(R.string.prefs_selected_model), modelId) }
                    viewModel.modelRunner.switchModel(model)
                } catch (e: TIOModelBundleException) {
                    e.printStackTrace()
                } catch (e: TIOModelException) {
                    e.printStackTrace()
                }
            }
        }

        // Thread Count Selection

        (nav.menu.findItem(R.id.nav_select_threads).actionView.findViewById<View>(R.id.menu_title) as TextView).setText(R.string.threads_menu_item_title)

        threadsSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, numThreadsOptions)
        threadsSpinner.setSelection(numThreadsOptions.indexOf(numThreads), false)

        threadsSpinner.onItemSelectedListener = object: OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) {
            }
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val threads = numThreadsOptions[position]
                prefs.edit(true) { putInt(getString(R.string.prefs_num_threads), threads) }
                viewModel.modelRunner.numThreads = threads
            }
        }

        // 16 Bit Checkbox

        precisionSwitch.isChecked = use16Bit

        precisionSwitch.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit(true) { putBoolean(getString(R.string.prefs_use_16_bit), isChecked) }
            viewModel.modelRunner.use16Bit = isChecked
        }
    }

    //region Permissions

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == READ_EXTERNAL_STORAGE_REQUEST_CODE) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
                // Delays is necessary or app freezes with onActivityResult never called
                Handler().postDelayed({
                    pickImage()
                }, 100)
            }
        }
    }

    //endRegion

    //region Image Picker

    private fun pickImage() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), READ_EXTERNAL_STORAGE_REQUEST_CODE)
        } else {
            val intent = Intent(Intent.ACTION_PICK)
            intent.type = "image/*"
            startActivityForResult(intent, REQUEST_CODE_PICK_IMAGE)
        }
    }

    private fun showImageResults(data: Intent?) {
        val image = data?.data ?: return

        val filePath = arrayOf(MediaStore.Images.Media.DATA)

        this.contentResolver.query(image, filePath, null, null, null)?.let {cursor ->
            cursor.moveToFirst()

            val imagePath = cursor.getString(cursor.getColumnIndex(filePath[0]))
            val options = BitmapFactory.Options().apply {
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }

            viewModel.bitmap = BitmapFactory.decodeFile(imagePath, options)

            changeTab(Tab.SinglePhoto)
            cursor.close()
        }
    }

    //region Take Photo

    // Really wish we could wrap this up into one method and lambdas rather than spreading it out

    private var takePhotoUri: Uri? = null

    private fun takePhoto() {
         Intent(MediaStore.ACTION_IMAGE_CAPTURE).also { takePictureIntent ->
            takePictureIntent.resolveActivity(packageManager)?.also {

                val file: File? = try  {
                    createTempTakenPhotoFile()
                } catch (x: IOException) {
                    // TODO: Show error
                    null
                }

                file?.also {
                    takePhotoUri = FileProvider.getUriForFile(this, "ai.doc.netrunner.fileprovider", file)

                    grantCameraActivityPermissions(takePictureIntent, takePhotoUri)
                    takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, takePhotoUri)
                    startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE)
                }
            }
        }
    }

    private fun grantCameraActivityPermissions(intent: Intent, photoUri: Uri?) {
        val cameraActivities: List<ResolveInfo> = packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
        for (cameraActivity in cameraActivities) {
            this.grantUriPermission(cameraActivity.activityInfo.packageName, photoUri, Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        }
    }

    private fun revokeCameraActivityPermissions(photoUri: Uri?) {
        revokeUriPermission(photoUri, Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
    }

    @Throws(IOException::class)
    private fun createTempTakenPhotoFile(): File {
        val timestamp: String = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        return File.createTempFile(timestamp,".jpg", filesDir)
    }

    private fun showTakenPhotoResults() {
        viewModel.bitmap = MediaStore.Images.Media.getBitmap(contentResolver, takePhotoUri)
        changeTab(Tab.SinglePhoto)
    }

    //endRegion

    //region Fragment Management

    private fun changeTab(tab: Tab) {
        if ( viewModel.currentTab == Tab.LiveVideo && viewModel.currentTab == tab) {
            return
        }

        viewModel.currentTab = tab
        setupFragment(tab)
    }

    private fun setupFragment(tab: Tab) {
        viewModel.modelRunner.stopStreamingInference()

        val fragment = when (tab) {
            Tab.LiveVideo -> LiveCameraClassificationFragment()
            Tab.SinglePhoto -> SingleImageClassificationFragment()
        }

        supportFragmentManager.beginTransaction().replace(R.id.container, fragment).commit()
    }

    //endRegion

    //region Utilities

    private val isEmulator: Boolean
        get() = (Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic")
                || Build.FINGERPRINT.startsWith("generic")
                || Build.FINGERPRINT.startsWith("unknown")
                || Build.HARDWARE.contains("goldfish")
                || Build.HARDWARE.contains("ranchu")
                || Build.MODEL.contains("google_sdk")
                || Build.MODEL.contains("Emulator")
                || Build.MODEL.contains("Android SDK built for x86")
                || Build.MANUFACTURER.contains("Genymotion")
                || Build.PRODUCT.contains("sdk_google")
                || Build.PRODUCT.contains("google_sdk")
                || Build.PRODUCT.contains("sdk")
                || Build.PRODUCT.contains("sdk_x86")
                || Build.PRODUCT.contains("vbox86p")
                || Build.PRODUCT.contains("emulator")
                || Build.PRODUCT.contains("simulator"))

    //endRegion
}