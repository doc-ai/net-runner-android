package ai.doc.netrunner_android;

import android.graphics.Bitmap;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.text.SpannableStringBuilder;

import java.util.Map;

import ai.doc.tensorio.TIOLayerInterface.TIOPixelBufferLayerDescription;
import ai.doc.tensorio.TIOLayerInterface.TIOVectorLayerDescription;
import ai.doc.tensorio.TIOModel.TIOModelException;
import ai.doc.tensorio.TIOTFLiteModel.GpuDelegateHelper;
import ai.doc.tensorio.TIOTFLiteModel.TIOTFLiteModel;

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

    private TIOTFLiteModel model;

    private Handler backgroundHandler;
    private static final String HANDLE_THREAD_NAME = "ClassificationThread";

    private final Object lock = new Object();
    private boolean running = false;

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

    // TODO: Vector output labeling should take place within TensorIO (tensorio-android #26)

    public ModelRunner(TIOTFLiteModel model) {
        this.model = model;

        this.labels = ((TIOVectorLayerDescription) this.model.getIO().getOutputs().get(0).getLayerDescription()).getLabels();
        this.inputWidth = ((TIOPixelBufferLayerDescription) this.model.getIO().getInputs().get(0).getLayerDescription()).getShape().width;
        this.inputHeight = ((TIOPixelBufferLayerDescription) this.model.getIO().getInputs().get(0).getLayerDescription()).getShape().height;

        HandlerThread backgroundThread = new HandlerThread(HANDLE_THREAD_NAME);
        backgroundThread.start();

        backgroundHandler = new Handler(backgroundThread.getLooper());

        backgroundHandler.post(() -> {
            try {
                ModelRunner.this.model.load();
            } catch (TIOModelException e) {
                e.printStackTrace();
            }
        });
    }

    private Runnable periodicClassify =
            new Runnable() {
                @Override
                public void run() {
                    synchronized (lock) {
                        if (running) {
                            Bitmap bitmap = dataSource.getNextInput(inputWidth, inputHeight);
                            if (bitmap != null) {
                                try {
                                    // run inference
                                    long startTime = SystemClock.uptimeMillis();
                                    Object result = model.runOn(bitmap);
                                    long endTime = SystemClock.uptimeMillis();

                                    listener.classificationResult(-1, result, endTime - startTime);

                                } catch (TIOModelException e) {
                                    e.printStackTrace();
                                }
                                bitmap.recycle();
                            }
                            backgroundHandler.post(periodicClassify);
                        }
                    }
                }
            };

    public String[] getLabels() {
        return labels;
    }

    // TODO: Classify frame assumes we are running a classification model
    // This whole class assumes we are running a classification model

    public void classifyFrame(int requestId, Bitmap frame, ClassificationResultListener listener) {
        backgroundHandler.post(() -> {
            SpannableStringBuilder predictionsBuilder = new SpannableStringBuilder();
            SpannableStringBuilder latencyBuilder = new SpannableStringBuilder();

            try {
                long startTime = SystemClock.uptimeMillis();
                Map<String,Object> output = model.runOn(frame);
                long endTime = SystemClock.uptimeMillis();
                listener.classificationResult(requestId, output, endTime - startTime);
            } catch (TIOModelException e) {
                e.printStackTrace();
            }
        });
    }

    public void startStreamClassification(ModelRunnerDataSource dataSource, ClassificationResultListener listener) {
        synchronized (lock){
            ModelRunner.this.dataSource = dataSource;
            ModelRunner.this.listener = listener;

            running = true;

            backgroundHandler.post(periodicClassify);
        }
    }

    public void stopStreamClassification() {
        synchronized (lock) {
            running = false;
            listener = null;
            dataSource = null;
        }
    }

    public void switchModel(TIOTFLiteModel newModel) throws TIOModelException {
        switchModel(newModel, this.device == Device.GPU, this.device == Device.NNAPI, this.numThreads, this.use16Bit);
    }

    public void switchModel(TIOTFLiteModel model, boolean useGPU, boolean useNNAPI, int numThreads, boolean use16Bit){
        backgroundHandler.post(() -> {
            synchronized (lock){
                this.model.unload();
                this.model = model;

                try {
                    this.model.load();
                } catch (TIOModelException e) {
                    e.printStackTrace();
                }

                ModelRunner.this.labels = ((TIOVectorLayerDescription) this.model.getIO().getOutputs().get(0).getLayerDescription()).getLabels();
                ModelRunner.this.inputWidth = ((TIOPixelBufferLayerDescription) this.model.getIO().getInputs().get(0).getLayerDescription()).getShape().width;
                ModelRunner.this.inputHeight = ((TIOPixelBufferLayerDescription) this.model.getIO().getInputs().get(0).getLayerDescription()).getShape().height;

                ModelRunner.this.use16Bit = use16Bit;

                ModelRunner.this.device = Device.CPU;
                if (useGPU && GpuDelegateHelper.isGpuDelegateAvailable()){
                    ModelRunner.this.device = Device.GPU;
                }
                else if (useNNAPI){
                    ModelRunner.this.device = Device.NNAPI;
                }

                ModelRunner.this.numThreads = numThreads;

                this.model.setOptions(use16Bit, useGPU, useNNAPI, numThreads);
            }
        });
    }

    public void useGPU() {
        if (this.device != Device.GPU) {
            backgroundHandler.post(() -> {
                if (!GpuDelegateHelper.isGpuDelegateAvailable()) {
                    this.device = Device.CPU;
                    throw new UnsupportedConfigurationException("GPU not supported in this build");
                } else {
                    model.useGPU();
                    this.device = Device.GPU;
                }
            });
        }
    }

    public void useCPU() {
        if (this.device != Device.CPU) {
            backgroundHandler.post(() -> {
                model.useCPU();
                this.device = Device.CPU;
            });
        }

    }

    public void useNNAPI() {
        if (this.device != Device.NNAPI) {
            backgroundHandler.post(() -> {
                model.useNNAPI();
                this.device = Device.NNAPI;
            });
        }
    }

    public void setNumThreads(int numThreads) {
        if (this.numThreads != numThreads) {
            backgroundHandler.post(() -> {
                model.setNumThreads(numThreads);
                this.numThreads = numThreads;
            });
        }
    }

    public void setUse16bit(boolean use16Bit) {
        if (this.use16Bit != use16Bit) {
            backgroundHandler.post(() -> {
                model.setAllow16BitPrecision(use16Bit);
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
