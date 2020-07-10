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
import androidx.lifecycle.ViewModelProvider

import com.google.android.material.navigation.NavigationView

import java.io.IOException
import java.util.*

private const val DEFAULT_MODEL_ID = "Mobilenet_V2_1.0_224"

// TODO: Close drawer after selection
// TODO: Select image before showing single image fragment

class MainActivity : AppCompatActivity() {

    private abstract inner class SpinnerListener : OnItemSelectedListener {
        override fun onItemSelected(parent: AdapterView<*>?, view: View, position: Int, id: Long) {
            view.let { OnUserSelectedItem(parent, it, position, id) }
        }

        abstract fun OnUserSelectedItem(parent: AdapterView<*>?, view: View?, position: Int, id: Long)
        override fun onNothingSelected(parent: AdapterView<*>?) {}
    }

    private val numThreadsOptions = arrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10)
    private val deviceOptions = ArrayList<String>()

    private lateinit var deviceSpinner: Spinner
    private lateinit var threadsSpinner: Spinner
    private lateinit var modelSpinner: Spinner
    private lateinit var precisionSwitch: SwitchCompat

    private val viewModel: ClassificationViewModel by lazy {
        ViewModelProvider(this).get(ClassificationViewModel::class.java)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val nav = findViewById<NavigationView>(R.id.nav_view)

        deviceSpinner = nav.menu.findItem(R.id.nav_select_accelerator).actionView.findViewById(R.id.spinner)
        precisionSwitch = nav.menu.findItem(R.id.nav_switch_precision).actionView as SwitchCompat
        modelSpinner = nav.menu.findItem(R.id.nav_select_model).actionView.findViewById(R.id.spinner)
        threadsSpinner = nav.menu.findItem(R.id.nav_select_threads).actionView.findViewById(R.id.spinner)

        viewModel.manager = TIOModelBundleManager(applicationContext, "")

        try {
            val bundle = viewModel.manager.bundleWithId(DEFAULT_MODEL_ID)
            val model = bundle.newModel()
            model.load()

            val modelRunner = ModelRunner((model as TIOTFLiteModel))
            viewModel.modelRunner = modelRunner
        } catch (e: IOException) {
            e.printStackTrace()
        } catch (e: TIOModelException) {
            e.printStackTrace()
        } catch (e: TIOModelBundleException) {
            e.printStackTrace()
        }

        setupDevices()
        setupDrawer()

        if (viewModel.currentTab != -1) {
            nav.menu.findItem(viewModel.currentTab).isChecked = true
        } else {
            viewModel.currentTab = R.id.live_camera_fragment_menu_item
        }

        setupFragment(viewModel.currentTab)
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

        deviceSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, deviceOptions)
        deviceSpinner.setSelection(0, false)

        deviceSpinner.onItemSelectedListener = object : SpinnerListener() {
            override fun OnUserSelectedItem(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val device = deviceOptions[position]
                if (device == getString(R.string.cpu)) {
                    viewModel.modelRunner.useCPU()
                    Toast.makeText(this@MainActivity, "using the CPU", Toast.LENGTH_SHORT).show()
                } else if (device == getString(R.string.gpu)) {
                    viewModel.modelRunner.useGPU()
                    Toast.makeText(this@MainActivity, "using the GPU", Toast.LENGTH_SHORT).show()
                } else {
                    viewModel.modelRunner.useNNAPI()
                    Toast.makeText(this@MainActivity, "using NNAPI", Toast.LENGTH_SHORT).show()
                }
            }
        }

        (nav.menu.findItem(R.id.nav_select_model).actionView.findViewById<View>(R.id.menu_title) as TextView).setText(R.string.model_menu_item_title)

        modelSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, viewModel.modelIds)
        modelSpinner.setSelection(0, false)

        modelSpinner.onItemSelectedListener = object : SpinnerListener() {
            override fun OnUserSelectedItem(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val model = viewModel.modelIds[position]
                val bundle = viewModel.manager.bundleWithId(model)
                try {
                    val newModel = bundle.newModel() as TIOTFLiteModel
                    viewModel.modelRunner.switchModel(newModel)
                    Toast.makeText(this@MainActivity, "Loading $model", Toast.LENGTH_SHORT).show()
                } catch (e: TIOModelBundleException) {
                    e.printStackTrace()
                } catch (e: TIOModelException) {
                    e.printStackTrace()
                }
            }
        }

        (nav.menu.findItem(R.id.nav_select_threads).actionView.findViewById<View>(R.id.menu_title) as TextView).setText(R.string.threads_menu_item_title)

        threadsSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, numThreadsOptions)
        threadsSpinner.setSelection(0, false)

        threadsSpinner.onItemSelectedListener = object : SpinnerListener() {
            override fun OnUserSelectedItem(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val threads = numThreadsOptions[position]
                viewModel.modelRunner.setNumThreads(threads)
                Toast.makeText(this@MainActivity, "using $threads threads", Toast.LENGTH_SHORT).show()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                viewModel.modelRunner.setNumThreads(1)
                threadsSpinner.setSelection(0)
            }
        }

        precisionSwitch.setOnCheckedChangeListener { buttonView, isChecked ->
            viewModel.modelRunner.setUse16bit(isChecked)
        }

        nav.setNavigationItemSelectedListener { menuItem: MenuItem ->
            if (!menuItem.isChecked) {
                val selectedTabMenuId = menuItem.itemId
                viewModel.currentTab = selectedTabMenuId
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
        viewModel.modelRunner.stopStreamClassification()

        if (selectedTabMenuId == R.id.live_camera_fragment_menu_item) {
            supportFragmentManager.beginTransaction().replace(R.id.container, LiveCameraClassificationFragment(), getString(R.string.active_fragment_tag)).commit()
        } else if (selectedTabMenuId == R.id.single_image_fragment_menu_item) {
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