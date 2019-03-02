package ai.doc.netrunner_android.view;


import android.Manifest;
import android.arch.lifecycle.LiveData;
import android.arch.lifecycle.MutableLiveData;
import android.arch.lifecycle.ViewModelProviders;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.databinding.DataBindingUtil;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;

import ai.doc.netrunner_android.ModelRunner;
import ai.doc.netrunner_android.R;
import ai.doc.netrunner_android.databinding.FragmentBulkInferenceBinding;

import static android.Manifest.permission.READ_EXTERNAL_STORAGE;
import static android.app.Activity.RESULT_OK;

public class BulkInferenceFragment extends Fragment {
    private static final int READ_EXTERNAL_STORAGE_REQUEST_CODE = 123;
    private ImageView imageView;
    private Bitmap selected;
    private Button btnClassify;
    private MutableLiveData<String> latency = new MutableLiveData<>();
    private MutableLiveData<String> predictions = new MutableLiveData<>();

    public BulkInferenceFragment() {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        FragmentBulkInferenceBinding binding = DataBindingUtil.inflate(inflater, R.layout.fragment_bulk_inference, container, false);
        binding.setFragment(this);
        binding.setLifecycleOwner(this);
        View root = binding.getRoot();
        imageView = root.findViewById(R.id.imageview);
        btnClassify = root.findViewById(R.id.btn_classify);
        return root;
    }

    public void pickImage() {

        if (ActivityCompat.checkSelfPermission(getActivity(), READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                    getActivity(),
                    new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                    READ_EXTERNAL_STORAGE_REQUEST_CODE
            );
        }
        else{
            Intent intent = new Intent(Intent.ACTION_PICK);
            intent.setType("image/*");
            startActivityForResult(intent, 1);
        }

    }

    public void classify(){
        ClassificationViewModel vm = ViewModelProviders.of(getActivity()).get(ClassificationViewModel.class);
        ModelRunner modelRunner = vm.getModelRunner();

        Bitmap small = Bitmap.createScaledBitmap(selected, modelRunner.getInputWidth(), modelRunner.getInputHeight(), false);
        modelRunner.classifyFrame(0, small, (requestId, prediction, latency) -> {
            BulkInferenceFragment.this.predictions.postValue(prediction);
            BulkInferenceFragment.this.latency.postValue(latency);
        });
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode != RESULT_OK) {
            return;
        }
        if (requestCode == 1) {
            Uri pickedImage = data.getData();
            // Let's read picked image path using content resolver
            String[] filePath = { MediaStore.Images.Media.DATA };
            Cursor cursor = getActivity().getContentResolver().query(pickedImage, filePath, null, null, null);
            cursor.moveToFirst();
            String imagePath = cursor.getString(cursor.getColumnIndex(filePath[0]));

            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inPreferredConfig = Bitmap.Config.ARGB_8888;

            selected = BitmapFactory.decodeFile(imagePath, options);
            imageView.setImageBitmap(selected);

            btnClassify.setEnabled(true);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == READ_EXTERNAL_STORAGE_REQUEST_CODE){
            if (ActivityCompat.checkSelfPermission(getActivity(), READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED){
                pickImage();
            }
        }
    }

    public LiveData<String> getLatency() {
        return latency;
    }

    public LiveData<String> getPredictions() {
        return predictions;
    }
}
