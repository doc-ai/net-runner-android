package ai.doc.netrunner;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import ai.doc.tensorio.TIOModel.TIOModelException;
import ai.doc.tensorio.TIOTFLiteModel.TIOTFLiteModel;
import ai.doc.tensorio.TIOUtilities.TIOClassificationHelper;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

import ai.doc.tensorio.TIOLayerInterface.TIOLayerInterface;
import ai.doc.tensorio.TIOLayerInterface.TIOPixelBufferLayerDescription;
import ai.doc.tensorio.TIOLayerInterface.TIOVectorLayerDescription;
import ai.doc.tensorio.TIOModel.TIOModelBundle;
import ai.doc.tensorio.TIOModel.TIOModelBundleException;
import ai.doc.tensorio.TIOModel.TIOModelOptions;
import ai.doc.tensorio.TIOModel.TIOVisionModel.TIOPixelFormat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class QuantizedMobilenetBundleTest {

    Context appContext = InstrumentationRegistry.getInstrumentation().getTargetContext();

    @Test
    public void testModel() {

        try {
            TIOModelBundle bundle = new TIOModelBundle(appContext, "mobilenet_v1_1.0_224_quant.tfbundle");
            TIOTFLiteModel model = (TIOTFLiteModel) bundle.newModel();
            assertNotNull(model);
            model.load();

            InputStream stream = appContext.getAssets().open("test-image.jpg");
            Bitmap bitmap = BitmapFactory.decodeStream(stream);
            Bitmap resizedBitmap = Bitmap.createScaledBitmap(bitmap, 224, 224, true);

            Map<String, Object> output = model.runOn(resizedBitmap);
            assertNotNull(output);

            Map<String, Float> classification = (Map<String, Float>) output.get("classification");
            assertNotNull(classification);

            List<Map.Entry<String, Float>> top5 = TIOClassificationHelper.topN(classification, 1);
            Map.Entry<String, Float> top = top5.get(0);
            String label = top.getKey();

            assertEquals("rocking chair", label);
        } catch (TIOModelBundleException | TIOModelException | IOException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void testModelBundle() {

        try {
            // TODO: Move all these to tensor/io JSON parsing test

            TIOModelBundle bundle = new TIOModelBundle(appContext, "mobilenet_v1_1.0_224_quant.tfbundle");

            // Basic Properties

            assertEquals(bundle.getName(), "MobileNet V1 1.0 224 Quantized");
            assertEquals(bundle.getDetails(), "MobileNet V1 with a width multiplier of 1.0 and an input resolution of 224x224. Quantized.\n\nMobileNets are based on a streamlined architecture that have depth-wise separable convolutions to build light weight deep neural networks. Trained on ImageNet with categories such as trees, animals, food, vehicles, person etc. MobileNets: Efficient Convolutional Neural Networks for Mobile Vision Applications." );
            assertEquals(bundle.getIdentifier(), "mobilenet-v1-100-224-quantized");
            assertEquals(bundle.getVersion(), "1");
            assertEquals(bundle.getAuthor(), "Andrew G. Howard, Menglong Zhu, Bo Chen, Dmitry Kalenichenko, Weijun Wang, Tobias Weyand, Marco Andreetto, Hartwig Adam");
            assertEquals(bundle.getLicense(), "Apache License. Version 2.0 http://www.apache.org/licenses/LICENSE-2.0");

            TIOModelOptions options = bundle.getOptions();
            assertEquals(options.getDevicePosition(), "0");

            // Inputs

            assertEquals(bundle.getIO().getInputs().size(), 1);
            assertTrue(bundle.getIO().getInputs().keys().contains("image"));

            TIOLayerInterface input = bundle.getIO().getInputs().get(0);

            assertEquals(input.getName(), "image");
            assertSame(input.getMode(), TIOLayerInterface.Mode.Input);
            assertTrue(input.getLayerDescription() instanceof TIOPixelBufferLayerDescription);

            TIOPixelBufferLayerDescription layerDescription = (TIOPixelBufferLayerDescription)input.getLayerDescription();

            assertTrue(layerDescription.isQuantized());
            assertSame(layerDescription.getPixelFormat(), TIOPixelFormat.RGB);
            assertEquals(layerDescription.getShape().channels, 3);
            assertEquals(layerDescription.getShape().height, 224);
            assertEquals(layerDescription.getShape().width, 224);
            assertNull(layerDescription.getNormalizer());
            assertNull(layerDescription.getDenormalizer());

            // Outputs

            assertEquals(bundle.getIO().getOutputs().size(), 1);
            assertEquals(bundle.getIO().getOutputs().size(), 1);
            assertTrue(bundle.getIO().getOutputs().keys().contains("classification"));

            TIOLayerInterface output = bundle.getIO().getOutputs().get(0);

            assertEquals(output.getName(), "classification");
            assertSame(output.getMode(), TIOLayerInterface.Mode.Output);
            assertTrue(output.getLayerDescription() instanceof TIOVectorLayerDescription);

            TIOVectorLayerDescription outputLayerDescription = (TIOVectorLayerDescription)output.getLayerDescription();

            assertTrue(outputLayerDescription.isQuantized());
            assertEquals(outputLayerDescription.getLength(), 1001);
            assertTrue(outputLayerDescription.isLabeled());
            assertEquals(outputLayerDescription.getLabels().length, 1001);
            assertEquals(outputLayerDescription.getLabels()[0], "background");
            assertEquals(outputLayerDescription.getLabels()[outputLayerDescription.getLabels().length-1], "toilet tissue");
            assertNull(outputLayerDescription.getQuantizer());
            assertNotNull(outputLayerDescription.getDequantizer());

            // Run Model

        } catch (TIOModelBundleException e) {
            e.printStackTrace();
        }
    }

}