package ai.doc.netrunner;

import android.content.Context;
import android.support.test.InstrumentationRegistry;

import org.junit.Test;

import ai.doc.tensorio.TIOLayerInterface.TIOLayerInterface;
import ai.doc.tensorio.TIOLayerInterface.TIOPixelBufferLayerDescription;
import ai.doc.tensorio.TIOLayerInterface.TIOVectorLayerDescription;
import ai.doc.tensorio.TIOModel.TIOModelBundle;
import ai.doc.tensorio.TIOModel.TIOModelBundleException;
import ai.doc.tensorio.TIOModel.TIOModelOptions;
import ai.doc.tensorio.TIOModel.TIOVisionModel.TIOPixelFormat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class QuantizedMobilenetBundleTest {
    @Test
    public void test() {
        Context appContext = InstrumentationRegistry.getTargetContext();

        try {
            TIOModelBundle bundle = new TIOModelBundle(appContext, "mobilenet_v1_1.0_224_quant.tfbundle");
            assertEquals(bundle.getName(), "MobileNet V1 1.0 224 Quantized");
            System.out.println(bundle.getDetails());
            assertEquals(bundle.getDetails(), "MobileNet V1 with a width multiplier of 1.0 and an input resolution of 224x224. Quantized.\n\nMobileNets are based on a streamlined architecture that have depth-wise separable convolutions to build light weight deep neural networks. Trained on ImageNet with categories such as trees, animals, food, vehicles, person etc. MobileNets: Efficient Convolutional Neural Networks for Mobile Vision Applications." );
            assertEquals(bundle.getIdentifier(), "mobilenet-v1-100-224-quantized");
            assertEquals(bundle.getVersion(), "1");
            assertEquals(bundle.getAuthor(), "Andrew G. Howard, Menglong Zhu, Bo Chen, Dmitry Kalenichenko, Weijun Wang, Tobias Weyand, Marco Andreetto, Hartwig Adam");
            assertEquals(bundle.getLicense(), "Apache License. Version 2.0 http://www.apache.org/licenses/LICENSE-2.0");

            assertEquals(bundle.getIndexedInputInterfaces().size(), 1);
            assertEquals(bundle.getNamedInputInterfaces().size(), 1);
            assertTrue(bundle.getNamedInputInterfaces().containsKey("image"));

            TIOLayerInterface input = bundle.getIndexedInputInterfaces().get(0);
            assertEquals(input.getName(), "image");
            assertTrue(input.isInput());
            assertTrue(input.getDataDescription() instanceof TIOPixelBufferLayerDescription);

            TIOPixelBufferLayerDescription layerDescription = (TIOPixelBufferLayerDescription)input.getDataDescription();
            assertTrue(layerDescription.isQuantized());
            assertSame(layerDescription.getPixelFormat(), TIOPixelFormat.RGB);
            assertEquals(layerDescription.getShape().channels, 3);
            assertEquals(layerDescription.getShape().height, 224);
            assertEquals(layerDescription.getShape().width, 224);

            assertNull(layerDescription.getNormalizer());
            assertNull(layerDescription.getDenormalizer());

            assertEquals(bundle.getIndexedOutputInterfaces().size(), 1);
            assertEquals(bundle.getNamedOutputInterfaces().size(), 1);
            assertTrue(bundle.getNamedOutputInterfaces().containsKey("classification"));

            TIOLayerInterface output = bundle.getIndexedOutputInterfaces().get(0);
            assertEquals(output.getName(), "classification");
            assertFalse(output.isInput());
            assertTrue(output.getDataDescription() instanceof TIOVectorLayerDescription);

            TIOVectorLayerDescription outputLayerDescription = (TIOVectorLayerDescription)output.getDataDescription();
            assertTrue(outputLayerDescription.isQuantized());
            assertEquals(outputLayerDescription.getLength(), 1001);
            assertTrue(outputLayerDescription.isLabeled());
            assertEquals(outputLayerDescription.getLabels().length, 1001);
            assertEquals(outputLayerDescription.getLabels()[0], "background");
            assertEquals(outputLayerDescription.getLabels()[outputLayerDescription.getLabels().length-1], "toilet tissue");
            assertNull(outputLayerDescription.getQuantizer());
            assertNotNull(outputLayerDescription.getDequantizer());

            TIOModelOptions options = bundle.getOptions();
            assertEquals(options.getDevicePosition(), "0");

        } catch (TIOModelBundleException e) {
            e.printStackTrace();
        }
    }

}