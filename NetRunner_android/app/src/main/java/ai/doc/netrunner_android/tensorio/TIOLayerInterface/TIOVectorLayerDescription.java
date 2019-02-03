package ai.doc.netrunner_android.tensorio.TIOLayerInterface;

import ai.doc.netrunner_android.tensorio.TIOData.TIODataDequantizer;
import ai.doc.netrunner_android.tensorio.TIOData.TIODataQuantizer;

/**
 * The description of a vector (array) input or output later.
 * <p>
 * Vector inputs and outputs are always unrolled vectors, and from the tensor's perspective they are
 * just an array of bytes. The total length of a vector will be the total volume of the layer.
 * For example, if an input layer is a tensor of shape `(24,24,2)`, the length of the vector will be
 * `24x24x2 = 1152`.
 * <p>
 * TensorFlow Lite models expect row major ordering of bytes, such that higher order dimensions are
 * traversed first. For example, a 2x4 matrix with the following values:
 *
 * <pre>
 * [[1 2 3 4]
 * [5 6 7 8]]
 * </pre>
 * <p>
 * <p>
 * should be unrolled and provided to the model as:
 *
 * <pre>
 * [1 2 3 4 5 6 7 8]
 * </pre>
 * <p>
 * i.e, start with the row and traverse the columns before moving to the next row.
 * <p>
 * Because output layers are also exposed as an array of bytes, a `TIOTFLiteModel` will always return
 * a vector in one dimension. If is up to you to reshape it if required.
 * <p>
 * WARNING: A `TIOVectorLayerDescription`'s length is different than the byte length of a `TIOData` object.
 * For example a quantized `TIOVector` (uint8_t) of length 4 will occupy 4 bytes of memory but an
 * unquantized `TIOVector` (float_t) of length 4 will occupy 16 bytes of memory.
 */

public class TIOVectorLayerDescription extends TIOLayerDescription {
    /**
     * The length of the vector in terms of its number of elements.
     */

    private int length;

    /**
     * Indexed labels corresponding to the indexed output of a layer. May be `nil`.
     * <p>
     * Labeling the output of a model is such a common operation that support for it is included
     * by default.
     */

    private String[] labels;

    /**
     * `YES` if there are labels associated with this layer, `NO` otherwise.
     */

    private boolean labeled;

    /**
     * A function that converts a vector from unquantized values to quantized values
     */

    private TIODataQuantizer quantizer;

    /**
     * A function that converts a vector from quantized values to unquantized values
     */

    private TIODataDequantizer dequantizer;

    /**
     * Designated initializer. Creates a vector description from the properties parsed in a model.json
     * file.
     *
     * @param length      The total number of elements in this layer.
     * @param labels      The indexed labels associated with the outputs of this layer. May be `nil`.
     * @param quantized   True if the values are quantized
     * @param quantizer   A function that transforms unquantized values to quantized input
     * @param dequantizer A function that transforms quantized output to unquantized values
     */

    public TIOVectorLayerDescription(int length, String[] labels, boolean quantized, TIODataQuantizer quantizer, TIODataDequantizer dequantizer) {
        this.length = length;
        this.labels = labels;
        this.quantized = quantized;
        this.quantizer = quantizer;
        this.dequantizer = dequantizer;

    }


    /**
     * Given the output vector of a tensor, returns labeled outputs using `labels`.
     *
     * @param vector A `TIOVector` of values.
     * @return NSDictionary The labeled values, where the dictionary keys are the labels and the
     * dictionary values are the associated vector values.
     * <p>
     * `labels` must not be `nil`.
     */
    /*
    public Map<String, Float> labeledValues(TIOVector vector) {
        return null;
    }
    */

}
