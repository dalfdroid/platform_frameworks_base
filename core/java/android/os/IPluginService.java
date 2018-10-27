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

    /**
     * Returns the contacts interposer of this plugin. May return null if the
     * plugin does not interpose on contacts data.
     *
     * @return The contacts interposer of this plugin.
     */
    public IPluginContactsInterposer getContactsInterposer() throws RemoteException;

    /**
     * Returns the calendar interposer of this plugin. May return null if the
     * plugin does not interpose on calendar data.
     *
     * @return The calendar interposer of this plugin.
     */
    public IPluginCalendarInterposer getCalendarInterposer() throws RemoteException;

    static final String descriptor = "android.os.IPluginService";
    static final int TRANSACTION_getLocationInterposer = (IBinder.FIRST_CALL_TRANSACTION + 0);
    static final int TRANSACTION_getContactsInterposer = (IBinder.FIRST_CALL_TRANSACTION + 1);
    static final int TRANSACTION_getCalendarInterposer = (IBinder.FIRST_CALL_TRANSACTION + 2);
}
