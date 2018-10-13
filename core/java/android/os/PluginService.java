package android.os;

import android.app.ActivityThread;
import android.content.Context;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.util.Log;

import android.os.IPluginService;
import android.os.IPluginLocationInterposer;

import java.util.List;

/**
 * This class represents a distinct plugin service.
 *
 * {@hide}
 */
public class PluginService {
    private static final String PLUGIN_MAIN = ".PluginMain";
    private static final String TAG = "heimdall";

    private static final String INTERPOSER_LOCATION = "location";

    private final String mPackage;
    private final List<String> mInterposers;

    private ComponentName mComponent = null;
    private ServiceConnection mConnection = null;
    private IPluginService mService = null;
    private IPluginLocationInterposer mLocationInterposer = null;

    private boolean mConnecting = false;
    private boolean mConnected = false;

    /**
     * Creates a new instance of the plugin service. Use the {@link #connect}
     * method to connect to the service.
     *
     * @param pluginPackage The package name of the plugin.
     * @param interposers A list of data interposers to retrieve from the plugin.
     * {@hide}
     */
    public PluginService(String pluginPackage, List<String> interposers) {
        mPackage = pluginPackage;
        mInterposers = interposers;

        mComponent = new ComponentName(mPackage, mPackage + PLUGIN_MAIN);
        mConnection = new ServiceConnection() {
                @Override
                public void onServiceConnected(ComponentName name, IBinder service) {
                    synchronized(PluginService.this) {
                        mService = IPluginService.Stub.asInterface(service);
                        retrieveInterposers();
                        mConnecting = false;
                        mConnected = true;
                        PluginService.this.notifyAll();
                    }
                }

                @Override
                public void onServiceDisconnected(ComponentName name) {
                    synchronized(PluginService.this) {
                        reset();
                        PluginService.this.notifyAll();
                    }
                }

                @Override
                public void onBindingDied(ComponentName name) {
                    synchronized(PluginService.this) {
                        Context c = ActivityThread.currentApplication();
                        if (c != null) {
                            c.unbindService(this);
                        }
                        reset();
                        PluginService.this.notifyAll();
                    }
                }

                private void reset() {
                    mService = null;
                    mLocationInterposer = null;
                    mConnecting = false;
                    mConnected = false;
                }
            };
    }

    private void retrieveInterposers() {
        for (String interposer : mInterposers) {
            if (interposer.equals(INTERPOSER_LOCATION)) {
                try {
                    mLocationInterposer = mService.getLocationInterposer();
                } catch (RemoteException ex) {
                    Log.d(TAG, "Could not get location interposer from plugin service " + mPackage);
                }
            }
        }
    }

    /**
     * Connect to the plugin service.
     *
     * {@hide}
     */
    public boolean connect() {
        Context c = ActivityThread.currentApplication();
        if (c == null) {
            Log.d(TAG, "Context is null. Can't connect to: " + mPackage);
            return false;
        }

        Intent intent = new Intent();
        intent.setComponent(mComponent);

        boolean ret = c.bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
        if (ret) {
            mConnecting = true;
        } else {
            Log.d(TAG, "Unable to bind to plugin service: " + mPackage);
            return false;
        }

        return ret;
    }

    /**
     * @return true if a connection has been made to the plugin service.
     * {@hide}
     */
    public boolean isConnected() {
        return mConnected;
    }

    /**
     * @return true if a connection attempt to the plugin service is ongoing.
     * {@hide}
     */
    public boolean isTryingToConnect() {
        return mConnecting;
    }

    /**
     * {@hide}
     */
    public Object getLocationInterposer() {
        // FIXME: It would be nice to return IPluginLocationInterposer instead
        // of Object in this method. However, doing so results in a weird
        // compile failure. Investigate and fix.
        return mLocationInterposer;
    }
}
