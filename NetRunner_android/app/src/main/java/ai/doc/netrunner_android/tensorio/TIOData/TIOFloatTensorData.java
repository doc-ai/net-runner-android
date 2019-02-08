package ai.doc.netrunner_android.tensorio.TIOData;

import android.util.Log;

import java.lang.reflect.Array;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.Arrays;

public class TIOFloatTensorData implements TIOData<float[]> {
    private int volume;
    private int[] shape;
    private ByteBuffer buffer;

    public TIOFloatTensorData(int[] shape) {
        this.shape = shape;
        this.volume = 1;
        for (int s : shape) {
            this.volume *= s;
        }
        this.buffer = ByteBuffer.allocate(volume*4);
        this.buffer.order(ByteOrder.nativeOrder());
    }

    @Override
    public ByteBuffer getByteBuffer() {
        return buffer;
    }

    @Override
    public void putData(float[] data) {
        buffer.rewind();
        FloatBuffer f = buffer.asFloatBuffer();
        f.put(data);
    }

    @Override
    public float[] getData() {
        float[] result = new float[volume];
        Log.i("result", Arrays.toString(result));
        buffer.rewind();
        Log.i("result", buffer.toString());
        Log.i("result", ""+buffer.asFloatBuffer().get());
        buffer.asFloatBuffer().get(result);
        return result;
    }


}
