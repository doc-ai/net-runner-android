package ai.doc.netrunner_android;

import android.graphics.Bitmap;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.style.RelativeSizeSpan;
import android.util.Log;

import java.util.AbstractMap;
import java.util.Map;
import java.util.PriorityQueue;

import ai.doc.netrunner_android.tensorio.TIOLayerInterface.TIOPixelBufferLayerDescription;
import ai.doc.netrunner_android.tensorio.TIOLayerInterface.TIOVectorLayerDescription;
import ai.doc.netrunner_android.tensorio.TIOModel.TIOModel;
import ai.doc.netrunner_android.tensorio.TIOModel.TIOModelException;
import ai.doc.netrunner_android.tensorio.TIOTensorflowLiteModel.GpuDelegateHelper;
import ai.doc.netrunner_android.tensorio.TIOTensorflowLiteModel.TIOTFLiteModel;


public class ModelRunner {
    private static final String TAG = "TfLiteCameraDemo";

    private String[] labels;
    private int inputWidth;
    private int inputHeight;

    private int numThreads = 1;
    private boolean use16Bit;
    private Device device;

    private ModelRunnerDataSource dataSource;
    private ClassificationResultListener listener;

    private TIOTFLiteModel classifier;

    private HandlerThread backgroundThread;
    private Handler backgroundHandler;
    private static final String HANDLE_THREAD_NAME = "CameraBackground";

    private final Object lock = new Object();
    private boolean runClassifier = false;
    private float[][] filterLabelProbArray = null;

    private static final int FILTER_STAGES = 3;
    private static final float FILTER_FACTOR = 0.4f;

    private static final int RESULTS_TO_SHOW = 3;
    private PriorityQueue<Map.Entry<String, Float>> sortedLabels =
            new PriorityQueue<>(
                    RESULTS_TO_SHOW,
                    (o1, o2) -> (o1.getValue()).compareTo(o2.getValue()));


    public interface ModelRunnerDataSource {
        Bitmap getNextInput(int size_x, int size_y);
    }

    public interface ClassificationResultListener {
        void classificationResult(int requestId, String prediction, String latency);
    }

    public class UnsupportedConfigurationException extends RuntimeException {
        public UnsupportedConfigurationException(String message) {
            super(message);
        }
    }

    public enum Device {
        CPU, GPU, NNAPI
    }

    public ModelRunner(TIOTFLiteModel classifier) {
        this.classifier = classifier;
        this.labels = ((TIOVectorLayerDescription)classifier.getOutputs().get(0).getDataDescription()).getLabels();
        this.inputWidth = ((TIOPixelBufferLayerDescription)classifier.getInputs().get(0).getDataDescription()).getShape().width;
        this.inputHeight = ((TIOPixelBufferLayerDescription)classifier.getInputs().get(0).getDataDescription()).getShape().height;

        filterLabelProbArray = new float[FILTER_STAGES][getNumLabels()];

        backgroundThread = new HandlerThread(HANDLE_THREAD_NAME);
        backgroundThread.start();
        backgroundHandler = new Handler(backgroundThread.getLooper());
    }

    private Runnable periodicClassify =
            new Runnable() {
                @Override
                public void run() {
                    synchronized (lock) {
                        if (runClassifier) {
                            if (classifier == null) {
                                Log.e(TAG, "Uninitialized Classifier or invalid context.");
                                return;
                            }
                            SpannableStringBuilder predictionsBuilder = new SpannableStringBuilder();
                            SpannableStringBuilder latencyBuilder = new SpannableStringBuilder();

                            Bitmap bitmap = dataSource.getNextInput(inputWidth, inputHeight);
                            if (bitmap != null) {
                                try {

                                    // run inference
                                    long startTime = SystemClock.uptimeMillis();
                                    float[] resultArray;
                                    Object result = classifier.runOn(bitmap);
                                    long endTime = SystemClock.uptimeMillis();
                                    resultArray = (float[])result;


                                    // Smooth the results across frames.
                                    applyFilter(resultArray);

                                    // Show the prediction
                                    printTopKLabels(predictionsBuilder,resultArray);

                                    // Show the latency
                                    long duration = endTime - startTime;
                                    latencyBuilder.append(new SpannableString(duration + " ms"));

                                } catch (TIOModelException e) {
                                    e.printStackTrace();
                                }
                                bitmap.recycle();

                                listener.classificationResult(-1, predictionsBuilder.toString(), latencyBuilder.toString());
                            }
                        }
                    }
                    try {
                        if (backgroundThread.isAlive()) {
                            backgroundHandler.post(periodicClassify);
                        }
                    } catch (IllegalStateException e) {
                        Log.i(TAG, e.getLocalizedMessage());
                    }

                }
            };

    private void applyFilter(float[] result) {
        int numLabels = getNumLabels();

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

    private void printTopKLabels(SpannableStringBuilder builder, float[] result) {
        // Keep a PriorityQueue with the top RESULTS_TO_SHOW predictions
        for (int i = 0; i < getNumLabels(); ++i) {
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

    private int getNumLabels() {
        return this.labels.length;
    }


    public void classifyFrame(int requestId, Bitmap frame, ClassificationResultListener listener) {
        backgroundHandler.post(() -> {
            SpannableStringBuilder predictionsBuilder = new SpannableStringBuilder();
            SpannableStringBuilder latencyBuilder = new SpannableStringBuilder();

            try {
                long startTime = SystemClock.uptimeMillis();
                float[] result = (float[])classifier.runOn(frame);
                long endTime = SystemClock.uptimeMillis();

                // Show the prediction
                printTopKLabels(predictionsBuilder,result);

                // Show the latency
                long duration = endTime - startTime;
                latencyBuilder.append(new SpannableString(duration + " ms"));

                listener.classificationResult(requestId, predictionsBuilder.toString(), latencyBuilder.toString());
            } catch (TIOModelException e) {
                e.printStackTrace();
            }
        });
    }

    public void startStreamClassification(ModelRunnerDataSource dataSource, ClassificationResultListener listener) {
        this.dataSource = dataSource;
        this.listener = listener;

        synchronized (lock) {
            runClassifier = true;
        }

        backgroundHandler.post(periodicClassify);
    }

    public void stopStreamClassification() {
        backgroundHandler.postAtFrontOfQueue(new Runnable() {
            @Override
            public void run() {
                backgroundHandler.removeCallbacksAndMessages(null);
                ModelRunner.this.dataSource = null;
                ModelRunner.this.listener = null;
            }
        });
    }

    public void switchModel(TIOTFLiteModel newModel) throws TIOModelException {
        backgroundHandler.post(() -> {
            classifier.unload();
            classifier = newModel;

            ModelRunner.this.labels = ((TIOVectorLayerDescription)classifier.getOutputs().get(0).getDataDescription()).getLabels();
            ModelRunner.this.inputWidth = ((TIOPixelBufferLayerDescription)classifier.getInputs().get(0).getDataDescription()).getShape().width;
            ModelRunner.this.inputHeight = ((TIOPixelBufferLayerDescription)classifier.getInputs().get(0).getDataDescription()).getShape().height;

            filterLabelProbArray = new float[FILTER_STAGES][getNumLabels()];

            classifier.setOptions(use16Bit, this.device == Device.GPU, this.device == Device.NNAPI, this.numThreads);
        });

    }

    public void useGPU() {
        if (this.device != Device.GPU) {
            backgroundHandler.post(() -> {
                if (!GpuDelegateHelper.isGpuDelegateAvailable()) {
                    this.device = Device.CPU;
                    throw new UnsupportedConfigurationException("GPU not supported in this build");
                } else {
                    classifier.useGPU();
                    this.device = Device.GPU;
                }
            });
        }
    }

    public void useCPU() {
        if (this.device != Device.CPU) {
            backgroundHandler.post(() -> {
                classifier.useCPU();
                this.device = Device.CPU;
            });
        }

    }

    public void useNNAPI() {
        if (this.device != Device.NNAPI) {
            backgroundHandler.post(() -> {
                classifier.useNNAPI();
                this.device = Device.NNAPI;
            });
        }
    }

    public void setNumThreads(int numThreads) {
        if (this.numThreads != numThreads) {
            backgroundHandler.post(() -> {
                classifier.setNumThreads(numThreads);
                this.numThreads = numThreads;
            });
        }
    }

    public void setUse16bit(boolean use16Bit) {
        if (this.use16Bit != use16Bit) {
            backgroundHandler.post(() -> {
                classifier.setAllow16BitPrecision(use16Bit);
                this.use16Bit = use16Bit;
            });
        }
    }

    public void close() {
        backgroundHandler.removeCallbacksAndMessages(null);
        backgroundHandler.post(() -> {
            classifier.unload();
        });
    }

    public int getInputWidth() {
        return inputWidth;
    }

    public int getInputHeight() {
        return inputHeight;
    }
}
