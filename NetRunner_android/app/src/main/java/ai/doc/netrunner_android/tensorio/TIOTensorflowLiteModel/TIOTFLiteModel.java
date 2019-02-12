package ai.doc.netrunner_android.tensorio.TIOTensorflowLiteModel;

import android.content.Context;
import android.content.res.AssetFileDescriptor;

import org.tensorflow.lite.Delegate;
import org.tensorflow.lite.Interpreter;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.Map;

import ai.doc.netrunner_android.tensorio.TIOData.TIOFloatTensorData;
import ai.doc.netrunner_android.tensorio.TIOModel.TIOModel;
import ai.doc.netrunner_android.tensorio.TIOModel.TIOModelBundle;
import ai.doc.netrunner_android.tensorio.TIOModel.TIOModelException;

public class TIOTFLiteModel extends TIOModel {

    private Interpreter tflite;
    private MappedByteBuffer tfliteModel;
    private final Interpreter.Options tfliteOptions = new Interpreter.Options();
    private Delegate gpuDelegate = null;

    public TIOTFLiteModel(Context context, TIOModelBundle bundle) {
        super(context, bundle);
    }


    @Override
    public void load() throws TIOModelException {
        try {
            tfliteModel = loadModelFile(getContext(), getBundle().getModelFilePath());
        } catch (IOException e) {
            throw new TIOModelException("Error loading model file", e);
        }
        tflite = new Interpreter(tfliteModel, tfliteOptions);
        super.load();
    }

    @Override
    public void unload() {
        tflite.close();
        super.unload();
    }

    @Override
    public Object runOn(Map<String, Object> input) throws TIOModelException {
        return super.runOn(input);
    }

    @Override
    public Object runOn(Object input) throws TIOModelException {
        super.runOn(input);
        TIOFloatTensorData output = new TIOFloatTensorData(new int[]{1001});
        ByteBuffer inputBuffer = getBundle().getIndexedInputInterfaces().get(0).getDataDescription().toByteBuffer(input);
        tflite.run(inputBuffer, output.getByteBuffer());
        return output.getData();
    }

    private MappedByteBuffer loadModelFile(Context context, String path) throws IOException {
        AssetFileDescriptor fileDescriptor = context.getAssets().openFd(path);
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }

    private void recreateInterpreter() {
        if (tflite != null) {
            tflite.close();
            // TODO(b/120679982)
            //gpuDelegate.close();
            tflite = new Interpreter(tfliteModel, tfliteOptions);
        }
    }

    public void useGPU() {
        if (gpuDelegate == null && GpuDelegateHelper.isGpuDelegateAvailable()) {
            gpuDelegate = GpuDelegateHelper.createGpuDelegate();
            tfliteOptions.addDelegate(gpuDelegate);
            recreateInterpreter();
        }
    }

    public void useCPU() {
        tfliteOptions.setUseNNAPI(false);
        recreateInterpreter();
    }

    public void useNNAPI() {
        tfliteOptions.setUseNNAPI(true);
        recreateInterpreter();
    }

    public void setNumThreads(int numThreads) {
        tfliteOptions.setNumThreads(numThreads);
        recreateInterpreter();
    }

    public void setAllow16BitPrecision(boolean use16Bit) {
        tfliteOptions.setAllowFp16PrecisionForFp32(use16Bit);
        recreateInterpreter();
    }


}
