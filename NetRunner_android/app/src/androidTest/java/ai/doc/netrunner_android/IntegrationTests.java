package ai.doc.netrunner_android;

import android.content.Context;
import android.support.test.InstrumentationRegistry;

import org.junit.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import ai.doc.netrunner_android.tensorio.TIOData.TIOPixelNormalizer;
import ai.doc.netrunner_android.tensorio.TIOLayerInterface.TIOLayerInterface;
import ai.doc.netrunner_android.tensorio.TIOLayerInterface.TIOPixelBufferLayerDescription;
import ai.doc.netrunner_android.tensorio.TIOLayerInterface.TIOVectorLayerDescription;
import ai.doc.netrunner_android.tensorio.TIOModel.TIOModel;
import ai.doc.netrunner_android.tensorio.TIOModel.TIOModelBundle;
import ai.doc.netrunner_android.tensorio.TIOModel.TIOModelBundleException;
import ai.doc.netrunner_android.tensorio.TIOModel.TIOModelException;
import ai.doc.netrunner_android.tensorio.TIOModel.TIOModelOptions;
import ai.doc.netrunner_android.tensorio.TIOModel.TIOVisionModel.TIOPixelFormat;
import ai.doc.netrunner_android.tensorio.TIOTensorflowLiteModel.TIOTFLiteModel;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class IntegrationTests {
    private float epsilon = 0.01f;

    @Test
    public void test1In1OutNumberModel() {
        Context appContext = InstrumentationRegistry.getTargetContext();

        try {
            TIOModelBundle bundle = new TIOModelBundle(appContext, "1_in_1_out_number_test.tfbundle");
            assertNotNull(bundle);

            TIOTFLiteModel model = (TIOTFLiteModel) bundle.newModel();
            assertNotNull(model);
            model.load();

            // Ensure inputs and outputs return correct count
            assertEquals(1, model.getInputs().size());
            assertEquals(1, model.getOutputs().size());

            // Run the model on a number
            float[] input = new float[]{2};
            Object output = model.runOn(input);
            assertTrue(output instanceof float[]);
            float[] result = (float[])output;

            assertEquals(1, result.length);
            assertEquals(25f, result[0], epsilon);

            // Run the model on a dictionary
            Map<String, float[]> input_dict = new HashMap<>();
            input_dict.put("input_x", new float[]{2});
            output = model.runOn(input_dict);
            assertTrue(output instanceof Map);
            Map<String, float[]> result_dict = (Map<String, float[]>)output;

            assertTrue(result_dict.containsKey("output_z"));
            assertEquals(1, result_dict.size());
            assertEquals(1, result_dict.get("output_z").length);
            assertEquals(25f, result_dict.get("output_z")[0], epsilon);

        } catch (TIOModelBundleException | TIOModelException e) {
            e.printStackTrace();
            fail();
        }
    }

    @Test
    public void test1x1VectorsModel() {
        Context appContext = InstrumentationRegistry.getTargetContext();
        try {
            TIOModelBundle bundle = new TIOModelBundle(appContext, "1_in_1_out_vectors_test.tfbundle");
            assertNotNull(bundle);

            TIOTFLiteModel model = (TIOTFLiteModel) bundle.newModel();
            assertNotNull(model);
            model.load();

            // Ensure inputs and outputs return correct count
            assertEquals(1, model.getInputs().size());
            assertEquals(1, model.getOutputs().size());

            float[] expected = new float[]{2, 2, 4, 4};
            float[] input = new float[]{1, 2, 3, 4};

            // Run the model on a vector
            Object output = model.runOn(input);
            assertTrue(output instanceof float[]);
            float[] result = (float[])output;

            assertEquals(4, result.length);
            assertTrue(Arrays.equals(expected, result));

            // Run the model on a dictionary
            Map<String, float[]> input_dict = new HashMap<>();
            input_dict.put("input_x", input);
            output = model.runOn(input_dict);
            assertTrue(output instanceof Map);
            Map<String, float[]> result_dict = (Map<String, float[]>)output;

            assertTrue(result_dict.containsKey("output_z"));
            assertEquals(1, result_dict.size());
            assertEquals(4, result_dict.get("output_z").length);
            assertTrue(Arrays.equals(expected, result_dict.get("output_z")));

        } catch (TIOModelBundleException | TIOModelException e) {
            e.printStackTrace();
            fail();
        }
    }

    @Test
    public void test2x2VectorsModel() {
        Context appContext = InstrumentationRegistry.getTargetContext();
        try {
            TIOModelBundle bundle = new TIOModelBundle(appContext, "2_in_2_out_vectors_test.tfbundle");
            assertNotNull(bundle);

            TIOTFLiteModel model = (TIOTFLiteModel) bundle.newModel();
            assertNotNull(model);
            model.load();

            // Ensure inputs and outputs return correct count
            assertEquals(2, model.getInputs().size());
            assertEquals(2, model.getOutputs().size());

            Map<String, float[]> inputs = new HashMap<>();
            inputs.put("input_x", new float[]{1, 2, 3, 4});
            inputs.put("input_y", new float[]{10, 20, 30, 40});

            Object output = model.runOn(inputs);
            assertTrue(output instanceof Map);
            Map<String, float[]> result = (Map<String, float[]>)output;

            assertEquals(2, result.size());
            assertTrue(result.containsKey("output_s"));
            assertTrue(result.containsKey("output_z"));

            assertEquals(1, result.get("output_s").length);
            assertEquals(1, result.get("output_z").length);

            assertEquals(64, result.get("output_s")[0], epsilon);
            assertEquals(240, result.get("output_z")[0],epsilon);


        } catch (TIOModelBundleException | TIOModelException e) {
            e.printStackTrace();
            fail();
        }
    }

}