package ai.doc.netrunner_android.tensorio.TIOLayerInterface;

import android.graphics.Bitmap;

import java.nio.ByteBuffer;

import ai.doc.netrunner_android.tensorio.TIOData.TIOBitmapData;
import ai.doc.netrunner_android.tensorio.TIOData.TIOData;
import ai.doc.netrunner_android.tensorio.TIOData.TIOPixelDenormalizer;
import ai.doc.netrunner_android.tensorio.TIOData.TIOPixelNormalizer;
import ai.doc.netrunner_android.tensorio.TIOModel.TIOVisionModel.TIOImageVolume;
import ai.doc.netrunner_android.tensorio.TIOModel.TIOVisionModel.TIOPixelFormat;

/**
 * The description of a pixel buffer input or output layer.
 */
public class TIOPixelBufferLayerDescription extends TIOLayerDescription {
    private final TIOBitmapData data;
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
        this.data = new TIOBitmapData(this.normalizer, this.denormalizer);
    }

    @Override
    public boolean isQuantized() {
        return quantized;
    }

    @Override
    public ByteBuffer toByteBuffer(Object o) {
        if (o == null) {
            throw new NullPointerException("Input to a model can not be null");
        } else if (!(o instanceof Bitmap)) {
            throw new IllegalArgumentException("Image input should be bitmap");
        }
        this.data.putData((Bitmap) o);
        return this.data.getBackingByteBuffer();
    }

    @Override
    public Bitmap fromByteBuffer(ByteBuffer buffer) {
        return null;
    }

    @Override
    public ByteBuffer getBackingByteBuffer() {
        return data.getBackingByteBuffer();
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
