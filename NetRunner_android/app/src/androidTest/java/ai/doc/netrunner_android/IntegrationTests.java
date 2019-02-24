package ai.doc.netrunner_android;

import android.content.Context;
import android.support.test.InstrumentationRegistry;

import org.junit.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import ai.doc.netrunner_android.tensorio.TIOModel.TIOModelBundle;
import ai.doc.netrunner_android.tensorio.TIOModel.TIOModelBundleException;
import ai.doc.netrunner_android.tensorio.TIOModel.TIOModelException;
import ai.doc.netrunner_android.tensorio.TIOTensorflowLiteModel.TIOTFLiteModel;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
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
            float[] result = (float[]) output;

            assertEquals(1, result.length);
            assertEquals(25f, result[0], epsilon);

            // Run the model on a dictionary
            Map<String, float[]> input_dict = new HashMap<>();
            input_dict.put("input_x", new float[]{2});
            output = model.runOn(input_dict);
            assertTrue(output instanceof Map);
            Map<String, float[]> result_dict = (Map<String, float[]>) output;

            assertTrue(result_dict.containsKey("output_z"));
            assertEquals(1, result_dict.size());
            assertEquals(1, result_dict.get("output_z").length);
            assertEquals(25f, result_dict.get("output_z")[0], epsilon);

            // try running on input of of the wrong length, should throw IllegalArgumentException
            try {
                input = new float[]{1, 2, 3, 4, 5};
                model.runOn(input);
                fail();
            } catch (IllegalArgumentException e) {

            }


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
            float[] result = (float[]) output;

            assertEquals(4, result.length);
            assertTrue(Arrays.equals(expected, result));

            // Run the model on a dictionary
            Map<String, float[]> input_dict = new HashMap<>();
            input_dict.put("input_x", input);
            output = model.runOn(input_dict);
            assertTrue(output instanceof Map);
            Map<String, float[]> result_dict = (Map<String, float[]>) output;

            assertTrue(result_dict.containsKey("output_z"));
            assertEquals(1, result_dict.size());
            assertEquals(4, result_dict.get("output_z").length);
            assertTrue(Arrays.equals(expected, result_dict.get("output_z")));

            // try running on input of of the wrong length, should throw IllegalArgumentException
            try {
                input = new float[]{1, 2, 3, 4, 5};
                model.runOn(input);
                fail();
            } catch (IllegalArgumentException e) {

            }

            // try running on input of of the wrong length, should throw IllegalArgumentException
            try {
                input = new float[]{1};
                model.runOn(input);
                fail();
            } catch (IllegalArgumentException e) {
            }


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
            Map<String, float[]> result = (Map<String, float[]>) output;

            assertEquals(2, result.size());
            assertTrue(result.containsKey("output_s"));
            assertTrue(result.containsKey("output_z"));

            assertEquals(1, result.get("output_s").length);
            assertEquals(1, result.get("output_z").length);

            assertEquals(64, result.get("output_s")[0], epsilon);
            assertEquals(240, result.get("output_z")[0], epsilon);

            // try running on input of of the wrong length, should throw IllegalArgumentException
            try {
                inputs = new HashMap<>();
                inputs.put("input_x", new float[]{1, 2, 3, 5, 6});
                inputs.put("input_y", new float[]{10, 20, 30});
                model.runOn(inputs);
                fail();
            } catch (IllegalArgumentException e) {

            }

            // try running on input of of the wrong length, should throw IllegalArgumentException
            try {
                inputs = new HashMap<>();
                inputs.put("input_x", new float[]{1});
                inputs.put("input_y", new float[]{10, 20, 30});
                model.runOn(inputs);
                fail();
            } catch (IllegalArgumentException e) {
            }


        } catch (TIOModelBundleException | TIOModelException e) {
            e.printStackTrace();
            fail();
        }
    }

    @Test
    public void test2x2MatricesModel() {
        Context appContext = InstrumentationRegistry.getTargetContext();
        try {
            TIOModelBundle bundle = new TIOModelBundle(appContext, "2_in_2_out_matrices_test.tfbundle");
            assertNotNull(bundle);

            TIOTFLiteModel model = (TIOTFLiteModel) bundle.newModel();
            assertNotNull(model);
            model.load();

            // Ensure inputs and outputs return correct count
            assertEquals(2, model.getInputs().size());
            assertEquals(2, model.getOutputs().size());

            float[] expectedS = new float[]{
                    18f, 18f, 18f, 18f,
                    180f, 180f, 180f, 180f,
                    1800f, 1800f, 1800f, 1800f,
                    18000f, 18000f, 18000f, 18000f
            };

            float[] expectedZ = new float[]{
                    56f, 72f, 56f, 72f,
                    5600f, 7200f, 5600f, 7200f,
                    560000f, 720000f, 560000f, 720000f,
                    56000000f, 72000000f, 56000000f, 72000000f
            };

            float[] inputX = new float[]{
                    1f, 2f, 3f, 4f,
                    10f, 20f, 30f, 40f,
                    100f, 200f, 300f, 400f,
                    1000f, 2000f, 3000f, 4000f
            };

            float[] inputY = new float[]{
                    5, 6, 7, 8,
                    50, 60, 70, 80,
                    500, 600, 700, 800,
                    5000, 6000, 7000, 8000
            };

            Map<String, float[]> inputs = new HashMap<>();
            inputs.put("input_x", inputX);
            inputs.put("input_y", inputY);

            Object output = model.runOn(inputs);
            assertTrue(output instanceof Map);
            Map<String, float[]> result = (Map<String, float[]>) output;

            assertEquals(2, result.size());
            assertTrue(result.containsKey("output_s"));
            assertTrue(result.containsKey("output_z"));

            assertEquals(16, result.get("output_s").length);
            assertEquals(16, result.get("output_z").length);

            assertArrayEquals(expectedS, result.get("output_s"), epsilon);
            assertArrayEquals(expectedZ, result.get("output_z"), epsilon);

            // try running on input of of the wrong length, should throw IllegalArgumentException
            try {
                inputs = new HashMap<>();
                inputs.put("input_x", new float[]{5, 6, 7, 8});
                inputs.put("input_y", new float[]{5, 6, 7, 8});
                model.runOn(inputs);
                fail();
            } catch (IllegalArgumentException e) {
            }


        } catch (TIOModelBundleException | TIOModelException e) {
            e.printStackTrace();
            fail();
        }
    }

    @Test
    public void test3x3MatricesModel() {
        Context appContext = InstrumentationRegistry.getTargetContext();
        try {
            TIOModelBundle bundle = new TIOModelBundle(appContext, "1_in_1_out_tensors_test.tfbundle");
            assertNotNull(bundle);

            TIOTFLiteModel model = (TIOTFLiteModel) bundle.newModel();
            assertNotNull(model);
            model.load();

            // Ensure inputs and outputs return correct count
            assertEquals(1, model.getInputs().size());
            assertEquals(1, model.getOutputs().size());

            float[] expectedZ = new float[]{
                    2, 3, 4, 5, 6, 7, 8, 9, 10,
                    12, 22, 32, 42, 52, 62, 72, 82, 92,
                    103, 203, 303, 403, 503, 603, 703, 803, 903
            };

            float[] inputX = new float[]{
                    1, 2, 3, 4, 5, 6, 7, 8, 9,
                    10, 20, 30, 40, 50, 60, 70, 80, 90,
                    100, 200, 300, 400, 500, 600, 700, 800, 900
            };

            Object output = model.runOn(inputX);

            assertTrue(output instanceof float[]);
            float[] result = (float[]) output;

            assertEquals(27, result.length);
            assertTrue(Arrays.equals(expectedZ, result));

            // try running on input of of the wrong length, should throw IllegalArgumentException
            try {
                model.runOn(new float[]{5, 6, 7, 8});
                fail();
            } catch (IllegalArgumentException e) {
            }


        } catch (TIOModelBundleException | TIOModelException e) {
            e.printStackTrace();
            fail();
        }

    }

}