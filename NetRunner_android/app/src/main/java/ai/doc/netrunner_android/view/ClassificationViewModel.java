package ai.doc.netrunner_android.view;

import android.arch.lifecycle.ViewModel;

import ai.doc.netrunner_android.ModelRunner;

public class ClassificationViewModel extends ViewModel {
    private int currentTab = -1;
    private ModelRunner modelRunner;


    public ModelRunner getModelRunner() {
        return modelRunner;
    }

    public void setModelRunner(ModelRunner modelRunner) {
        this.modelRunner = modelRunner;
    }

    public int getCurrentTab() {
        return currentTab;
    }

    public void setCurrentTab(int currentTab) {
        this.currentTab = currentTab;
    }
}
