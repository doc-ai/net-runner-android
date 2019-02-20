package ai.doc.netrunner_android;


import android.arch.lifecycle.ViewModelProviders;
import android.os.Bundle;
import android.support.design.widget.NavigationView;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.SwitchCompat;
import android.support.v7.widget.Toolbar;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CompoundButton;
import android.widget.Spinner;
import android.widget.Switch;

import java.io.IOException;
import java.util.ArrayList;

import ai.doc.netrunner_android.tensorio.TIOModel.TIOModel;
import ai.doc.netrunner_android.tensorio.TIOModel.TIOModelBundle;
import ai.doc.netrunner_android.tensorio.TIOModel.TIOModelBundleException;
import ai.doc.netrunner_android.tensorio.TIOModel.TIOModelBundleManager;
import ai.doc.netrunner_android.tensorio.TIOModel.TIOModelException;
import ai.doc.netrunner_android.tensorio.TIOTensorflowLiteModel.GpuDelegateHelper;
import ai.doc.netrunner_android.tensorio.TIOTensorflowLiteModel.TIOTFLiteModel;
import ai.doc.netrunner_android.view.BenchmarkFragment;
import ai.doc.netrunner_android.view.BulkInferenceFragment;
import ai.doc.netrunner_android.view.ClassificationViewModel;
import ai.doc.netrunner_android.view.LiveCameraClassificationFragment;

public class MainActivity extends AppCompatActivity {
    private Integer[] numThreadsOptions = new Integer[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10};
    private ArrayList<String> deviceOptions = new ArrayList<>();
    private ArrayList<String> modelStrings = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        setupModels();
        setupDevices();
        setupDrawer();

        ClassificationViewModel vm = ViewModelProviders.of(this).get(ClassificationViewModel.class);

        if (vm.getModelRunner() == null) {
            try {
                TIOModelBundleManager manager = new TIOModelBundleManager(getApplicationContext(), "");
                TIOModelBundle bundle = manager.bundleWithId("mobilenet-v2-100-224-unquantized");
                TIOModel model = bundle.newModel();
                model.load();
                ModelRunner modelRunner = new ModelRunner((TIOTFLiteModel)model);
                vm.setModelRunner(modelRunner);
            } catch (IOException | TIOModelException | TIOModelBundleException e) {
                e.printStackTrace();
            }
        }

        if (vm.getCurrentTab() != -1) {
            NavigationView nav = findViewById(R.id.nav_view);
            nav.getMenu().findItem(vm.getCurrentTab()).setChecked(true);
        } else {
            vm.setCurrentTab(R.id.live_camera_fragment_menu_item);
        }
        setupFragment(vm.getCurrentTab());
    }


    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                ((DrawerLayout) findViewById(R.id.drawer_layout)).openDrawer(GravityCompat.START);
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void setupDrawer() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        ActionBar actionbar = getSupportActionBar();
        if (actionbar != null) {
            actionbar.setDisplayHomeAsUpEnabled(true);
            actionbar.setHomeAsUpIndicator(R.drawable.ic_menu_black_24dp);
        }
        NavigationView nav = findViewById(R.id.nav_view);

        Spinner s = (Spinner) nav.getMenu().findItem(R.id.nav_select_accelerator).getActionView();
        s.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, deviceOptions));
        s.setSelection(0);
        s.setOnItemSelectedListener(new SpinnerListener() {
            @Override
            public void OnUserSelectedItem(AdapterView<?> parent, View view, int position, long id) {
                String device = deviceOptions.get(position);
                ClassificationViewModel vm = ViewModelProviders.of(MainActivity.this).get(ClassificationViewModel.class);
                if (device.equals(getString(R.string.cpu))) {
                    vm.getModelRunner().useCPU();
                } else if (device.equals(getString(R.string.gpu))) {
                    vm.getModelRunner().useGPU();
                } else {
                    vm.getModelRunner().useNNAPI();
                }

            }
        });

        Spinner s2 = (Spinner) nav.getMenu().findItem(R.id.nav_select_model).getActionView();
        s2.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, modelStrings));
        s2.setSelection(0);
        s2.setOnItemSelectedListener(new SpinnerListener() {
            @Override
            public void OnUserSelectedItem(AdapterView<?> parent, View view, int position, long id) {
                String model = modelStrings.get(position);
            }
        });

        Spinner s3 = (Spinner) nav.getMenu().findItem(R.id.nav_select_threads).getActionView();
        s3.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, numThreadsOptions));
        s3.setSelection(0);
        s3.setOnItemSelectedListener(new SpinnerListener() {
            @Override
            public void OnUserSelectedItem(AdapterView<?> parent, View view, int position, long id) {
                ClassificationViewModel vm = ViewModelProviders.of(MainActivity.this).get(ClassificationViewModel.class);
                int threads = numThreadsOptions[position];
                vm.getModelRunner().setNumThreads(threads);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                ClassificationViewModel vm = ViewModelProviders.of(MainActivity.this).get(ClassificationViewModel.class);
                vm.getModelRunner().setNumThreads(1);
                s3.setSelection(0);
            }
        });

        SwitchCompat s4 = (SwitchCompat)nav.getMenu().findItem(R.id.nav_switch_precision).getActionView();
        s4.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                ClassificationViewModel vm = ViewModelProviders.of(MainActivity.this).get(ClassificationViewModel.class);
                vm.getModelRunner().setUse16bit(isChecked);
            }
        });


        nav.setNavigationItemSelectedListener(
                menuItem -> {
                    if (!menuItem.isChecked()) {
                        int selectedTabMenuId = menuItem.getItemId();
                        ClassificationViewModel vm = ViewModelProviders.of(MainActivity.this).get(ClassificationViewModel.class);
                        vm.setCurrentTab(selectedTabMenuId);
                        menuItem.setChecked(true);
                        setupFragment(selectedTabMenuId);
                        return true;
                    }
                    return false;
                });
    }

    private void setupModels() {
        modelStrings.add(getString(R.string.mobilenetV1Float));
        modelStrings.add(getString(R.string.mobilenetV1Quant));
    }

    private void setupDevices() {
        deviceOptions.add(getString(R.string.cpu));
        if (GpuDelegateHelper.isGpuDelegateAvailable()) {
            deviceOptions.add(getString(R.string.gpu));
        }
        deviceOptions.add(getString(R.string.nnapi));
    }

    private void setupFragment(int selectedTabMenuId) {
        switch (selectedTabMenuId) {
            case R.id.live_camera_fragment_menu_item:
                getSupportFragmentManager().beginTransaction().replace(R.id.container, new LiveCameraClassificationFragment(), getString(R.string.active_fragment_tag)).commit();
                break;
            case R.id.bulk_inference_fragment_menu_item:
                getSupportFragmentManager().beginTransaction().replace(R.id.container, new BulkInferenceFragment()).commit();
                break;
            case R.id.benchmark_fragment_menu_item:
                getSupportFragmentManager().beginTransaction().replace(R.id.container, new BenchmarkFragment()).commit();
                break;
        }
    }

    private abstract class SpinnerListener implements AdapterView.OnItemSelectedListener {
        @Override
        public final void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
            if (view != null) {
                OnUserSelectedItem(parent, view, position, id);
            }
        }

        public abstract void OnUserSelectedItem(AdapterView<?> parent, View view, int position, long id);

        @Override
        public void onNothingSelected(AdapterView<?> parent) {
        }
    }

}
