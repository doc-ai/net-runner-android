package ai.doc.netrunner_android.view;


import android.arch.lifecycle.LiveData;
import android.arch.lifecycle.MutableLiveData;
import android.arch.lifecycle.ViewModelProviders;
import android.databinding.DataBindingUtil;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.style.RelativeSizeSpan;
import android.view.LayoutInflater;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;

import java.util.AbstractMap;
import java.util.Map;
import java.util.PriorityQueue;

import ai.doc.netrunner_android.ModelRunner;
import ai.doc.netrunner_android.R;
import ai.doc.netrunner_android.databinding.FragmentLiveCameraClassificationBinding;

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

    public void startClassification(){
        ClassificationViewModel vm = ViewModelProviders.of(getActivity()).get(ClassificationViewModel.class);
        vm.getModelRunner().startStreamClassification(this, (requestId, prediction, l) -> {
            if (prediction instanceof float[]) {
                float[] resultArray = (float[]) prediction;
                String[] labels = vm.getModelRunner().getLabels();

                // Smooth the results across frames.
                applyFilter(resultArray, labels.length);

                // Show the prediction
                SpannableStringBuilder predictionsBuilder = new SpannableStringBuilder();
                printTopKLabels(predictionsBuilder, resultArray, labels);

                predictions.postValue(predictionsBuilder.toString());
                latency.postValue(l + " ms");
            }
        });
    }

    public void stopClassification(){
        ClassificationViewModel vm = ViewModelProviders.of(getActivity()).get(ClassificationViewModel.class);
        vm.getModelRunner().stopStreamClassification();
    }


    public LiveData<String> getLatency() {
        return latency;
    }

    public LiveData<String> getPredictions() {
        return predictions;
    }

    private void printTopKLabels(SpannableStringBuilder builder, float[] result, String[] labels) {
        // Keep a PriorityQueue with the top RESULTS_TO_SHOW predictions
        PriorityQueue<Map.Entry<String, Float>> sortedLabels =
                new PriorityQueue<>(
                        RESULTS_TO_SHOW,
                        (o1, o2) -> (o1.getValue()).compareTo(o2.getValue()));

        for (int i = 0; i < labels.length; ++i) {
            sortedLabels.add(new AbstractMap.SimpleEntry<>(labels[i], result[i]));
            if (sortedLabels.size() > RESULTS_TO_SHOW) {
                sortedLabels.poll();
            }
        }

        final int size = sortedLabels.size();

        for (int i = 0; i < size; i++) {
            Map.Entry<String, Float> label = sortedLabels.poll();
            SpannableString span =
                    new SpannableString(String.format("%s: %4.2f\n", label.getKey(), label.getValue()));

            // Make first item bigger.
            if (i == size - 1) {
                float sizeScale = (i == size - 1) ? 1.25f : 0.8f;
                span.setSpan(new RelativeSizeSpan(sizeScale), 0, span.length(), 0);
            }
            builder.insert(0, span);
        }
    }

    private void applyFilter(float[] result, int numLabels) {

        if (filterLabelProbArray == null || filterLabelProbArray[0].length != numLabels){
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
