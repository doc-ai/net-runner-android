package ai.doc.netrunner_android.view;

import android.arch.lifecycle.ViewModel;

import ai.doc.netrunner_android.ModelRunner;
import ai.doc.netrunner_android.tensorio.TIOModel.TIOModelBundleManager;

public class ClassificationViewModel extends ViewModel {
    private int currentTab = -1;
    private ModelRunner modelRunner;
    private TIOModelBundleManager manager;


    public ModelRunner getModelRunner() {
        return modelRunner;
    }

    public void setModelRunner(ModelRunner modelRunner) {
        this.modelRunner = modelRunner;
    }

    public TIOModelBundleManager getManager() {
        return manager;
    }

    public void setManager(TIOModelBundleManager manager) {
        this.manager = manager;
    }

    public int getCurrentTab() {
        return currentTab;
    }

    public void setCurrentTab(int currentTab) {
        this.currentTab = currentTab;
    }
}
