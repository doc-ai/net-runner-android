package ai.doc.netrunner_android;

public class ModelPrediction {
    private String prediction;
    private String latency;

    public ModelPrediction() {
    }

    public ModelPrediction(String prediction, String latency) {
        this.prediction = prediction;
        this.latency = latency;
    }

    public String getPrediction() {
        return prediction;
    }

    public void setPrediction(String prediction) {
        this.prediction = prediction;
    }

    public String getLatency() {
        return latency;
    }

    public void setLatency(String latency) {
        this.latency = latency;
    }
}
