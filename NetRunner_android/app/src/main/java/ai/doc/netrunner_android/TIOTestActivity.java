package ai.doc.netrunner_android;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.widget.ImageView;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.AbstractMap;
import java.util.Arrays;
import java.util.Map;
import java.util.PriorityQueue;

import ai.doc.netrunner_android.tensorio.TIOLayerInterface.TIOVectorLayerDescription;
import ai.doc.netrunner_android.tensorio.TIOModel.TIOModel;
import ai.doc.netrunner_android.tensorio.TIOModel.TIOModelBundle;
import ai.doc.netrunner_android.tensorio.TIOModel.TIOModelBundleException;
import ai.doc.netrunner_android.tensorio.TIOModel.TIOModelBundleManager;
import ai.doc.netrunner_android.tensorio.TIOModel.TIOModelException;

public class TIOTestActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tiotest);

        try {
            TIOModelBundleManager manager = new TIOModelBundleManager(getApplicationContext(), "");
            TIOModelBundle bundle = manager.bundleWithId("segmentation");
            TIOModel model = bundle.newModel();
            model.load();

            InputStream bitmap=getAssets().open("picture2.jpg");
            Bitmap bMap= BitmapFactory.decodeStream(bitmap);
            bMap = Bitmap.createScaledBitmap(bMap, 300, 300, false);
            byte[] result = (byte[])model.runOn(bMap);
            Bitmap bmp = Bitmap.createBitmap(300, 300, Bitmap.Config.ALPHA_8);
            bmp.copyPixelsFromBuffer(ByteBuffer.wrap(result));

            ImageView iv = (ImageView)findViewById(R.id.imageView);
            iv.setImageBitmap(bmp);

            /*

            float[] result = (float[])model.runOn(bMap);

            Log.i("result", Arrays.toString(result));

            PriorityQueue<Map.Entry<Integer, Float>> pq = new PriorityQueue<>(10, (o1, o2) -> (o2.getValue()).compareTo(o1.getValue()));
            for (int i = 0; i < 1001; i++){
                pq.add(new AbstractMap.SimpleEntry<>(i, result[i]));
            }

            String[] labels = ((TIOVectorLayerDescription)model.descriptionOfOutputAtIndex(0)).getLabels();
            for (int i = 0; i< 10; i++){
                Map.Entry<Integer, Float> e = pq.poll();
                Log.i(labels[e.getKey()], ""+e.getValue());
            }
            */
        } catch (IOException e) {
            e.printStackTrace();
        } catch (TIOModelBundleException e) {
            e.printStackTrace();
        } catch (TIOModelException e) {
            e.printStackTrace();
        }
    }
}
