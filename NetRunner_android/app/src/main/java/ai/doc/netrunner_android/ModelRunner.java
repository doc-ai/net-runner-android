package ai.doc.netrunner_android;

import android.arch.lifecycle.LiveData;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.HandlerThread;
import android.text.SpannableStringBuilder;
import android.util.Log;


public class ModelRunner extends LiveData<ModelPrediction> {
    private int numThreads;
    private boolean use16Bit;
    private ModelRunnerDataSource dataSource;
    private ClassificationResultListener listener;
    private Device device;
    private static final String TAG = "TfLiteCameraDemo";
    private ImageClassifier classifier;
    private HandlerThread backgroundThread;
    private Handler backgroundHandler;
    private static final String HANDLE_THREAD_NAME = "CameraBackground";
    private final Object lock = new Object();
    private boolean runClassifier = false;


    public interface ModelRunnerDataSource {
        Bitmap getNextInput(int size_x, int size_y);
    }

    public interface ClassificationResultListener {
        void classificationResult(int requestId, ModelPrediction result);
    }

    public class UnsupportedConfigurationException extends RuntimeException {
        public UnsupportedConfigurationException(String message) {
            super(message);
        }
    }

    public enum Device {
        CPU, GPU, NNAPI
    }

    public ModelRunner(ImageClassifier classifier) {
        this.classifier = classifier;
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

                            Bitmap bitmap = dataSource.getNextInput(classifier.getImageSizeX(), classifier.getImageSizeY());
                            if (bitmap != null) {
                                classifier.classifyFrame(bitmap, predictionsBuilder, latencyBuilder);
                                bitmap.recycle();
                                ModelPrediction prediction = new ModelPrediction();
                                prediction.setPrediction(predictionsBuilder.toString());
                                prediction.setLatency(latencyBuilder.toString());
                                listener.classificationResult(-1, prediction);
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


    public void classifyFrame(int requestId, Bitmap frame, ClassificationResultListener listener) {
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
        backgroundThread.quitSafely();
        try {
            backgroundThread.join();
            backgroundThread = null;
            backgroundHandler = null;
            synchronized (lock) {
                runClassifier = false;
            }
            backgroundThread = new HandlerThread(HANDLE_THREAD_NAME);
            backgroundThread.start();
            backgroundHandler = new Handler(backgroundThread.getLooper());
            this.dataSource = null;
            this.listener = null;
        } catch (InterruptedException e) {
            Log.e(TAG, "Interrupted when stopping background thread", e);
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

    public void close() {
        //backgroundHandler.removeCallbacksAndMessages(null);
        //backgroundHandler.post(() -> {
        //    classifier.close();
        //});
    }
}
