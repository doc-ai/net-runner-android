package ai.doc.netrunner.view;


import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModelProviders;
import androidx.databinding.DataBindingUtil;
import android.graphics.Bitmap;
import android.os.Bundle;
import androidx.fragment.app.Fragment;
import android.view.LayoutInflater;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;

import java.util.List;
import java.util.Map;

import ai.doc.netrunner.ModelRunner;
import ai.doc.netrunner.R;
import ai.doc.netrunner.databinding.FragmentLiveCameraClassificationBinding;
import ai.doc.tensorio.TIOUtilities.TIOClassificationHelper;

/**
 * A simple {@link Fragment} subclass.
 */

public class LiveCameraClassificationFragment extends LiveCameraFragment implements ModelRunner.ModelRunnerDataSource {
    private static final int RESULTS_TO_SHOW = 3;
    private static final int FILTER_STAGES = 3;
    private static final float FILTER_FACTOR = 0.4f;

    private MutableLiveData<String> latency = new MutableLiveData<>();
    private MutableLiveData<String> predictions = new MutableLiveData<>();
    private TextureView textureView;
    private float[][] filterLabelProbArray = null;

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
        startClassification();
    }

    @Override
    public void onPause() {
        super.closeCamera();
        stopClassification();
        super.onPause();
    }

    public void startClassification() {
        ClassificationViewModel vm = ViewModelProviders.of(getActivity()).get(ClassificationViewModel.class);

        vm.getModelRunner().startStreamClassification(this, (requestId, output, l) -> {
            Map<String, Float> classification = (Map<String, Float>)((Map<String,Object>)output).get("classification");
            List<Map.Entry<String, Float>> top5 = TIOClassificationHelper.topN(classification, RESULTS_TO_SHOW);
            String top5formatted = formattedResults(top5);

            // TODO: Apply smoothing filter

            predictions.postValue(top5formatted);
            latency.postValue(l + " ms");
        });
    }

    public void stopClassification() {
        ClassificationViewModel vm = ViewModelProviders.of(getActivity()).get(ClassificationViewModel.class);
        vm.getModelRunner().stopStreamClassification();
    }

    public LiveData<String> getLatency() {
        return latency;
    }

    public LiveData<String> getPredictions() {
        return predictions;
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

    private void applyFilter(float[] result, int numLabels) {

        if (filterLabelProbArray == null || filterLabelProbArray[0].length != numLabels) {
            filterLabelProbArray = new float[FILTER_STAGES][numLabels];
        }

        // Low pass filter `labelProbArray` into the first stage of the filter.
        for (int j = 0; j < numLabels; ++j) {
            filterLabelProbArray[0][j] +=
                    FILTER_FACTOR * (result[j] - filterLabelProbArray[0][j]);
        }
        // Low pass filter each stage into the next.
        for (int i = 1; i < FILTER_STAGES; ++i) {
            for (int j = 0; j < numLabels; ++j) {
                filterLabelProbArray[i][j] +=
                        FILTER_FACTOR * (filterLabelProbArray[i - 1][j] - filterLabelProbArray[i][j]);
            }
        }

        // Copy the last stage filter output back to `labelProbArray`.
        System.arraycopy(filterLabelProbArray[FILTER_STAGES - 1], 0, result, 0, numLabels);
    }

}
