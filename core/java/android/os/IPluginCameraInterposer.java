package android.os;

/**
 * The interface of a camera interposer in a plugin.
 *
 * {@hide}
 */
public interface IPluginCameraInterposer extends IInterface
{
    /**
     * This is called when the framework discovers that a new camera stream is
     * created. It allows the plugin to confirm whether it wants to interpose on
     * this particular stream. Note that this is called on a plugin only when a
     * user chooses to use that plugin to interpose an app's access to the
     * camera.
     *
     * @param packageName The package for which the camera stream is being
     * created.
     * @param streamId The id of the disconnecting camera stream.
     * @param width The width of the frames in the camera stream.
     * @param height The height of the frames in the camera stream.
     * @param format The format of the camera stream.
     *
     * @return true if the plugin should interpose on the camera stream, and
     * false otherwise.
     */
    public boolean shouldInterpose(String packageName, int streamId, int width, int height, int format);

    /**
     * This is called when a camera stream is disconnecting. The plugin should
     * perform all cleanup of resources associated with the given stream id.
     *
     * @param packageName The package that owns the camera stream.
     * @param streamId The id of the disconnecting camera stream.
     */
    public void streamDisconnecting(String packageName, int streamId);

    static final String descriptor = "android.os.IPluginCameraInterposer";

    /**
     * The starting ID of all internal transactions; internal transactions are
     * not exposed to nor implemented by the plugins.
     */
    static final int TRANSACTION_FIRST_HIDDEN = (IBinder.FIRST_CALL_TRANSACTION + 100);
}
