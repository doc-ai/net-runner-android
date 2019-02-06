package ai.doc.netrunner_android;

import org.junit.Test;

import ai.doc.netrunner_android.tensorio.TIOData.TIODataDequantizer;
import ai.doc.netrunner_android.tensorio.TIOData.TIODataQuantizer;

import static junit.framework.TestCase.assertEquals;
import static org.junit.Assert.assertTrue;

public class QuantizationDequantizationTests {
    @Test
    public void testDataQuantizerStandardZeroToOne() {

        int epsilon = 1;

        TIODataQuantizer quantizer = TIODataQuantizer.TIODataQuantizerZeroToOne();


        assertTrue(quantizer.quantize(0) == 0);
        assertTrue(quantizer.quantize(1) == 255);
        assertEquals(quantizer.quantize(0.5f), 127, epsilon);
    }

    @Test
    public void testDataQuantizerStandardNegativeOneToOne() {
        float epsilon = 1;
        TIODataQuantizer quantizer = TIODataQuantizer.TIODataQuantizerNegativeOneToOne();


        assertTrue(quantizer.quantize(-1) == 0);
        assertTrue(quantizer.quantize(1) == 255);
        assertEquals(quantizer.quantize(0), 127, epsilon);
    }

    @Test
    public void testDataQuantizerScaleAndBias() {
        int epsilon = 1;


        TIODataQuantizer quantizer = TIODataQuantizer.TIODataQuantizerWithQuantization(255.0f, 0.0f);
        assertTrue(quantizer.quantize(0) == 0);
        assertTrue(quantizer.quantize(1) == 255);
        assertEquals(quantizer.quantize(0.5f), 127, epsilon);
    }

    @Test
    public void testDataDequantizerStandardZeroToOne() {

        float epsilon = 0.01f;

        TIODataDequantizer dequantizer = TIODataDequantizer.TIODataDequantizerZeroToOne();
        TIODataQuantizer quantizer = TIODataQuantizer.TIODataQuantizerZeroToOne();
        assertTrue(dequantizer.dequantize(0) == 0);
        assertTrue(dequantizer.dequantize(255) == 1);
        assertEquals(dequantizer.dequantize(127), 0.5, epsilon);
    }

    @Test
    public void testDataDequantizerStandardNegativeOneToOne() {
        float epsilon = 0.01f;

        TIODataDequantizer dequantizer = TIODataDequantizer.TIODataDequantizerNegativeOneToOne();

        TIODataQuantizer quantizer = TIODataQuantizer.TIODataQuantizerZeroToOne();
        assertTrue(dequantizer.dequantize(0) == -1);
        assertTrue(dequantizer.dequantize(255) == 1);
        assertEquals(dequantizer.dequantize(127), 0, epsilon);
    }

    @Test
    public void testDataDequantizerScaleAndBias() {
        float epsilon = 0.01f;

        TIODataDequantizer dequantizer = TIODataDequantizer.TIODataDequantizerWithDequantization(1.0f / 255.0f, 0f);


        TIODataQuantizer quantizer = TIODataQuantizer.TIODataQuantizerZeroToOne();
        assertTrue(dequantizer.dequantize(0) == 0);
        assertTrue(dequantizer.dequantize(255) == 1);
        assertEquals(dequantizer.dequantize(127), 0.5, epsilon);
    }

}
