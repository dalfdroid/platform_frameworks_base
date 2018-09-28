package android.app;

/**
 * This interface is used by the helper bridge library to call back into the
 * proxy manager.
 *
 * @hide
 */
public interface PermissionsPluginProxyCallbacks {

    /**
     * Called by the helper library in LocationCallback.onLocationResult(...),
     * thus providing an opportunity modify the LocationResult before it is
     * given to the app.
     *
     * @param locationResult The LocationResult to modify.
     * @return The LocationResult to return to the application.
     */
    public Object modifyLocationData(Object locationResult);
}
