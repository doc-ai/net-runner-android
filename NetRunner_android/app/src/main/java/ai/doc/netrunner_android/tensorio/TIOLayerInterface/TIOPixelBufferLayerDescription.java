package ai.doc.netrunner_android.tensorio.TIOLayerInterface;

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
     * The pixel format of the image data, must be PixelFormat.RGB or kPixelFormat.BGR
     */

    private TIOPixelFormat pixelFormat;

    /**
     * The shape of the pixel data, including width, height, and channels
     */

    private TIOImageVolume shape;

    /**
     * A function that normalizes pixel values from a uint8_t range of `[0,255]` to some other
     * floating point range, may be `nil`.
     */

    private TIOPixelNormalizer normalizer;

    /**
     * A function that denormalizes pixel values from a floating point range back to uint8_t values
     * in the range `[0,255]`, may be nil.
     */

    private TIOPixelDenormalizer denormalizer ;

/**
 * Designated initializer. Creates a pixel buffer description from the properties parsed in a
 * model.json file.
 *
 * @param pixelFormat The expected format of the pixels
 * @param shape The shape of the input image
 * @param normalizer A function which normalizes the pixel values for an input layer, may be `nil`.
 * @param denormalizer A function which denormalizes pixel values for an output layer, may be `nil`
 * @param quantized `YES` if this layer expectes quantized values, `NO` otherwise
 *
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
