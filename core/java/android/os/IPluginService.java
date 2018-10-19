package android.os;

/**
 * The interface to a plugin service.
 *
 * {@hide}
 */
public interface IPluginService extends IInterface
{
    /**
     * Returns the location interposer of this plugin. May return null if the
     * plugin does not interpose on location data.
     *
     * @return The location interposer of this plugin.
     */
    public IPluginLocationInterposer getLocationInterposer() throws RemoteException;

    static final String descriptor = "android.os.IPluginService";
    static final int TRANSACTION_getLocationInterposer = (IBinder.FIRST_CALL_TRANSACTION + 0);
}
