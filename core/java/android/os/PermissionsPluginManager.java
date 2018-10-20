package android.os;

import android.app.ActivityThread;
import android.database.BulkCursorDescriptor;
import android.database.CursorWindow;
import android.location.Location;
import android.util.Log;
import android.net.Uri;
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

    private static void copySourceToTargetParcel(Parcel sourceParcel,
            Parcel targetParcel, ArrayDeque<ParcelObject> objectsToWrite) {

        targetParcel.stopRecording();
        int originalPos = 0;

        while (!objectsToWrite.isEmpty()) {
            ParcelObject parcelObject = objectsToWrite.getFirst();
            if (originalPos < parcelObject.mStartPos) {
                int length = parcelObject.mStartPos - originalPos;
                targetParcel.appendFrom(sourceParcel, originalPos, length);
            }

            switch (parcelObject.mObjectType) {
            case ParcelObject.BINDER_OBJECT:
                IBinder val = (IBinder) parcelObject.mObject;
                targetParcel.writeStrongBinder(val);
                break;

            case ParcelObject.PERTURBABLE_OBJECT:
                PerturbableObject pertObj = (PerturbableObject) parcelObject;
                Parcelable parcelable = pertObj.getLatestParcelable();
                parcelable.writeToParcel(targetParcel, pertObj.mWriteFlags);
                break;

            default:
                String errorMsg = "Panic! Unexpected object while creating target parcel: " +
                    parcelObject;
                Log.d(TAG, errorMsg);
                throw new UnsupportedOperationException(errorMsg);
            }

            originalPos = parcelObject.mEndPos;
            objectsToWrite.removeFirst();
        }

        if (originalPos < sourceParcel.dataSize()) {
            int length = sourceParcel.dataSize() - originalPos;
            targetParcel.appendFrom(sourceParcel, originalPos, length);
        }
    }

    private void perturbObject(String targetPkg, PluginProxy pluginProxy,
            PerturbableObject perturbableObject) {
        Parcelable parcelable = perturbableObject.mParcelable;

        switch (perturbableObject.mPerturbableType) {
        case LOCATION:
            Location location = (Location) parcelable;
            IPluginLocationInterposer locInterposer =
                (IPluginLocationInterposer) pluginProxy.getLocationInterposer();
            if (locInterposer != null) {
                try {
                    location = locInterposer.modifyLocation(targetPkg, location);
                    perturbableObject.setPerturbedObject(location);
                } catch (RemoteException ex) {
                    Log.d(TAG, "RemoteException while modifying location for " +
                          targetPkg);
                }
            }
            break;

        case CONTACTS:

            CursorWindow window = (CursorWindow) parcelable;
            IPluginContactsInterposer contactsInterposer =
                (IPluginContactsInterposer) pluginProxy.getContactsInterposer();

            PerturbableObject.QueryMetadata metadata =
                (PerturbableObject.QueryMetadata) perturbableObject.mMetadata;

            if (metadata == null) {
                Log.d(TAG, "Metadata is not supported to be null for contacts. Breaking!");
                break;
            }

            if (contactsInterposer != null) {

                try {
                    CursorWindow perturbedWindow = contactsInterposer.modifyData
                        (targetPkg, metadata.url, metadata.projection,
                         metadata.queryArgs, window, metadata.columnNames, metadata.count);

                    if (perturbedWindow != null) {
                        perturbableObject.setPerturbedObject(perturbedWindow);
                    }
                } catch (RemoteException ex) {
                    Log.d(TAG, "RemoteException while modifying contact for " + targetPkg);
                }
            }
            break;


        default:
            Log.d(TAG, "Unhandled perturbable type: " + perturbableObject.mPerturbableType
                  + ", perturbableObject: " + perturbableObject
                  + ". Writing original ...");
            break;
        }
    }

    private Parcel perturbAllDataImpl(String targetPkg, Parcel sourceParcel, Parcel targetParcel) {

        if (!sourceParcel.hasPerturbables()) {
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
            Log.d(TAG, "Proceeding to perturb data for " + targetPkg);
        }

        ArrayDeque<ParcelObject> recordedObjects = sourceParcel.getRecordedObjects();
        ArrayDeque<ParcelObject> objectsToWrite = new ArrayDeque<>();

        sourceParcel.stopRecording();

        int originalParcelPos = 0;

        for (ParcelObject recordedObject : recordedObjects) {
            switch (recordedObject.mObjectType) {
            case ParcelObject.BINDER_OBJECT:
                objectsToWrite.add(recordedObject);
                break;

            case ParcelObject.PERTURBABLE_OBJECT:
                PerturbableObject perturbableObject =
                    (PerturbableObject) recordedObject;
                perturbObject(targetPkg, pluginProxy, perturbableObject);
                objectsToWrite.add(perturbableObject);
                break;

            default:
                String errorMsg = "Panic! Unexpected recorded object type encountered: " +
                    recordedObject;
                Log.d(TAG, errorMsg);
                throw new UnsupportedOperationException(errorMsg);
            }
        }

        if (targetParcel == null) {
            targetParcel = Parcel.obtain();
        }

        copySourceToTargetParcel(sourceParcel, targetParcel, objectsToWrite);
        return targetParcel;
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
    public static Parcel perturbAllData(String targetPkg, Parcel sourceParcel) {

        if (targetPkg == null || targetPkg.length() == 0) {
            return null;
        }

        return getInstance().perturbAllDataImpl(targetPkg, sourceParcel, null);
    }

    /**
     * {@hide}
     */
    public static void fixupTargetParcel(int callingUid, Parcel sourceParcel, Parcel targetParcel) {

        if (!sourceParcel.hasPerturbables()) {
            copySourceToTargetParcel(sourceParcel, targetParcel,
                sourceParcel.getRecordedObjects());
            return;
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
                copySourceToTargetParcel(sourceParcel, targetParcel,
                    sourceParcel.getRecordedObjects());
                return;
            }

            local.uidsToPackage.put(callingUid, targetPkg);
        }

        Parcel val = local.perturbAllDataImpl(targetPkg, sourceParcel, targetParcel);
        if (val == null) {
            // The attempt to perturb data was aborted for some reason, so we
            // must copy all recorded objects from the source parcel.
            copySourceToTargetParcel(sourceParcel, targetParcel,
                sourceParcel.getRecordedObjects());
        }
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
