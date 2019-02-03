package ai.doc.netrunner_android.tensorio.TIOTensorflowLiteModel;

import ai.doc.netrunner_android.tensorio.TIOData.TIOData;
import ai.doc.netrunner_android.tensorio.TIOModel.TIOModel;
import ai.doc.netrunner_android.tensorio.TIOModel.TIOModelBundle;

public class TIOTFLiteModel extends TIOModel {

    public TIOTFLiteModel(TIOModelBundle bundle) {
        super(bundle);
    }

    public TIOTFLiteModel(String path) {
        super(path);
    }

    @Override
    public boolean load() {
        return false;
    }

    @Override
    public void unload() {
    }

    @Override
    public TIOData runOn(TIOData input) {
        return null;
    }
}
