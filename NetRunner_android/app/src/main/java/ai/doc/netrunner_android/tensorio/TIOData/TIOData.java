package ai.doc.netrunner_android.tensorio.TIOData;

import java.nio.ByteBuffer;

/**
 * A `TIOData` is any data type that knows how to provide bytes to a input tensor and how to
 * read bytes from an output tensor.
 */

public interface TIOData<T> {
    public ByteBuffer getByteBuffer();
    public void putData(T data);
    public T getData();
}
