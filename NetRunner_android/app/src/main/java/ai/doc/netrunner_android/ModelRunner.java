package ai.doc.netrunner_android;

import android.graphics.Bitmap;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.text.SpannableStringBuilder;
import android.util.Log;
import android.view.Display;

import ai.doc.netrunner_android.tensorio.TIOLayerInterface.TIOPixelBufferLayerDescription;
import ai.doc.netrunner_android.tensorio.TIOLayerInterface.TIOVectorLayerDescription;
import ai.doc.netrunner_android.tensorio.TIOModel.TIOModelException;
import ai.doc.netrunner_android.tensorio.TIOTensorflowLiteModel.GpuDelegateHelper;
import ai.doc.netrunner_android.tensorio.TIOTensorflowLiteModel.TIOTFLiteModel;


public class ModelRunner {
    private static final String TAG = "ModelRunner";

    private String[] labels;
    private int inputWidth;
    private int inputHeight;

    private int numThreads = 1;
    private boolean use16Bit;
    private Device device;

    private ModelRunnerDataSource dataSource;
    private ClassificationResultListener listener;

    private TIOTFLiteModel classifier;

    private Handler backgroundHandler;
    private static final String HANDLE_THREAD_NAME = "ClassificationThread";

    private final Object lock = new Object();
    private boolean runClassifier = false;

    public interface ModelRunnerDataSource {
        Bitmap getNextInput(int size_x, int size_y);
    }

    public interface ClassificationResultListener {
        void classificationResult(int requestId, Object prediction, long latency);
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

        this.labels = ((TIOVectorLayerDescription) classifier.getOutputs().get(0).getDataDescription()).getLabels();
        this.inputWidth = ((TIOPixelBufferLayerDescription) classifier.getInputs().get(0).getDataDescription()).getShape().width;
        this.inputHeight = ((TIOPixelBufferLayerDescription) classifier.getInputs().get(0).getDataDescription()).getShape().height;

        HandlerThread backgroundThread = new HandlerThread(HANDLE_THREAD_NAME);
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

                            Bitmap bitmap = dataSource.getNextInput(inputWidth, inputHeight);
                            if (bitmap != null) {
                                try {
                                    // run inference
                                    long startTime = SystemClock.uptimeMillis();
                                    Object result = classifier.runOn(bitmap);
                                    long endTime = SystemClock.uptimeMillis();

                                    listener.classificationResult(-1, result, endTime - startTime);

                                } catch (TIOModelException e) {
                                    e.printStackTrace();
                                }
                                bitmap.recycle();
                            }
                        }
                        try {
                            backgroundHandler.post(periodicClassify);
                        } catch (IllegalStateException e) {
                            Log.i(TAG, e.getLocalizedMessage());
                        }
                    }
                }
            };

    public String[] getLabels() {
        return labels;
    }


    public void classifyFrame(int requestId, Bitmap frame, ClassificationResultListener listener) {
        backgroundHandler.post(() -> {
            SpannableStringBuilder predictionsBuilder = new SpannableStringBuilder();
            SpannableStringBuilder latencyBuilder = new SpannableStringBuilder();

            try {
                long startTime = SystemClock.uptimeMillis();
                float[] result = (float[]) classifier.runOn(frame);
                long endTime = SystemClock.uptimeMillis();

                listener.classificationResult(requestId, result, endTime - startTime);
            } catch (TIOModelException e) {
                e.printStackTrace();
            }
        });
    }

    public void startStreamClassification(ModelRunnerDataSource dataSource, ClassificationResultListener listener) {
        synchronized (lock){
            backgroundHandler.removeCallbacksAndMessages(null);

            ModelRunner.this.dataSource = dataSource;
            ModelRunner.this.listener = listener;

            runClassifier = true;

            backgroundHandler.post(periodicClassify);
        }
    }

    public void stopStreamClassification() {
        synchronized (lock) {
            runClassifier = false;
            backgroundHandler.removeCallbacksAndMessages(null);
        }
    }

    public void switchModel(TIOTFLiteModel newModel) throws TIOModelException {
        switchModel(newModel, this.device == Device.GPU, this.device == Device.NNAPI, this.numThreads, this.use16Bit);
    }

    public void switchModel(TIOTFLiteModel newModel, boolean useGPU, boolean useNNAPI, int numThreads, boolean use16Bit){
        synchronized (lock){
            classifier.unload();
            classifier = newModel;

            ModelRunner.this.labels = ((TIOVectorLayerDescription) classifier.getOutputs().get(0).getDataDescription()).getLabels();
            ModelRunner.this.inputWidth = ((TIOPixelBufferLayerDescription) classifier.getInputs().get(0).getDataDescription()).getShape().width;
            ModelRunner.this.inputHeight = ((TIOPixelBufferLayerDescription) classifier.getInputs().get(0).getDataDescription()).getShape().height;

            ModelRunner.this.use16Bit = use16Bit;

            ModelRunner.this.device = Device.CPU;
            if (useGPU && GpuDelegateHelper.isGpuDelegateAvailable()){
                ModelRunner.this.device = Device.GPU;
            }
            else if (useNNAPI){
                ModelRunner.this.device = Device.NNAPI;
            }

            ModelRunner.this.numThreads = numThreads;

            classifier.setOptions(use16Bit, useGPU, useNNAPI, numThreads);
        }
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

    public int getInputWidth() {
        return inputWidth;
    }

    public int getInputHeight() {
        return inputHeight;
    }
}
