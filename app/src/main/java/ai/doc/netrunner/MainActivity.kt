package ai.doc.netrunner

import ai.doc.netrunner.view.ClassificationViewModel
import ai.doc.netrunner.view.LiveCameraClassificationFragment
import ai.doc.netrunner.view.SingleImageClassificationFragment
import ai.doc.tensorio.TIOModel.TIOModelBundleException
import ai.doc.tensorio.TIOModel.TIOModelBundleManager
import ai.doc.tensorio.TIOModel.TIOModelException
import ai.doc.tensorio.TIOTFLiteModel.GpuDelegateHelper
import ai.doc.tensorio.TIOTFLiteModel.TIOTFLiteModel
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.*
import android.widget.AdapterView.OnItemSelectedListener
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.appcompat.widget.Toolbar
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.ViewModelProviders
import com.google.android.material.navigation.NavigationView
import java.io.IOException
import java.util.*

class MainActivity : AppCompatActivity() {

    private abstract inner class SpinnerListener : OnItemSelectedListener {
        override fun onItemSelected(parent: AdapterView<*>?, view: View, position: Int, id: Long) {
            view?.let { OnUserSelectedItem(parent, it, position, id) }
        }

        abstract fun OnUserSelectedItem(parent: AdapterView<*>?, view: View?, position: Int, id: Long)
        override fun onNothingSelected(parent: AdapterView<*>?) {}
    }

    companion object {
        private const val DEFAULT_MODEL_ID = "Mobilenet_V1_1.0_224"

        // TODO: Remove reference to face model
        private const val FACE_MODEL_ID = "phenomenal-face-mobilenet-v2-100-224-v101"
    }

    private val numThreadsOptions = arrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10)
    private val deviceOptions = ArrayList<String>()
    private val modelStrings = ArrayList<String>()
    private var deviceSpinner: Spinner? = null
    private var threadsSpinner: Spinner? = null
    private var modelSpinner: Spinner? = null
    private var precisionSwitch: SwitchCompat? = null
    private var faceModelLoaded = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val nav = findViewById<NavigationView>(R.id.nav_view)
        deviceSpinner = nav.menu.findItem(R.id.nav_select_accelerator).actionView.findViewById(R.id.spinner)
        precisionSwitch = nav.menu.findItem(R.id.nav_switch_precision).actionView as SwitchCompat
        modelSpinner = nav.menu.findItem(R.id.nav_select_model).actionView.findViewById(R.id.spinner)
        threadsSpinner = nav.menu.findItem(R.id.nav_select_threads).actionView.findViewById(R.id.spinner)
        try {
            val vm = ViewModelProviders.of(this).get(ClassificationViewModel::class.java)
            if (vm.manager == null) {
                val manager = TIOModelBundleManager(applicationContext, "")
                vm.manager = manager
            }
            val manager = vm.manager
            modelStrings.addAll(manager!!.bundleIds)
            setupDevices()
            setupDrawer()
            if (vm.modelRunner == null) {
                val bundle = manager.bundleWithId(DEFAULT_MODEL_ID)
                val model = bundle.newModel()
                model.load()
                val modelRunner = ModelRunner((model as TIOTFLiteModel))
                vm.modelRunner = modelRunner
            }
            if (vm.currentTab != -1) {
                nav.menu.findItem(vm.currentTab).isChecked = true
            } else {
                vm.currentTab = R.id.live_camera_fragment_menu_item
            }
            setupFragment(vm.currentTab)
        } catch (e: IOException) {
            e.printStackTrace()
        } catch (e: TIOModelException) {
            e.printStackTrace()
        } catch (e: TIOModelBundleException) {
            e.printStackTrace()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
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

    private fun loadFaceModel() {
        val vm = ViewModelProviders.of(this).get(ClassificationViewModel::class.java)
        modelSpinner!!.setSelection(modelStrings.indexOf(FACE_MODEL_ID), false)
        modelSpinner!!.isEnabled = false
        deviceSpinner!!.setSelection(deviceOptions.indexOf(getString(R.string.cpu)), false)
        deviceSpinner!!.isEnabled = false
        val manager = vm.manager
        val bundle = manager!!.bundleWithId(FACE_MODEL_ID)
        try {
            val newModel = bundle.newModel() as TIOTFLiteModel
            vm.modelRunner!!.switchModel(newModel, false, false, numThreadsOptions[threadsSpinner!!.selectedItemPosition], precisionSwitch!!.isChecked)
            Toast.makeText(this@MainActivity, "Loading $FACE_MODEL_ID", Toast.LENGTH_SHORT).show()
        } catch (e: TIOModelBundleException) {
            e.printStackTrace()
        }
        faceModelLoaded = true
    }

    private fun loadDefaultModel() {
        val vm = ViewModelProviders.of(this).get(ClassificationViewModel::class.java)
        vm.modelRunner!!.stopStreamClassification()
        modelSpinner!!.setSelection(modelStrings.indexOf(DEFAULT_MODEL_ID), false)
        modelSpinner!!.isEnabled = true
        deviceSpinner!!.setSelection(deviceOptions.indexOf(getString(R.string.cpu)), false)
        deviceSpinner!!.isEnabled = true
        val manager = vm.manager
        val bundle = manager!!.bundleWithId(DEFAULT_MODEL_ID)
        try {
            val newModel = bundle.newModel() as TIOTFLiteModel
            vm.modelRunner!!.switchModel(newModel, false, false, numThreadsOptions[threadsSpinner!!.selectedItemPosition], precisionSwitch!!.isChecked)
            Toast.makeText(this@MainActivity, "Loading $DEFAULT_MODEL_ID", Toast.LENGTH_SHORT).show()
        } catch (e: TIOModelBundleException) {
            e.printStackTrace()
        }
        faceModelLoaded = false
    }

    private fun setupDrawer() {
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        val actionbar = supportActionBar
        if (actionbar != null) {
            actionbar.setDisplayHomeAsUpEnabled(true)
            actionbar.setHomeAsUpIndicator(R.drawable.ic_menu_black_24dp)
        }
        val nav = findViewById<NavigationView>(R.id.nav_view)
        (nav.menu.findItem(R.id.nav_select_accelerator).actionView.findViewById<View>(R.id.menu_title) as TextView).setText(R.string.device_menu_item_title)
        deviceSpinner!!.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, deviceOptions)
        deviceSpinner!!.setSelection(0, false)
        deviceSpinner!!.onItemSelectedListener = object : SpinnerListener() {
            override fun OnUserSelectedItem(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val device = deviceOptions[position]
                val vm = ViewModelProviders.of(this@MainActivity).get(ClassificationViewModel::class.java)
                if (device == getString(R.string.cpu)) {
                    vm.modelRunner!!.useCPU()
                    Toast.makeText(this@MainActivity, "using the CPU", Toast.LENGTH_SHORT).show()
                } else if (device == getString(R.string.gpu)) {
                    vm.modelRunner!!.useGPU()
                    Toast.makeText(this@MainActivity, "using the GPU", Toast.LENGTH_SHORT).show()
                } else {
                    vm.modelRunner!!.useNNAPI()
                    Toast.makeText(this@MainActivity, "using NNAPI", Toast.LENGTH_SHORT).show()
                }
            }
        }
        (nav.menu.findItem(R.id.nav_select_model).actionView.findViewById<View>(R.id.menu_title) as TextView).setText(R.string.model_menu_item_title)
        modelSpinner!!.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, modelStrings)
        modelSpinner!!.setSelection(0, false)
        modelSpinner!!.onItemSelectedListener = object : SpinnerListener() {
            override fun OnUserSelectedItem(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val model = modelStrings[position]
                val vm = ViewModelProviders.of(this@MainActivity).get(ClassificationViewModel::class.java)
                val manager = vm.manager
                val bundle = manager!!.bundleWithId(model)
                try {
                    val newModel = bundle.newModel() as TIOTFLiteModel
                    vm.modelRunner!!.switchModel(newModel)
                    Toast.makeText(this@MainActivity, "Loading $model", Toast.LENGTH_SHORT).show()
                } catch (e: TIOModelBundleException) {
                    e.printStackTrace()
                } catch (e: TIOModelException) {
                    e.printStackTrace()
                }
            }
        }
        (nav.menu.findItem(R.id.nav_select_threads).actionView.findViewById<View>(R.id.menu_title) as TextView).setText(R.string.threads_menu_item_title)
        threadsSpinner!!.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, numThreadsOptions)
        threadsSpinner!!.setSelection(0, false)
        threadsSpinner!!.onItemSelectedListener = object : SpinnerListener() {
            override fun OnUserSelectedItem(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val vm = ViewModelProviders.of(this@MainActivity).get(ClassificationViewModel::class.java)
                val threads = numThreadsOptions[position]
                vm.modelRunner!!.setNumThreads(threads)
                Toast.makeText(this@MainActivity, "using $threads threads", Toast.LENGTH_SHORT).show()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                val vm = ViewModelProviders.of(this@MainActivity).get(ClassificationViewModel::class.java)
                vm.modelRunner!!.setNumThreads(1)
                threadsSpinner!!.setSelection(0)
            }
        }
        precisionSwitch!!.setOnCheckedChangeListener { buttonView, isChecked ->
            val vm = ViewModelProviders.of(this@MainActivity).get(ClassificationViewModel::class.java)
            vm.modelRunner!!.setUse16bit(isChecked)
        }
        nav.setNavigationItemSelectedListener { menuItem: MenuItem ->
            if (!menuItem.isChecked) {
                val selectedTabMenuId = menuItem.itemId
                val vm = ViewModelProviders.of(this@MainActivity).get(ClassificationViewModel::class.java)
                vm.currentTab = selectedTabMenuId
                menuItem.isChecked = true
                setupFragment(selectedTabMenuId)
                return@setNavigationItemSelectedListener true
            }
            false
        }
    }

    private fun setupDevices() {
        deviceOptions.add(getString(R.string.cpu))
        if (GpuDelegateHelper.isGpuDelegateAvailable()) {
            deviceOptions.add(getString(R.string.gpu))
        }
        deviceOptions.add(getString(R.string.nnapi))
    }

    private fun setupFragment(selectedTabMenuId: Int) {
        val vm = ViewModelProviders.of(this).get(ClassificationViewModel::class.java)
        vm.modelRunner!!.stopStreamClassification()
        if (selectedTabMenuId == R.id.live_camera_fragment_menu_item) {
            if (faceModelLoaded) {
                loadDefaultModel()
            }
            supportFragmentManager.beginTransaction().replace(R.id.container, LiveCameraClassificationFragment(), getString(R.string.active_fragment_tag)).commit()
        } else if (selectedTabMenuId == R.id.single_image_fragment_menu_item) {
            if (faceModelLoaded) {
                loadDefaultModel()
            }
            supportFragmentManager.beginTransaction().replace(R.id.container, SingleImageClassificationFragment()).commit()
        }
    }

    // For Later
    private val isEmulator: Boolean
        private get() = (Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic")
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

}