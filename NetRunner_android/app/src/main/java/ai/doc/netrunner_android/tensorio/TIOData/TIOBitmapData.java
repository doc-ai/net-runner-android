package ai.doc.netrunner_android.tensorio.TIOData;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class TIOBitmapData implements TIOData<Bitmap> {
    private final TIOPixelNormalizer normalizer;
    private final TIOPixelDenormalizer denormalizer;
    private int[] intValues;
    private ByteBuffer buffer;

    public TIOBitmapData(TIOPixelNormalizer normalizer, TIOPixelDenormalizer denormalizer) {
        this.normalizer = normalizer;
        this.denormalizer = denormalizer;
    }

    @Override
    public ByteBuffer getByteBuffer() {
        return buffer;
    }

    @Override
    public void putData(Bitmap data) {
        if (this.intValues == null){
            this.intValues = new int[data.getWidth()*data.getHeight()];
        }
        if (buffer == null) {
            this.buffer = ByteBuffer.allocateDirect(data.getWidth()*data.getHeight()*3*4);
            this.buffer.order(ByteOrder.nativeOrder());
        }
        buffer.rewind();
        data.getPixels(intValues, 0, data.getWidth(), 0, 0, data.getWidth(), data.getHeight());

        // Convert the image to floating point.
        int pixel = 0;
        for (int i = 0; i < data.getWidth(); ++i) {
            for (int j = 0; j < data.getHeight(); ++j) {
                final int val = intValues[pixel++];
                addPixelValue(val, buffer);
            }
        }
    }

    private void addPixelValue(int pixelValue, ByteBuffer imgData){
        if (this.normalizer != null){
            imgData.putFloat(this.normalizer.normalize((pixelValue >> 16) & 0xFF, 0));
            imgData.putFloat(this.normalizer.normalize((pixelValue >> 8) & 0xFF, 1));
            imgData.putFloat(this.normalizer.normalize(pixelValue & 0xFF, 2));
        }
        else{
            imgData.putFloat((pixelValue >> 16) & 0xFF);
            imgData.putFloat((pixelValue >> 8) & 0xFF);
            imgData.putFloat(pixelValue & 0xFF);
        }

    }

    @Override
    public Bitmap getData() {
        byte[] imageBytes= new byte[buffer.remaining()];
        buffer.get(imageBytes);
        return BitmapFactory.decodeByteArray(imageBytes,0,imageBytes.length);
    }
}
