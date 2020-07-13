package ai.doc.netrunner

import ai.doc.tensorio.TIOModel.TIOModelBundle
import ai.doc.tensorio.TIOModel.TIOModelBundleException
import ai.doc.tensorio.TIOModel.TIOModelException
import ai.doc.tensorio.TIOTFLiteModel.TIOTFLiteModel
import ai.doc.tensorio.TIOUtilities.TIOClassificationHelper

import android.graphics.Bitmap
import android.graphics.BitmapFactory

import androidx.test.platform.app.InstrumentationRegistry

import org.junit.Assert
import org.junit.Test

import java.io.IOException

class QuantizedMobileNetTests {

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

}