package ai.doc.netrunner

import ai.doc.netrunner.view.*
import ai.doc.netrunner.MainViewModel.Tab
import ai.doc.netrunner.outputhandler.OutputHandlerManager

import ai.doc.tensorio.TIOModel.TIOModelBundleManager
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
import android.os.Looper
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
import androidx.fragment.app.Fragment
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

private const val SET_PRGM = "programmatic"

class MainActivity : AppCompatActivity() {

    private val numThreadsOptions = arrayOf(1, 2, 4, 8)

    private val deviceOptions: ArrayList<String> by lazy {
        arrayListOf(getString(R.string.cpu), getString(R.string.gpu), getString(R.string.nnapi)).apply {
            if (isEmulator || !viewModel.modelRunner.canRunOnGPU) {
                remove(getString(R.string.gpu))
            }
            if (isEmulator || !viewModel.modelRunner.canRunOnNnApi) {
                remove(getString(R.string.nnapi))
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

        // Register Model Output Formatters

        OutputHandlerManager.registerHandlers()

        // Init Model and Model Runner

        initModelRunner()

        // UI

        setupInputSourceButton()
        setupDrawer()

        setupFragment(viewModel.currentTab)
    }

    /** Initializes the model runner and falls back on default model if there are problems */

    private fun initModelRunner() {

        // Acquire Saved Settings

        val selectedModel = prefs.getString(getString(R.string.prefs_selected_model), getString(R.string.prefs_default_selected_model))!!
        val device = prefs.getString(getString(R.string.prefs_run_on_device), getString(R.string.prefs_default_device))!!
        val numThreads = prefs.getInt(getString(R.string.prefs_num_threads), 1)
        val use16Bit = prefs.getBoolean(getString(R.string.prefs_use_16_bit), false)

        // Load the Model

        viewModel.manager = TIOModelBundleManager(applicationContext, "")

        try {
            val bundle = viewModel.manager.bundleWithId(selectedModel)
            val model = bundle.newModel()

            val modelRunner = ModelRunner((model as TIOTFLiteModel), modelRunnerExceptionHandler)

            viewModel.modelRunner = modelRunner
            viewModel.modelRunner.setDevice(ModelRunner.deviceFromString(device))
            viewModel.modelRunner.setNumThreads(numThreads)
            viewModel.modelRunner.setUse16Bit(use16Bit)

            model.load()
        } catch(e: Exception) {

            AlertDialog.Builder(this).apply {
                setTitle(R.string.modelrunner_initfail_dialog_title)
                setMessage(R.string.modelrunner_initfail_dialog_message)

                setPositiveButton("OK") { dialog, _ ->
                    dialog.dismiss()
                }
            }.show()

            resetSettings()
            initModelRunner()
        }
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

    /** Called when action bar drawer button is tapped */

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> {
                (findViewById<View>(R.id.drawer_layout) as DrawerLayout).openDrawer(GravityCompat.START)
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    /** Catches uncaught exceptions on the Model Runner background thread */

    private val modelRunnerExceptionHandler: Thread.UncaughtExceptionHandler by lazy {
        Thread.UncaughtExceptionHandler() { _, exception ->
            Handler(Looper.getMainLooper()).post(Runnable {

                // An inference exception is serious enough that we must reset everything

                if (exception is ModelRunner.ModelInferenceException) {
                    viewModel.modelRunner.model.unload()
                    resetSettings()
                    initModelRunner()
                }

                // Show Alert

                val title: Int = when (exception) {
                    is ModelRunner.ModelLoadingException -> R.string.modelrunner_exception_dialog_title
                    is ModelRunner.ModelInferenceException -> R.string.modelrunner_exception_run_inference_dialog_title
                    is ModelRunner.GPUUnavailableException -> R.string.modelrunner_exception_dialog_title
                    else -> R.string.modelrunner_exception_unknown_dialog_title
                }

                val message: Int = when (exception) {
                    is ModelRunner.ModelLoadingException -> R.string.modelrunner_exception_load_model
                    is ModelRunner.ModelInferenceException -> R.string.modelrunner_exception_run_inference
                    is ModelRunner.GPUUnavailableException -> R.string.modelrunner_exeption_gpu
                    else -> R.string.modelrunner_exception_unknown
                }

                AlertDialog.Builder(this).apply {
                    setTitle(title)
                    setMessage(message)

                    setPositiveButton("OK") { dialog, _ ->
                        dialog.dismiss()
                    }
                }.show()

                // Reset UI to Previous Settings

                resetSettingsUI()

                // Restart Model Runner

                viewModel.modelRunner.reset()
                child<ModelRunnerWatcher>(R.id.container)?.startRunning()
            })
        }
    }

    /** Reset to default settings */

    private fun resetSettings() {
        prefs.edit(true) {
            putString(getString(R.string.prefs_selected_model), getString(R.string.prefs_default_selected_model))
            putString(getString(R.string.prefs_run_on_device), getString(R.string.prefs_default_device))
            putInt(getString(R.string.prefs_num_threads), 1)
            putBoolean(getString(R.string.prefs_use_16_bit), false)
        }
    }

    /** Update UI to reflect change in settings, needed after fall back when exception occurs on model runner thread */

    private fun resetSettingsUI() {

        val selectedModel = prefs.getString(getString(R.string.prefs_selected_model), getString(R.string.prefs_default_selected_model))!!
        val device = prefs.getString(getString(R.string.prefs_run_on_device), getString(R.string.prefs_default_device))!!
        val numThreads = prefs.getInt(getString(R.string.prefs_num_threads), 0)
        val use16Bit = prefs.getBoolean(getString(R.string.prefs_use_16_bit), false)

        val nav = findViewById<NavigationView>(R.id.nav_view)

        val deviceSpinner = nav.menu.findItem(R.id.nav_select_accelerator).actionView.findViewById(R.id.spinner) as Spinner
        val modelSpinner = nav.menu.findItem(R.id.nav_select_model).actionView.findViewById(R.id.spinner) as Spinner
        val threadsSpinner = nav.menu.findItem(R.id.nav_select_threads).actionView.findViewById(R.id.spinner) as Spinner
        val precisionSwitch = nav.menu.findItem(R.id.nav_switch_precision).actionView as SwitchCompat

        // Tag is set and checked in handlers to avoid programmatic triggering of action

        deviceSpinner.tag = SET_PRGM
        deviceSpinner.setSelection(deviceOptions.indexOf(device), false)

        modelSpinner.tag = SET_PRGM
        modelSpinner.setSelection(viewModel.modelIds.indexOf(selectedModel), false)

        threadsSpinner.tag = SET_PRGM
        threadsSpinner.setSelection(numThreadsOptions.indexOf(numThreads), false)

        precisionSwitch.tag = SET_PRGM
        precisionSwitch.isChecked = use16Bit
    }

    /** Actionbar button shows dialog allowing user to choose input source */

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

    /** The drawer contains all the configuration settings available to the user */

    private fun setupDrawer() {
        val context = this

        // Saved Preferences

        val selectedModel = prefs.getString(getString(R.string.prefs_selected_model), getString(R.string.prefs_default_selected_model))!!
        val device = prefs.getString(getString(R.string.prefs_run_on_device), getString(R.string.prefs_default_device))!!
        val numThreads = prefs.getInt(getString(R.string.prefs_num_threads), 0)
        val use16Bit = prefs.getBoolean(getString(R.string.prefs_use_16_bit), false)

        // Toolbar and Nav

        setSupportActionBar(findViewById<Toolbar>(R.id.toolbar))

        supportActionBar?.let { actionbar ->
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
                if (deviceSpinner.tag == SET_PRGM) {
                    deviceSpinner.tag = null
                    return
                }

                val selectedDevice = deviceOptions[position]
                viewModel.modelRunner.setDevice(ModelRunner.deviceFromString(selectedDevice)) {
                    prefs.edit(true) { putString(getString(R.string.prefs_run_on_device), selectedDevice) }
                }
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
                if (modelSpinner.tag == SET_PRGM) {
                    modelSpinner.tag = null
                    return
                }

                val selectedModelId = viewModel.modelIds[position]
                val selectedBundle = viewModel.manager.bundleWithId(selectedModelId)

                child<ModelRunnerWatcher>(R.id.container)?.stopRunning()

                try {
                    val model = selectedBundle.newModel() as TIOTFLiteModel

                    viewModel.modelRunner.switchModel(model) {
                        prefs.edit(true) { putString(getString(R.string.prefs_selected_model), selectedModelId) }
                        child<ModelRunnerWatcher>(R.id.container)?.let {
                            it.modelDidChange()
                            it.startRunning()
                        }
                    }
                } catch (e: Exception) {
                    AlertDialog.Builder(context).apply {
                        setTitle(R.string.unable_to_load_model_dialog_title)
                        setMessage(R.string.unable_to_load_model_dialog_message)

                        setPositiveButton("OK") { dialog, _ ->
                            dialog.dismiss()
                        }
                    }.show()
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
                if (threadsSpinner.tag == SET_PRGM) {
                    threadsSpinner.tag = null
                    return
                }

                val selectedThreads = numThreadsOptions[position]

                viewModel.modelRunner.setNumThreads(selectedThreads) {
                    prefs.edit(true) { putInt(getString(R.string.prefs_num_threads), selectedThreads) }
                }
            }
        }

        // 16 Bit Checkbox

        precisionSwitch.isChecked = use16Bit

        precisionSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (precisionSwitch.tag == SET_PRGM) {
                precisionSwitch.tag = null
                return@setOnCheckedChangeListener
            }

            viewModel.modelRunner.setUse16Bit(isChecked) {
                prefs.edit(true) { putBoolean(getString(R.string.prefs_use_16_bit), isChecked) }
            }
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

    //endregion

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

    //endregion

    //region Fragment Management

    /** Setup new tab unless we're on video and requesting video */

    private fun changeTab(tab: Tab) {
        if ( viewModel.currentTab == Tab.LiveVideo && viewModel.currentTab == tab) {
            return
        }

        viewModel.currentTab = tab
        setupFragment(tab)
    }

    private fun setupFragment(tab: Tab) {
        val fragment: Fragment = when (tab) {
            Tab.LiveVideo -> LiveCameraClassificationFragment()
            Tab.SinglePhoto -> SingleImageClassificationFragment()
        }

        supportFragmentManager.beginTransaction().replace(R.id.container, fragment).commit()
    }

    //endregion

    //region Utilities

    private fun <T>child(id: Int): T? {
        @Suppress("UNCHECKED_CAST")
        return supportFragmentManager.findFragmentById(id) as? T
    }

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

    //endregion
}