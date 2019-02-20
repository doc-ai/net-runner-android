package ai.doc.netrunner_android.tensorio.TIOTensorflowLiteModel;

import android.content.Context;
import android.content.res.AssetFileDescriptor;

import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.experimental.GpuDelegate;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.Map;

import ai.doc.netrunner_android.tensorio.TIOLayerInterface.TIOLayerDescription;
import ai.doc.netrunner_android.tensorio.TIOModel.TIOModel;
import ai.doc.netrunner_android.tensorio.TIOModel.TIOModelBundle;
import ai.doc.netrunner_android.tensorio.TIOModel.TIOModelException;

public class TIOTFLiteModel extends TIOModel {

    private Interpreter tflite;
    private MappedByteBuffer tfliteModel;
    private GpuDelegate gpuDelegate = null;

    private int numThreads = 1;
    private boolean useGPU = false;
    private boolean useNNAPI = false;
    private boolean use16bit = false;

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
        tflite = new Interpreter(tfliteModel);
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

        // Fetch the input and output layer descriptions from the model
        TIOLayerDescription inputLayer = getBundle().getIndexedInputInterfaces().get(0).getDataDescription();
        TIOLayerDescription outputLayer = getBundle().getIndexedOutputInterfaces().get(0).getDataDescription();

        // Ask the input layer to parse the input object into a Bytebuffer
        ByteBuffer inputBuffer = inputLayer.toByteBuffer(input);

        // Ask the output layer for a buffer to store the output in
        ByteBuffer outputBuffer = outputLayer.getBackingByteBuffer();

        // Run the model on the input buffer, store the output in the outputbuffer
        tflite.run(inputBuffer, outputBuffer);

        // Ask the outputlayer to convert the outputbuffer back to an object to return to the user
        return outputLayer.fromByteBuffer(outputBuffer);
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
            if (gpuDelegate != null) {
                gpuDelegate.close();
            }

            Interpreter.Options tfliteOptions = new Interpreter.Options();
            tfliteOptions.setAllowFp16PrecisionForFp32(use16bit);
            tfliteOptions.setUseNNAPI(useNNAPI);
            tfliteOptions.setNumThreads(numThreads);
            if (useGPU && GpuDelegateHelper.isGpuDelegateAvailable()){
                tfliteOptions.addDelegate((GpuDelegate)GpuDelegateHelper.createGpuDelegate());
            }

            tflite = new Interpreter(tfliteModel, tfliteOptions);
        }
    }

    public void useGPU() {
        if (GpuDelegateHelper.isGpuDelegateAvailable()){
            useGPU = true;
            recreateInterpreter();
        }

    }

    public void useCPU() {
        useGPU = false;
        useNNAPI = false;
        recreateInterpreter();
    }

    public void useNNAPI() {
        useGPU = false;
        useNNAPI = true;
        recreateInterpreter();
    }

    public void setNumThreads(int numThreads) {
        this.numThreads = numThreads;
        recreateInterpreter();
    }

    public void setAllow16BitPrecision(boolean use16Bit) {
        this.use16bit = use16Bit;
        recreateInterpreter();
    }


}
