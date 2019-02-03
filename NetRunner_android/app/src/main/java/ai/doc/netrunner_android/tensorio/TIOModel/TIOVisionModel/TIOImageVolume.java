package ai.doc.netrunner_android.tensorio.TIOModel.TIOVisionModel;

public class TIOImageVolume {
    public int height;
    public int width;
    public int channels;

    public TIOImageVolume(int height, int width, int channels) {
        this.height = height;
        this.width = width;
        this.channels = channels;
    }
}

