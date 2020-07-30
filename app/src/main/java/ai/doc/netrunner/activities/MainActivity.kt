package ai.doc.netrunner.activities

import ai.doc.netrunner.*
import ai.doc.netrunner.fragments.*
import ai.doc.netrunner.viewmodels.MainViewModel.Tab
import ai.doc.netrunner.outputhandler.OutputHandlerManager
import ai.doc.netrunner.utilities.*
import ai.doc.netrunner.viewmodels.MainViewModel
import ai.doc.netrunner.viewmodels.ModelBundlesViewModel
import ai.doc.tensorio.core.modelbundle.ModelBundle
import ai.doc.tensorio.core.modelbundle.ModelBundlesManager
import ai.doc.tensorio.tflite.model.TFLiteModel

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.provider.MediaStore
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.*
import android.widget.AdapterView.OnItemSelectedListener
import androidx.annotation.LayoutRes

import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.appcompat.widget.Toolbar
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

private const val READ_EXTERNAL_STORAGE_PERMISSIONS_REQUEST = 1001
private const val LIVE_CAMERA_PERMISSIONS_REQUEST = 2001
private const val TAKE_PHOTO_PERMISSIONS_REQUEST = 3001

private const val PICK_IMAGE_ACTIVITY = 1
private const val IMAGE_CAPTURE_ACTIVITY = 2
private const val MODEL_MANAGER_ACTIVITY = 3

class MainActivity : AppCompatActivity(), WelcomeFragment.Callbacks {

    private class ModelBundleArrayAdapter(context: Context, @LayoutRes resource: Int, list: List<ModelBundle>) : ArrayAdapter<ModelBundle>(context, resource, list) {

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = super.getView(position, convertView, parent) as TextView
            view.text = getItem(position)!!.name
            return view
        }

        override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
            return getView(position, convertView, parent)
        }
    }

    private val numThreadsOptions = arrayOf(1, 2, 4, 8)

    private val deviceOptions: ArrayList<String> by lazy {
        arrayListOf(getString(R.string.cpu), getString(R.string.gpu), getString(R.string.nnapi)).apply {
            if (DeviceUtilities.isEmulator || !viewModel.modelRunner.canRunOnGPU) {
                remove(getString(R.string.gpu))
            }
            if (DeviceUtilities.isEmulator || !viewModel.modelRunner.canRunOnNnApi) {
                remove(getString(R.string.nnapi))
            }
        }
    }

    private val viewModel: MainViewModel by lazy {
        ViewModelProvider(this).get(MainViewModel::class.java)
    }

    private val modelBundlesViewModel: ModelBundlesViewModel by lazy {
        ViewModelProvider(this).get(ModelBundlesViewModel::class.java)
    }

    private val prefs: SharedPreferences by lazy {
        getSharedPreferences("Settings", Context.MODE_PRIVATE)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        OutputHandlerManager.registerHandlers()
        initModelRunner()

        setupInputSourceButton()
        setupDrawer()

        setupWelcome(!alreadyWelcomed)
        restoreState(savedInstanceState)
    }

    /** Initializes the model runner and falls back on default model if there are problems */

    private fun initModelRunner() {

        // Acquire Saved Settings

        val selectedModel = prefs.getString(getString(R.string.prefs_selected_model), getString(R.string.prefs_default_selected_model))!!
        val device = prefs.getString(getString(R.string.prefs_run_on_device), getString(R.string.prefs_default_device))!!
        val numThreads = prefs.getInt(getString(R.string.prefs_num_threads), 1)
        val use16Bit = prefs.getBoolean(getString(R.string.prefs_use_16_bit), false)

        // Load the Model

        modelBundlesViewModel.setBundleManagers(
                ModelBundlesManager.managerWithAssets(applicationContext, ""),
                ModelBundlesManager.managerWithFiles(ModelManagerUtilities.getModelFilesDir(this)))

        try {
            val bundle = modelBundlesViewModel.bundleWithId(selectedModel)
            val model = bundle.newModel()

            val modelRunner = ModelRunner((model as TFLiteModel), modelRunnerExceptionHandler)

            viewModel.modelRunner = modelRunner
            viewModel.modelRunner.device = ModelRunner.deviceFromString(device)
            viewModel.modelRunner.numThreads = numThreads
            viewModel.modelRunner.use16Bit = use16Bit

            model.load()
        } catch(e: Exception) {
            alertInitModelRunnerException()
            resetSettings()
            initModelRunner()
        }
    }

    private fun restoreState(savedInstanceState: Bundle?) {
        // Cold start: (savedInstanceState==null) Must install the current tab
        // Warm start: (savedInstanceState==val ) UI recreated by OS

        // If camera permissions have changed in the meantime, regardless the start,
        // then relaunch welcome even if already shown

        if (!PermissionsManager.hasCameraPermissions(this)) {
            setupWelcome(true)
            setupFragment(viewModel.currentTab)
        } else  if (savedInstanceState == null) {
            setupFragment(viewModel.currentTab)
        }
    }

    /** Activities requested include: photo gallery, camera */

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (resultCode != Activity.RESULT_OK) {
            return
        }

        when (requestCode) {
            PICK_IMAGE_ACTIVITY ->
                showImageResults(data)
            IMAGE_CAPTURE_ACTIVITY -> {
                revokeCameraActivityPermissions(takePhotoUri)
                showTakenPhotoResults()
            }
            MODEL_MANAGER_ACTIVITY -> {
                if (data?.getBooleanExtra(MODEL_MANAGER_DID_UPDATE_MODELS, false) == true) {
                    reloadModelBundles()
                }
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

    /**
     * Catches the uncaught inference exception on the Model Runner background thread
     *
     * When inference fails: unload the model, let the user know, and reset the model runner
     * An orientation change or tapping pause|play may restart inference, in which case this
     * exception handler just catches the exception again
     */

    private val modelRunnerExceptionHandler: Thread.UncaughtExceptionHandler by lazy {
        Thread.UncaughtExceptionHandler() { _, _ ->
            HandlerUtilities.main(Runnable {
                 viewModel.modelRunner.model.unload()
                alertInferenceException()
                viewModel.modelRunner.reset()
            })
        }
    }

    /** Called when the Model Manager downloads or deletes a model */

    private fun reloadModelBundles() {

        // Reload model managers

        modelBundlesViewModel.reloadManagers()

        // Refresh model spinner

        val nav = findViewById<NavigationView>(R.id.nav_view)
        val modelSpinner = nav.menu.findItem(R.id.nav_select_model).actionView.findViewById(R.id.spinner) as Spinner
        val adapter = modelSpinner.adapter as? ModelBundleArrayAdapter

        adapter?.clear()
        adapter?.addAll(modelBundlesViewModel.modelBundles)
        adapter?.notifyDataSetChanged()

        // Fall back to default model if current model was deleted

        val previousModel = viewModel.modelRunner.model.bundle
        val defaultModelId = getString(R.string.prefs_default_selected_model)

        if (!modelBundlesViewModel.modelIds.contains(previousModel.identifier)) {
            modelSpinner.setSelection(modelBundlesViewModel.modelBundles.indexOf(modelBundlesViewModel.bundleWithId(defaultModelId)), false)
            prefs.edit(true) {
                putString(getString(R.string.prefs_selected_model), defaultModelId)
            }
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

                setNegativeButton(android.R.string.cancel) { dialog, which ->
                    dialog.cancel()
                }

                setItems(items) { dialog, which ->
                    dialog.dismiss()
                    when (which) {
                        0 -> runLiveVideo()
                        1 -> takePhoto()
                        2 -> pickImage()
                    }
                }
            }.show()
        }
    }

    /** The drawer contains all the configuration settings available to the user */

    private fun setupDrawer() {

        // Saved Preferences

        val selectedModel = prefs.getString(getString(R.string.prefs_selected_model), getString(R.string.prefs_default_selected_model))!!
        val device = prefs.getString(getString(R.string.prefs_run_on_device), getString(R.string.prefs_default_device))!!
        val numThreads = prefs.getInt(getString(R.string.prefs_num_threads), 0)
        val use16Bit = prefs.getBoolean(getString(R.string.prefs_use_16_bit), false)

        // Toolbar and Nav

        setSupportActionBar(findViewById<Toolbar>(R.id.toolbar))

        supportActionBar?.let { actionbar ->
            actionbar.setHomeAsUpIndicator(R.drawable.ic_menu_black_24dp)
            actionbar.setDisplayHomeAsUpEnabled(true)
        }

        val nav = findViewById<NavigationView>(R.id.nav_view)

        val deviceSpinner = nav.menu.findItem(R.id.nav_select_accelerator).actionView.findViewById(R.id.spinner) as Spinner
        val modelSpinner = nav.menu.findItem(R.id.nav_select_model).actionView.findViewById(R.id.spinner) as Spinner
        val threadsSpinner = nav.menu.findItem(R.id.nav_select_threads).actionView.findViewById(R.id.spinner) as Spinner
        val precisionSwitch = nav.menu.findItem(R.id.nav_switch_precision).actionView as SwitchCompat

        // Model Management

        (nav.menu.findItem(R.id.nav_import_model)).setOnMenuItemClickListener {
            startActivityForResult(Intent(this, ModelManagerActivity::class.java), MODEL_MANAGER_ACTIVITY)
            return@setOnMenuItemClickListener true
        }

        // Model Selection

        (nav.menu.findItem(R.id.nav_select_model).actionView.findViewById<View>(R.id.menu_title) as TextView).setText(R.string.model_menu_item_title)

        modelSpinner.adapter = ModelBundleArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, modelBundlesViewModel.modelBundles)
        modelSpinner.setSelection(modelBundlesViewModel.modelBundles.indexOf(modelBundlesViewModel.bundleWithId(selectedModel)), false)

        modelSpinner.onItemSelectedListener = object: OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) {
            }
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val previousValue = modelBundlesViewModel.modelBundles.indexOf(viewModel.modelRunner.model.bundle)
                val selectedBundle = modelBundlesViewModel.modelBundles[position]
                val selectedModelId = selectedBundle.identifier

                try { restartingInference {
                    viewModel.modelRunner.model = selectedBundle.newModel() as TFLiteModel
                    prefs.edit(true) { putString(getString(R.string.prefs_selected_model), selectedModelId) }
                    child<ModelRunnerWatcher>(R.id.container)?.modelDidChange()
                }} catch (e: Exception) {
                    alertModelChangeException()
                    modelSpinner.setSelection(previousValue)
                }
            }
        }

        // Device Selection

        (nav.menu.findItem(R.id.nav_select_accelerator).actionView.findViewById<View>(R.id.menu_title) as TextView).setText(R.string.device_menu_item_title)

        deviceSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, deviceOptions)
        deviceSpinner.setSelection(deviceOptions.indexOf(device), false)

        deviceSpinner.onItemSelectedListener = object: OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) {
            }
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val previousValue = deviceOptions.indexOf(ModelRunner.stringForDevice(viewModel.modelRunner.device))
                val selectedDevice = deviceOptions[position]

                try { restartingInference {
                    viewModel.modelRunner.device = ModelRunner.deviceFromString(selectedDevice)
                    prefs.edit(true) { putString(getString(R.string.prefs_run_on_device), selectedDevice) }
                }} catch (e: Exception) {
                    alertConfigChangeException()
                    deviceSpinner.setSelection(previousValue)
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
                val previousValue = numThreadsOptions.indexOf(viewModel.modelRunner.numThreads)
                val selectedThreads = numThreadsOptions[position]

                try { restartingInference {
                    viewModel.modelRunner.numThreads = selectedThreads
                    prefs.edit(true) { putInt(getString(R.string.prefs_num_threads), selectedThreads) }
                }} catch (e: Exception) {
                    alertConfigChangeException()
                    threadsSpinner.setSelection(previousValue)
                }
            }
        }

        // 16 Bit Checkbox

        precisionSwitch.isChecked = use16Bit

        precisionSwitch.setOnCheckedChangeListener { _, isChecked ->
            val previousValue = viewModel.modelRunner.use16Bit

            try { restartingInference {
                viewModel.modelRunner.use16Bit = isChecked
                prefs.edit(true) { putBoolean(getString(R.string.prefs_use_16_bit), isChecked) }
            }} catch (e: Exception) {
                alertConfigChangeException()
                precisionSwitch.isChecked = previousValue
            }
        }
    }

    //region Permissions

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        // Delays are necessary or app freezes with onActivityResult never called

        when (requestCode) {
            READ_EXTERNAL_STORAGE_PERMISSIONS_REQUEST -> {
                if (PermissionsManager.hasImageGalleryPermissions(this)) {
                    Handler().postDelayed({ pickImage() }, 100)
                } else if (PermissionsManager.neverAskImageGalleryPermissionsAgain(this)) {
                    PermissionsManager.showImageGalleryRationale(this)
                }
            }
            TAKE_PHOTO_PERMISSIONS_REQUEST -> {
                if (PermissionsManager.hasCameraPermissions(this)) {
                    Handler().postDelayed({ takePhoto() }, 100)
                } else if (PermissionsManager.neverAskCameraPermissionsAgain(this)) {
                    PermissionsManager.showCameraRationale(this)
                }
            }
            LIVE_CAMERA_PERMISSIONS_REQUEST -> {
                if (PermissionsManager.hasCameraPermissions(this)) {
                    Handler().postDelayed({ runLiveVideo() }, 100)
                } else if (PermissionsManager.neverAskCameraPermissionsAgain(this)) {
                    PermissionsManager.showCameraRationale(this)
                }
            }
        }
    }

    //endregion

    //region Image Picker

    private fun pickImage() {
        if (PermissionsManager.hasImageGalleryPermissions(this)) {
            startActivityForResult(Intent(Intent.ACTION_PICK).apply { type = "image/*" }, PICK_IMAGE_ACTIVITY)
        } else {
            PermissionsManager.requestImageGalleryPermissions(this, READ_EXTERNAL_STORAGE_PERMISSIONS_REQUEST)
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

    //region Live Video

    private fun runLiveVideo() {
        if (PermissionsManager.hasCameraPermissions(this)) {
            changeTab(Tab.LiveVideo)
        } else {
            PermissionsManager.requestCameraPermissions(this, LIVE_CAMERA_PERMISSIONS_REQUEST)
        }
    }

    //endregion

    //region Take Photo

    // Really wish we could wrap this up into one method and lambdas rather than spreading it out

    private var takePhotoUri: Uri? = null

    private fun takePhoto() {
        if (PermissionsManager.hasCameraPermissions(this)) {
            Intent(MediaStore.ACTION_IMAGE_CAPTURE).also { takePictureIntent ->
                takePictureIntent.resolveActivity(packageManager)?.also {

                    val file: File? = try {
                        createTempTakenPhotoFile()
                    } catch (x: IOException) {
                        // TODO: Show error
                        null
                    }

                    file?.also {
                        takePhotoUri = FileProvider.getUriForFile(this, "ai.doc.netrunner.fileprovider", file)

                        grantCameraActivityPermissions(takePictureIntent, takePhotoUri)
                        takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, takePhotoUri)
                        startActivityForResult(takePictureIntent, IMAGE_CAPTURE_ACTIVITY)
                    }
                }
            }
        } else {
            PermissionsManager.requestCameraPermissions(this, TAKE_PHOTO_PERMISSIONS_REQUEST)
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

    //region Welcome

    private val alreadyWelcomed by lazy {
        prefs.getBoolean(getString(R.string.prefs_welcomed), false)
    }

    private fun setupWelcome(showWelcome: Boolean) {
        if (showWelcome) {
            window.statusBarColor = resources.getColor(R.color.black)
            viewModel.currentTab = Tab.Welcome
            supportActionBar?.hide()
        } else {
            supportActionBar?.show()
        }
    }

    override fun onWelcomeCompleted(didGrantPermissions: Boolean) {
        if (!didGrantPermissions) {
            return
        }

        prefs.edit(true) { putBoolean(getString(R.string.prefs_welcomed), true) }
        changeTab(MainViewModel.Tab.LiveVideo)

        Handler().postDelayed({ // for effect
            window.statusBarColor = resources.getColor(R.color.colorPrimaryDark)
            supportActionBar?.show()
        }, 100)
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
            Tab.LiveVideo -> LiveCameraTabFragment()
            Tab.SinglePhoto -> SingleImageTabFragment()
            Tab.Welcome -> WelcomeFragment()
        }

        supportFragmentManager.beginTransaction().replace(R.id.container, fragment).commit()
    }

    //endregion

    // region Alerts

    /** Alert when model runner initialization fails in onCreate */

    private fun alertInitModelRunnerException() {
        AlertDialog.Builder(this).apply {
            setTitle(R.string.modelrunner_initfail_dialog_title)
            setMessage(R.string.modelrunner_initfail_dialog_message)

            setPositiveButton(R.string.dialog_ok_button) { dialog, _ ->
                dialog.dismiss()
            }
        }.show()
    }

    /** Alert when an inference exception is raised on the model runner thread */

    private fun alertInferenceException() {
        AlertDialog.Builder(this).apply {
            setTitle(R.string.modelrunner_exception_run_inference_dialog_title)
            setMessage(R.string.modelrunner_exception_run_inference_message)

            setPositiveButton(R.string.dialog_ok_button) { dialog, _ ->
                dialog.dismiss()
            }
        }.show()
    }

    /** Alert when changing a configuration settings raises an exception */

    private fun alertConfigChangeException() {
        AlertDialog.Builder(this).apply {
            setTitle(R.string.modelrunner_settings_exception_dialog_title)
            setMessage(R.string.modelrunner_exception_change_settings_message)

            setPositiveButton(R.string.dialog_ok_button) { dialog, _ ->
                dialog.dismiss()
            }
        }.show()
    }

    /** Alert when changing the model raises an exception */

    private fun alertModelChangeException() {
        AlertDialog.Builder(this).apply {
            setTitle(R.string.modelrunner_model_exception_dialog_title)
            setMessage(R.string.modelrunner_exception_change_model_message)

            setPositiveButton(R.string.dialog_ok_button) { dialog, _ ->
                dialog.dismiss()
            }
        }.show()
    }

    // endregion

    //region Utilities

    private fun <T>child(id: Int): T? {
        @Suppress("UNCHECKED_CAST")
        return supportFragmentManager.findFragmentById(id) as? T
    }

    private fun restartingInference(around: ()->Unit) {
        child<ModelRunnerWatcher>(R.id.container)?.stopRunning()
        around()
        child<ModelRunnerWatcher>(R.id.container)?.startRunning()
    }

    //endregion
}