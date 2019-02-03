package ai.doc.netrunner_android.tensorio.TIOLayerInterface;

/**
 * Describes an input or output layer. Used internally by a model when parsing its description.
 * <p>
 * A layer description encapsulates information about an input or output tensor that is needed
 * to prepare obj-c data and copy bytes into and out of it. For example, a vector layer description
 * for an input tensor describes any transformations the submitted data must undergo before the
 * underlying bytes are copied to the tensor, e.g. quantization and normalization, as well as the
 * shape of the expected input, which determines how many bytes are copied into the tensor.
 */
public abstract class TIOLayerDescription {
    /**
     * true if this data is quantized (bytes of type uint8_t), false if not (bytes of type float_t)
     */
    protected boolean quantized;

    public boolean isQuantized() {
        return quantized;
    }
}
