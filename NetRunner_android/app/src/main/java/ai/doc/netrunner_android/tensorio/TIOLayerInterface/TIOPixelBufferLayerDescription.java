package ai.doc.netrunner_android.tensorio.TIOLayerInterface;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import ai.doc.netrunner_android.tensorio.TIOData.TIOPixelDenormalizer;
import ai.doc.netrunner_android.tensorio.TIOData.TIOPixelNormalizer;
import ai.doc.netrunner_android.tensorio.TIOModel.TIOVisionModel.TIOImageVolume;
import ai.doc.netrunner_android.tensorio.TIOModel.TIOVisionModel.TIOPixelFormat;

/**
 * The description of a pixel buffer input or output layer.
 */
public class TIOPixelBufferLayerDescription extends TIOLayerDescription {
    /**
     * `true` is the layer is quantized, `false` otherwise
     */
    private boolean quantized;

    /**
     * The pixel format of the image data, must be TIOPixelFormat.RGB or TIOPixelFormat.BGR
     */

    private TIOPixelFormat pixelFormat;

    /**
     * The shape of the pixel data, including width, height, and channels
     */

    private TIOImageVolume shape;

    /**
     * A function that normalizes pixel values from a byte range of `[0,255]` to some other
     * floating point range, may be `nil`.
     */

    private TIOPixelNormalizer normalizer;

    /**
     * A function that denormalizes pixel values from a floating point range back to byte values
     * in the range `[0,255]`, may be nil.
     */

    private TIOPixelDenormalizer denormalizer;

    private int[] intValues;
    private ByteBuffer buffer;

    /**
     * Creates a pixel buffer description from the properties parsed in a model.json file.
     *
     * @param pixelFormat  The expected format of the pixels
     * @param shape        The shape of the input image
     * @param normalizer   A function which normalizes the pixel values for an input layer, may be null.
     * @param denormalizer A function which denormalizes pixel values for an output layer, may be null
     * @param quantized    true if this layer expectes quantized values, false otherwise
     */
    public TIOPixelBufferLayerDescription(TIOPixelFormat pixelFormat, TIOImageVolume shape, TIOPixelNormalizer normalizer, TIOPixelDenormalizer denormalizer, boolean quantized) {
        this.pixelFormat = pixelFormat;
        this.shape = shape;
        this.normalizer = normalizer;
        this.denormalizer = denormalizer;
        this.quantized = quantized;

    }

    @Override
    public boolean isQuantized() {
        return quantized;
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
    public ByteBuffer toByteBuffer(Object o) {
        if (o == null) {
            throw new NullPointerException("Input to a model can not be null");
        } else if (!(o instanceof Bitmap)) {
            throw new IllegalArgumentException("Image input should be bitmap");
        }

        Bitmap bitmap = (Bitmap)o;

        if (this.intValues == null){
            this.intValues = new int[this.shape.width * this.shape.height];
        }

        if (buffer == null) {
            if (isQuantized()){
                this.buffer = ByteBuffer.allocateDirect(this.shape.width*this.shape.height*3);
            }
            else{
                this.buffer = ByteBuffer.allocateDirect(this.shape.width*this.shape.height*3*4);
            }
            this.buffer.order(ByteOrder.nativeOrder());
        }

        buffer.rewind();

        bitmap.getPixels(intValues, 0, bitmap.getWidth(), 0, 0, bitmap.getWidth(), bitmap.getHeight());

        // Convert the image to floating point.
        int pixel = 0;
        for (int i = 0; i < bitmap.getWidth(); ++i) {
            for (int j = 0; j < bitmap.getHeight(); ++j) {
                final int val = intValues[pixel++];
                addPixelValue(val, buffer);
            }
        }
        return buffer;
    }

    @Override
    public Bitmap fromByteBuffer(ByteBuffer buffer) {
        byte[] imageBytes= new byte[buffer.remaining()];
        buffer.get(imageBytes);
        return BitmapFactory.decodeByteArray(imageBytes,0,imageBytes.length);
    }

    @Override
    public ByteBuffer getBackingByteBuffer() {
        return buffer;
    }

    public TIOPixelFormat getPixelFormat() {
        return pixelFormat;
    }

    public TIOImageVolume getShape() {
        return shape;
    }

    public TIOPixelNormalizer getNormalizer() {
        return normalizer;
    }

    public TIOPixelDenormalizer getDenormalizer() {
        return denormalizer;
    }
}
