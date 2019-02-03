package ai.doc.netrunner_android;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;

import ai.doc.netrunner_android.tensorio.TIOModel.TIOModel;
import ai.doc.netrunner_android.tensorio.TIOModel.TIOModelBundle;
import ai.doc.netrunner_android.tensorio.TIOModel.TIOModelBundleException;

public class TIOTestActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tiotest);

        try {
            TIOModelBundle bundle = new TIOModelBundle(this, "mobilenet_v2_1.4_224.tfbundle");
            Log.i("bundle", bundle.toString());
            TIOModel model = bundle.newModel();
            Log.i("model", model.toString());
            for (String input: bundle.getNamedInputInterfaces().keySet()){
                Log.i("input", bundle.getNamedInputInterfaces().get(input).getName());
            }
            for (String output: bundle.getNamedOutputInterfaces().keySet()){
                Log.i("output", bundle.getNamedOutputInterfaces().get(output).getName());
            }

        } catch (TIOModelBundleException e) {
            e.printStackTrace();
        }
    }
}
