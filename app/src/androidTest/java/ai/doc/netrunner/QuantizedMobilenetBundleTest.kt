package ai.doc.netrunner

import ai.doc.tensorio.TIOLayerInterface.TIOLayerInterface
import ai.doc.tensorio.TIOModel.TIOModelBundle
import ai.doc.tensorio.TIOModel.TIOModelBundleException
import ai.doc.tensorio.TIOModel.TIOModelException
import ai.doc.tensorio.TIOModel.TIOPixelFormat
import ai.doc.tensorio.TIOTFLiteModel.TIOTFLiteModel
import ai.doc.tensorio.TIOUtilities.TIOClassificationHelper

import android.graphics.Bitmap
import android.graphics.BitmapFactory

import androidx.test.platform.app.InstrumentationRegistry

import org.junit.Assert
import org.junit.Test

import java.io.IOException

class QuantizedMobilenetBundleTest {

    private var appContext = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun testModel() {
        try {
            val bundle = TIOModelBundle(appContext, "mobilenet_v1_1.0_224_quant.tfbundle")
            val model = bundle.newModel() as TIOTFLiteModel
            Assert.assertNotNull(model)
            model.load()

            val stream = appContext.assets.open("test-image.jpg")
            val bitmap = BitmapFactory.decodeStream(stream)
            val resizedBitmap = Bitmap.createScaledBitmap(bitmap, 224, 224, true)
            val output = model.runOn(resizedBitmap)

            Assert.assertNotNull(output)
            val classification = output["classification"] as Map<String, Float>?

            Assert.assertNotNull(classification)
            val top5 = TIOClassificationHelper.topN(classification, 1)
            val top = top5[0]
            val label = top.key
            Assert.assertEquals("rocking chair", label)
        } catch (e: TIOModelBundleException) {
            e.printStackTrace()
        } catch (e: TIOModelException) {
            e.printStackTrace()
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    @Test
    fun testModelBundle() {
        try {
            // TODO: Move all these to tensor/io JSON parsing test
            val bundle = TIOModelBundle(appContext, "mobilenet_v1_1.0_224_quant.tfbundle")

            // Basic Properties
            Assert.assertEquals(bundle.name, "MobileNet V1 1.0 224 Quantized")
            Assert.assertEquals(bundle.details, "MobileNet V1 with a width multiplier of 1.0 and an input resolution of 224x224. Quantized.\n\nMobileNets are based on a streamlined architecture that have depth-wise separable convolutions to build light weight deep neural networks. Trained on ImageNet with categories such as trees, animals, food, vehicles, person etc. MobileNets: Efficient Convolutional Neural Networks for Mobile Vision Applications.")
            Assert.assertEquals(bundle.identifier, "mobilenet-v1-100-224-quantized")
            Assert.assertEquals(bundle.version, "1")
            Assert.assertEquals(bundle.author, "Andrew G. Howard, Menglong Zhu, Bo Chen, Dmitry Kalenichenko, Weijun Wang, Tobias Weyand, Marco Andreetto, Hartwig Adam")
            Assert.assertEquals(bundle.license, "Apache License. Version 2.0 http://www.apache.org/licenses/LICENSE-2.0")

            val options = bundle.options
            Assert.assertEquals(options.devicePosition, "0")

            // Inputs
            Assert.assertEquals(bundle.io.inputs.size().toLong(), 1)
            Assert.assertTrue(bundle.io.inputs.keys().contains("image"))

            val input = bundle.io.inputs[0]
            Assert.assertEquals(input.name, "image")
            Assert.assertSame(input.mode, TIOLayerInterface.Mode.Input)

            input.doCase({ _ ->
                Assert.fail()
            },{ pixelLayer ->
                Assert.assertTrue(pixelLayer.isQuantized)
                Assert.assertSame(pixelLayer.pixelFormat, TIOPixelFormat.RGB)
                Assert.assertEquals(pixelLayer.shape.channels.toLong(), 3)
                Assert.assertEquals(pixelLayer.shape.height.toLong(), 224)
                Assert.assertEquals(pixelLayer.shape.width.toLong(), 224)
                Assert.assertNull(pixelLayer.normalizer)
                Assert.assertNull(pixelLayer.denormalizer)
            })

            // Outputs
            Assert.assertEquals(bundle.io.outputs.size().toLong(), 1)
            Assert.assertEquals(bundle.io.outputs.size().toLong(), 1)
            Assert.assertTrue(bundle.io.outputs.keys().contains("classification"))

            val output = bundle.io.outputs[0]
            Assert.assertEquals(output.name, "classification")
            Assert.assertSame(output.mode, TIOLayerInterface.Mode.Output)

            input.doCase({ vectorLayer ->
                Assert.assertTrue(vectorLayer.isQuantized)
                Assert.assertEquals(vectorLayer.length.toLong(), 1001)
                Assert.assertTrue(vectorLayer.isLabeled)
                Assert.assertEquals(vectorLayer.labels.size.toLong(), 1001)
                Assert.assertEquals(vectorLayer.labels[0], "background")
                Assert.assertEquals(vectorLayer.labels[vectorLayer.labels.size - 1], "toilet tissue")
                Assert.assertNull(vectorLayer.quantizer)
                Assert.assertNotNull(vectorLayer.dequantizer)
            }, { _ ->
                Assert.fail()
            })

        } catch (e: TIOModelBundleException) {
            e.printStackTrace()
        }
    }
}