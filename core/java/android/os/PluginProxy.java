package android.os;

import android.app.ActivityThread;
import android.content.Context;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.util.Log;

import android.os.IPluginService;
import android.os.IPluginContactsInterposer;
import android.os.IPluginLocationInterposer;
import android.os.IPluginCalendarInterposer;

import com.android.permissionsplugin.PermissionsPluginOptions;

import java.util.List;

/**
 * This class represents a proxy to a distinct plugin service.
 *
 * {@hide}
 */
public class PluginProxy {
    private static final String PLUGIN_MAIN = ".PluginMain";

    public static final String INTERPOSER_LOCATION = "location";
    public static final String INTERPOSER_CONTACTS = "contacts";
    public static final String INTERPOSER_CALENDAR = "calendar";
    public static final String INTERPOSER_CAMERA = "camera";

    private final String mPackage;
    private final List<String> mInterposers;

    private ComponentName mComponent = null;
    private ServiceConnection mConnection = null;
    private PluginServiceProxy mService = null;
    private IPluginLocationInterposer mLocationInterposer = null;
    private IPluginContactsInterposer mContactsInterposer = null;
    private IPluginCalendarInterposer mCalendarInterposer = null;
    private PluginCameraInterposerProxy mCameraInterposer = null;

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
    public PluginProxy(String pluginPackage, List<String> interposers) {
        mPackage = pluginPackage;
        mInterposers = interposers;

        mComponent = new ComponentName(mPackage, mPackage + PLUGIN_MAIN);
        mConnection = new ServiceConnection() {
                @Override
                public void onServiceConnected(ComponentName name, IBinder service) {
                    synchronized(PluginProxy.this) {
                        service = Binder.allowBlocking(service);
                        mService = (PluginServiceProxy) PluginService.asInterface(service);
                        retrieveInterposers();
                        mConnecting = false;
                        mConnected = true;
                        PluginProxy.this.notifyAll();
                    }
                }

                @Override
                public void onServiceDisconnected(ComponentName name) {
                    synchronized(PluginProxy.this) {
                        reset();
                        PluginProxy.this.notifyAll();
                    }
                }

                @Override
                public void onBindingDied(ComponentName name) {
                    synchronized(PluginProxy.this) {
                        Context c = ActivityThread.currentApplication();
                        if (c != null) {
                            c.unbindService(this);
                        }
                        reset();
                        PluginProxy.this.notifyAll();
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
            try {
                if (interposer.equals(INTERPOSER_LOCATION)) {
                    IBinder binder = mService.getLocationInterposerRaw();
                    if (binder != null) {
                        binder = Binder.allowBlocking(binder);
                        mLocationInterposer =
                            IPluginLocationInterposer.Stub.asInterface(binder);
                    }
                } else if (interposer.equals(INTERPOSER_CONTACTS)) {
                    IBinder binder = mService.getContactsInterposerRaw();
                    if (binder != null) {
                        binder = Binder.allowBlocking(binder);
                        mContactsInterposer =
                            IPluginContactsInterposer.Stub.asInterface(binder);
                    }
                } else if (interposer.equals(INTERPOSER_CALENDAR)) {
                    IBinder binder = mService.getCalendarInterposerRaw();
                    if (binder != null) {
                        binder = Binder.allowBlocking(binder);
                        mCalendarInterposer =
                            IPluginCalendarInterposer.Stub.asInterface(binder);
                    }
                } else if (interposer.equals(INTERPOSER_CAMERA)) {
                    IBinder binder = mService.getCameraInterposerRaw();
                    if (binder != null) {
                        binder = Binder.allowBlocking(binder);
                        mCameraInterposer =
                            PluginCameraInterposer.asInterface(binder);
                    }
                }
            } catch (RemoteException ex) {
                Log.d(PermissionsPluginOptions.TAG, "Could not get " + interposer + " interposer from plugin service " + mPackage);
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
            Log.d(PermissionsPluginOptions.TAG, "Context is null. Can't connect to: " + mPackage);
            return false;
        }

        Intent intent = new Intent();
        intent.setComponent(mComponent);

        boolean ret = c.bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
        if (ret) {
            mConnecting = true;
        } else {
            Log.d(PermissionsPluginOptions.TAG, "Unable to bind to plugin service: " + mPackage);
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

    public IPluginLocationInterposer getLocationInterposer() {
        return mLocationInterposer;
    }

    public IPluginContactsInterposer getContactsInterposer() {
        return mContactsInterposer;
    }

    public IPluginCalendarInterposer getCalendarInterposer() {
        return mCalendarInterposer;
    }

    public PluginCameraInterposerProxy getCameraInterposer() {
        return mCameraInterposer;
    }

    public String getPackage() {
        return mPackage;
    }
}
