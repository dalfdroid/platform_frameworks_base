package android.os;

import android.app.ActivityThread;
import android.util.Log;
import android.location.Location;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.List;

/**
 * {@hide}
 */
public class PermissionsPluginManager {

    private static final ThreadLocal<PermissionsPluginManager> sThreadLocal =
        new ThreadLocal<>();

    private static final boolean DEBUG = true;
    private static final String TAG = "heimdall";

    private static final HashMap<String, PluginService> sPluginServices =
        new HashMap<>();

    private static boolean mConnectMethodInUse = false;

    /**
     * Note: all non-static class variables below will be thread-local.
     */
    private HashMap<Integer, String> uidsToPackage;

    private static PluginService connectToPluginService(String pluginPackage,
        List<String> interposers) {

        synchronized (sPluginServices) {
            while (mConnectMethodInUse) {
                try {
                    sPluginServices.wait();
                } catch (InterruptedException ex) {
                    Log.d(TAG, "Unexpected interruption while waiting to connect to " +
                          pluginPackage + ". Aborting ...");
                    return null;
                }
            }
            mConnectMethodInUse = true;

            PluginService pluginService = sPluginServices.get(pluginPackage);
            if (pluginService == null) {
                pluginService = new PluginService(pluginPackage, interposers);
                sPluginServices.put(pluginPackage, pluginService);
            }

            if (pluginService.isConnected()) {
                mConnectMethodInUse = false;
                return pluginService;
            }

            if (DEBUG) {
                Log.d(TAG, "Connecting to plugin service: " + pluginPackage +
                      " with interposers " + interposers);
            }

            boolean startedConnecting = pluginService.connect();
            if (!startedConnecting) {
                Log.d(TAG, "Failed to try bind service to : " + pluginPackage);
                mConnectMethodInUse = false;
                return pluginService;
            }

            synchronized(pluginService) {
                while (pluginService.isTryingToConnect() &&
                       !pluginService.isConnected()) {
                    try {
                        pluginService.wait();
                    } catch (InterruptedException ex) {
                        break;
                    }
                }
            }

            if (!pluginService.isConnected()) {
                Log.d(TAG, "Attempt to connect to plugin service " + pluginPackage + " ultimately failed!");
            } else {
                if (DEBUG) {
                    Log.d(TAG, "Connected to: " + pluginPackage);
                }
            }

            mConnectMethodInUse = false;
            return pluginService;
        }
    }

    private Parcel perturbAllDataImpl(String targetPkg, Parcel originalParcel, boolean modifyOriginal) {

        ArrayDeque<PerturbableObject> perturbables = originalParcel.getPerturbables();

        if (perturbables == null || perturbables.size() == 0) {
            return null;
        }

        // TODO(nisarg): Check if the given package has plugins. If so, proceed
        // with the rest of the code. Otherwise, return null. For now, return
        // null all the time.
        if (true) {
            return null;
        }

        /**
         *
         * <start of sample plugin code>
         * (remember to import java.util.Arrays)

          if (!targetPkg.equals("com.nr.heimdall.testapp")) {
              return null;
          }

          PluginService pluginService =
              connectToPluginService("blind.al.simpleplugin", Arrays.asList("location"));

          if (!pluginService.isConnected()) {
              return null;
          }

        * <end of sample plugin code>
        */

        if (DEBUG) {
            Log.d(TAG, "Proceeding to perturb data for " + targetPkg + ". ModifyOriginal: " + modifyOriginal);
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
                // TODO(ali or nisarg): Perturb the location here.

                /**
                 * <start of location modifying code>

                IPluginLocationInterposer locInterposer =
                    (IPluginLocationInterposer) pluginService.getLocationInterposer();
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

                * <end of location modifying code>
                */
                object = location;
                break;

            default:
                Log.d(TAG, "Unhandled parcelable: " + perturbableObject.type + ". Writing original ...");
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
                Log.d("heimdall", "Could not retrieve package name of uid " + callingUid + ". RemoteException: " + ex);
                return null;
            }

            local.uidsToPackage.put(callingUid, targetPkg);
        }

        return local.perturbAllDataImpl(targetPkg, originalParcel, true);
    }
}
