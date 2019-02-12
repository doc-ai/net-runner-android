package ai.doc.netrunner_android.view;


import android.arch.lifecycle.LiveData;
import android.arch.lifecycle.MutableLiveData;
import android.arch.lifecycle.ViewModelProviders;
import android.databinding.DataBindingUtil;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;

import ai.doc.netrunner_android.ModelRunner;
import ai.doc.netrunner_android.R;
import ai.doc.netrunner_android.databinding.FragmentLiveCameraClassificationBinding;

/**
 * A simple {@link Fragment} subclass.
 */
public class LiveCameraClassificationFragment extends LiveCameraFragment implements ModelRunner.ModelRunnerDataSource {
    private MutableLiveData<String> latency = new MutableLiveData<>();
    private MutableLiveData<String> predictions = new MutableLiveData<>();
    private TextureView textureView;

    public LiveCameraClassificationFragment() {
        // Required empty public constructor
    }


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        FragmentLiveCameraClassificationBinding binding = DataBindingUtil.inflate(inflater, R.layout.fragment_live_camera_classification, container, false);
        binding.setFragment(this);
        binding.setLifecycleOwner(this);
        return binding.getRoot();
    }


    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        textureView = view.findViewById(R.id.texture);
    }

    @Override
    public Bitmap getNextInput(int size_x, int size_y) {
        return textureView.getBitmap(size_x, size_y);
    }

    @Override
    public void onResume() {
        super.onResume();
        ClassificationViewModel vm = ViewModelProviders.of(getActivity()).get(ClassificationViewModel.class);
        vm.getModelRunner().startStreamClassification(this, (requestId, predictionText, latencyText) -> {
            predictions.postValue(predictionText);
            latency.postValue(latencyText);
        });
    }

    @Override
    public void onPause() {
        super.closeCamera();
        ClassificationViewModel vm = ViewModelProviders.of(getActivity()).get(ClassificationViewModel.class);
        vm.getModelRunner().stopStreamClassification();
        super.onPause();
    }


    public LiveData<String> getLatency() {
        return latency;
    }

    public LiveData<String> getPredictions() {
        return predictions;
    }

}
