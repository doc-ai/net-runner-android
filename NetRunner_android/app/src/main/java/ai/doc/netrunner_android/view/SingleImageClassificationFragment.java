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
import android.os.Bundle;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;

import java.util.List;
import java.util.Map;

import ai.doc.netrunner_android.ModelRunner;
import ai.doc.netrunner_android.R;
import ai.doc.netrunner_android.databinding.FragmentSingleImageBinding;
import ai.doc.tensorio.TIOUtilities.TIOClassificationHelper;

import static android.Manifest.permission.READ_EXTERNAL_STORAGE;
import static android.app.Activity.RESULT_OK;

public class SingleImageClassificationFragment extends Fragment {
    private static final int READ_EXTERNAL_STORAGE_REQUEST_CODE = 123;
    private static final int RESULTS_TO_SHOW = 3;

    private ImageView imageView;
    private Bitmap selected;
    private Button btnClassify;
    private MutableLiveData<String> latency = new MutableLiveData<>();
    private MutableLiveData<String> predictions = new MutableLiveData<>();

    public SingleImageClassificationFragment() {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        FragmentSingleImageBinding binding = DataBindingUtil.inflate(inflater, R.layout.fragment_single_image, container, false);
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

        modelRunner.classifyFrame(0, small, (requestId, output, l) -> {
            Map<String, Float> classification = (Map<String, Float>)((Map<String,Object>)output).get("classification");
            List<Map.Entry<String, Float>> top5 = TIOClassificationHelper.topN(classification, RESULTS_TO_SHOW);
            String top5formatted = formattedResults(top5);

            predictions.postValue(top5formatted);
            latency.postValue(l+" ms");
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

    private String formattedResults(List<Map.Entry<String, Float>> results) {
        StringBuilder b = new StringBuilder();

        for (Map.Entry<String, Float> entry : results) {
            b.append(entry.getKey());
            b.append(": ");
            b.append(String.format("%.2f", entry.getValue()));
            b.append("\n");
        }

        b.setLength(b.length() - 1);

        return b.toString();
    }

    public LiveData<String> getLatency() {
        return latency;
    }

    public LiveData<String> getPredictions() {
        return predictions;
    }
}
