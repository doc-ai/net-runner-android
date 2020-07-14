package ai.doc.netrunner

import ai.doc.netrunner.view.ClassificationViewModel
import ai.doc.netrunner.view.ClassificationViewModel.CurrentTab
import ai.doc.netrunner.view.LiveCameraClassificationFragment
import ai.doc.netrunner.view.SingleImageClassificationFragment

import ai.doc.tensorio.TIOModel.TIOModelBundleException
import ai.doc.tensorio.TIOModel.TIOModelBundleManager
import ai.doc.tensorio.TIOModel.TIOModelException
import ai.doc.tensorio.TIOTFLiteModel.TIOTFLiteModel

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.*
import android.widget.AdapterView.OnItemSelectedListener

import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.appcompat.widget.Toolbar
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.ViewModelProvider

import com.google.android.material.navigation.NavigationView

import java.io.IOException
import java.lang.IllegalArgumentException
import kotlin.collections.ArrayList

private const val DEFAULT_MODEL_ID = "Mobilenet_V2_1.0_224"

// TODO: Close drawer after selection
// TODO: Select image before showing single image fragment

class MainActivity : AppCompatActivity() {

    private val numThreadsOptions = arrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10)

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

    private val viewModel: ClassificationViewModel by lazy {
        ViewModelProvider(this).get(ClassificationViewModel::class.java)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

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

        setupInputSourceButton()
        setupDrawer()

        if (viewModel.currentTab == CurrentTab.None) {
            viewModel.currentTab = CurrentTab.LiveVideo
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
                    when (which) {
                        0 -> setupFragment(CurrentTab.LiveVideo)
                        // TODO: Take a picture intent
                        1 -> setupFragment(CurrentTab.TakePhoto)
                        // TODO: Choose picture first
                        2 -> setupFragment(CurrentTab.ChoosePhoto)
                    }
                    dialog.dismiss()
                }
            }.show()
        }
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

        val deviceSpinner = nav.menu.findItem(R.id.nav_select_accelerator).actionView.findViewById(R.id.spinner) as Spinner
        val modelSpinner = nav.menu.findItem(R.id.nav_select_model).actionView.findViewById(R.id.spinner) as Spinner
        val threadsSpinner = nav.menu.findItem(R.id.nav_select_threads).actionView.findViewById(R.id.spinner) as Spinner
        val precisionSwitch = nav.menu.findItem(R.id.nav_switch_precision).actionView as SwitchCompat

        // Device Selection

        (nav.menu.findItem(R.id.nav_select_accelerator).actionView.findViewById<View>(R.id.menu_title) as TextView).setText(R.string.device_menu_item_title)

        deviceSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, deviceOptions)
        deviceSpinner.setSelection(0, false)

        deviceSpinner.onItemSelectedListener = object: OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) {
            }
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val device = deviceOptions[position]

                when (device) {
                    getString(R.string.cpu) ->
                        viewModel.modelRunner.device = ModelRunner.Device.CPU
                    getString(R.string.gpu) ->
                        viewModel.modelRunner.device = ModelRunner.Device.GPU
                    else ->
                        viewModel.modelRunner.device = ModelRunner.Device.NNAPI
                }

                Toast.makeText(this@MainActivity, "Using $device", Toast.LENGTH_SHORT).show()
            }
        }

        // Model Selection

        (nav.menu.findItem(R.id.nav_select_model).actionView.findViewById<View>(R.id.menu_title) as TextView).setText(R.string.model_menu_item_title)

        modelSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, viewModel.modelIds)
        modelSpinner.setSelection(0, false)

        modelSpinner.onItemSelectedListener = object: OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) {
            }
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val modelId = viewModel.modelIds[position]
                val bundle = viewModel.manager.bundleWithId(modelId)
                try {
                    val model = bundle.newModel() as TIOTFLiteModel
                    viewModel.modelRunner.switchModel(model)
                    Toast.makeText(this@MainActivity, "Loading $modelId", Toast.LENGTH_SHORT).show()
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
        threadsSpinner.setSelection(0, false)

        threadsSpinner.onItemSelectedListener = object: OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) {
            }
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val threads = numThreadsOptions[position]
                viewModel.modelRunner.numThreads = threads
                Toast.makeText(this@MainActivity, "Using $threads threads", Toast.LENGTH_SHORT).show()
            }
        }

        // 16 Bit Checkbox

        precisionSwitch.setOnCheckedChangeListener { _, isChecked ->
            viewModel.modelRunner.use16Bit = isChecked
        }
    }

    private fun setupFragment(tab: CurrentTab) {
        viewModel.modelRunner.stopStreamingInference()

        val fragment = when (tab) {
            CurrentTab.LiveVideo -> LiveCameraClassificationFragment()
            CurrentTab.TakePhoto -> SingleImageClassificationFragment()
            CurrentTab.ChoosePhoto -> SingleImageClassificationFragment()
            CurrentTab.None -> throw IllegalArgumentException()
        }

        supportFragmentManager.beginTransaction().replace(R.id.container, fragment).commit()
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

}