package ai.doc.netrunner_android;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;

import java.io.IOException;

import ai.doc.netrunner_android.tensorio.TIOModel.TIOModel;
import ai.doc.netrunner_android.tensorio.TIOModel.TIOModelBundle;
import ai.doc.netrunner_android.tensorio.TIOModel.TIOModelBundleException;
import ai.doc.netrunner_android.tensorio.TIOModel.TIOModelBundleManager;

public class TIOTestActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tiotest);

        try {
            TIOModelBundleManager manager = new TIOModelBundleManager(this, "");
            for (String id: manager.getBundleIds()){
                TIOModelBundle bundle = manager.bundleWithId(id);
                Log.i("bundle", bundle.toString());
                TIOModel model = bundle.newModel();
                Log.i("model", model.toString());
                for (String input: bundle.getNamedInputInterfaces().keySet()){
                    Log.i("input", bundle.getNamedInputInterfaces().get(input).getName());
                }
                for (String output: bundle.getNamedOutputInterfaces().keySet()){
                    Log.i("output", bundle.getNamedOutputInterfaces().get(output).getName());
                }
            }

        } catch (IOException e) {
            e.printStackTrace();
        } catch (TIOModelBundleException e) {
            e.printStackTrace();
        }
    }
}
