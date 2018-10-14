package android.os;

import android.app.ActivityThread;
import android.util.Log;
import android.location.Location;

import android.content.pm.ParceledListSlice;

import com.android.permissionsplugin.PermissionsPlugin;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.List;
import java.util.Collections;

/**
 * {@hide}
 */
public class PermissionsPluginManager {

    private static final ThreadLocal<PermissionsPluginManager> sThreadLocal =
        new ThreadLocal<>();

    private static final boolean DEBUG = true;
    private static final String TAG = "heimdall";

    private static final HashMap<String, PluginProxy> sPluginProxies =
        new HashMap<>();

    private static boolean mConnectMethodInUse = false;

    /**
     * Note: all non-static class variables below will be thread-local.
     */
    private HashMap<Integer, String> uidsToPackage;

    private static PluginProxy connectToPluginService(String pluginPackage,
        List<String> interposers) {

        synchronized (sPluginProxies) {
            while (mConnectMethodInUse) {
                try {
                    sPluginProxies.wait();
                } catch (InterruptedException ex) {
                    Log.d(TAG, "Unexpected interruption while waiting to connect to " +
                          pluginPackage + ". Aborting ...");
                    return null;
                }
            }
            mConnectMethodInUse = true;

            PluginProxy pluginProxy = sPluginProxies.get(pluginPackage);
            if (pluginProxy == null) {
                pluginProxy = new PluginProxy(pluginPackage, interposers);
                sPluginProxies.put(pluginPackage, pluginProxy);
            }

            if (pluginProxy.isConnected()) {
                mConnectMethodInUse = false;
                return pluginProxy;
            }

            if (DEBUG) {
                Log.d(TAG, "Connecting to plugin service: " + pluginPackage +
                      " with interposers " + interposers);
            }

            boolean startedConnecting = pluginProxy.connect();
            if (!startedConnecting) {
                Log.d(TAG, "Failed to try bind service to : " + pluginPackage);
                mConnectMethodInUse = false;
                return pluginProxy;
            }

            synchronized(pluginProxy) {
                while (pluginProxy.isTryingToConnect() &&
                       !pluginProxy.isConnected()) {
                    try {
                        pluginProxy.wait();
                    } catch (InterruptedException ex) {
                        break;
                    }
                }
            }

            if (!pluginProxy.isConnected()) {
                Log.d(TAG, "Attempt to connect to plugin service " + pluginPackage + " ultimately failed!");
            } else {
                if (DEBUG) {
                    Log.d(TAG, "Connected to: " + pluginPackage);
                }
            }

            mConnectMethodInUse = false;
            return pluginProxy;
        }
    }

    private Parcel perturbAllDataImpl(String targetPkg, Parcel originalParcel, boolean modifyOriginal) {

        ArrayDeque<PerturbableObject> perturbables = originalParcel.getPerturbables();

        if (perturbables == null || perturbables.size() == 0) {
            return null;
        }

        // Check if any active plugin is available for the target package.
        // If so, proceed with the rest of the code. Otherwise, return null.
        List<PermissionsPlugin> pluginList = getActivePermissionsPluginsForApp(targetPkg);
        if (DEBUG) {
            Log.d(TAG, "Received " + pluginList.size() +
                  " active plugins for app: " + targetPkg);
        }

        if (pluginList == null || pluginList.isEmpty()) {
            return null;
        }

        // TODO: For now, we only support one active plugin per app.
        // In particular, we consider the first available active plugin.
        // In future, we should allow multiple plugins.
        PermissionsPlugin plugin = pluginList.get(0);

        PluginProxy pluginProxy =
            connectToPluginService(plugin.packageName, plugin.supportedAPIs);

        if (pluginProxy == null || !pluginProxy.isConnected()) {
            return null;
        }

        if (DEBUG) {
            Log.d(TAG, "Proceeding to perturb data for " + targetPkg +
                  ". ModifyOriginal: " + modifyOriginal);
        }

        Parcel perturbedParcel = Parcel.obtain();
        originalParcel.setIgnorePerturbables();

        int originalParcelPos = 0;

        for (PerturbableObject perturbableObject : perturbables) {

            int dataStartPos = perturbableObject.parcelStartPos;
            int dataEndPos = perturbableObject.parcelEndPos;

            if (originalParcelPos < dataStartPos) {
                int length = dataStartPos - originalParcelPos;
                perturbedParcel.appendFrom(originalParcel, originalParcelPos, length);
            }

            Parcelable object = perturbableObject.object;
            switch (perturbableObject.type) {
            case LOCATION:
                Location location = (Location) object;
                IPluginLocationInterposer locInterposer =
                    (IPluginLocationInterposer) pluginProxy.getLocationInterposer();

                if (locInterposer != null) {
                    try {
                        location = locInterposer.modifyLocation(targetPkg, location);
                    } catch (RemoteException ex) {
                        if (DEBUG) {
                            Log.d(TAG, "RemoteException while modifying location for " +
                                  targetPkg);
                        }
                    }
                }

                object = location;
                break;

            default:
                Log.d(TAG, "Unhandled parcelable: " + perturbableObject.type +
                      ". Writing original ...");
                break;
            }

            object.writeToParcel(perturbedParcel, perturbableObject.writeFlags);
            originalParcelPos = dataEndPos;
        }

        if (originalParcelPos < originalParcel.dataSize()) {
            int length = originalParcel.dataSize() - originalParcelPos;
            perturbedParcel.appendFrom(originalParcel, originalParcelPos, length);
        }

        Parcel parcelToReturn = null;

        if (modifyOriginal) {
            originalParcel.setDataPosition(0);
            originalParcel.appendFrom(perturbedParcel, 0, perturbedParcel.dataPosition());
            perturbedParcel.recycle();

            parcelToReturn = originalParcel;
        } else {
            parcelToReturn = perturbedParcel;
        }

        return parcelToReturn;
    }

    private static PermissionsPluginManager getInstance() {
        PermissionsPluginManager instance = sThreadLocal.get();
        if (instance == null) {
            instance = new PermissionsPluginManager();
            sThreadLocal.set(instance);
        }

        return instance;
    }

    /**
     * {@hide}
     */
    public static Parcel perturbAllData(String targetPkg, Parcel originalParcel) {

        if (targetPkg == null || targetPkg.length() == 0) {
            return null;
        }

        return getInstance().perturbAllDataImpl(targetPkg, originalParcel, false);
    }

    /**
     * {@hide}
     */
    public static Parcel perturbAllData(int callingUid, Parcel originalParcel) {

        ArrayDeque<PerturbableObject> perturbables = originalParcel.getPerturbables();
        if (perturbables == null || perturbables.size() == 0) {
            return null;
        }

        PermissionsPluginManager local = getInstance();
        if (local.uidsToPackage == null) {
            local.uidsToPackage = new HashMap<>();
        }

        String targetPkg = local.uidsToPackage.get(callingUid);
        if (targetPkg == null) {
            try {
                targetPkg = ActivityThread.getPackageManager().getNameForUid(callingUid);
            } catch (RemoteException ex) {
                Log.d(TAG, "Could not retrieve package name of uid " + callingUid + ". RemoteException: " + ex);
                return null;
            }

            local.uidsToPackage.put(callingUid, targetPkg);
        }

        return local.perturbAllDataImpl(targetPkg, originalParcel, true);
    }


    // Retrieve list of active permissions plugin for a given package    
    private List<PermissionsPlugin> getActivePermissionsPluginsForApp(String appPackage){
        try {
            ParceledListSlice<PermissionsPlugin> parceledList =
                    ActivityThread.getPackageManager().getActivePermissionsPluginsForApp(appPackage);
            if (parceledList == null) {
                return Collections.emptyList();
            }
            return parceledList.getList();
        } catch (RemoteException e) {
            Log.d(TAG, "Could not retrieve permissions plugin for app " + appPackage + ". RemoteException: " + e);
            return Collections.emptyList();
        }
    }
}
