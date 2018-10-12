package android.os;

import android.util.Log;

import android.location.Location;

import java.util.ArrayDeque;
import java.util.HashMap;

/**
 * {@hide}
 */
public class PermissionsPluginManager {

    private static final boolean DEBUG = true;
    private static final String TAG = "heimdall";

    private static final ThreadLocal<PermissionsPluginManager> sThreadLocal =
        new ThreadLocal<>();
    static {
        sThreadLocal.set(new PermissionsPluginManager());
    }

    private HashMap<Integer, String> uidsToPackage;

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
}
