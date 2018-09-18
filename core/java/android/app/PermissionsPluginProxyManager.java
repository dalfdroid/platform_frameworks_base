package android.app;

import android.util.Log;

import java.util.ArrayList;
import java.util.List;

/**
 * This is the manager of the app proxies belonging to the different plugins.
 * @hide
 */
public class PermissionsPluginProxyManager {

    private static final String TAG = "heimdall";
    private static final boolean DEBUG_MESSAGES = true;

    /**
     * Do not initialize the proxy manager for packages starting with these
     * package names.
     */
    private static final List<String> ignoredPackages = new ArrayList<>();
    static {
        ignoredPackages.add("android");
        ignoredPackages.add("com.google");
        ignoredPackages.add("com.android");
        ignoredPackages.add("com.qualcomm");
    }

    private String mPackageName;
    private boolean initialized = false;

    /* package */ PermissionsPluginProxyManager() {
        // Nothing to do here at the moment
    }

    /**
     * Initialize the proxy manager. This must be called just once per
     * application instance. If the current app is an internal android or google
     * app, no initialization will be done.
     *
     * @param packageName The current application's package name.
     */
    /* package */ void initialize(String packageName) {

        for (String ignoredPackage : ignoredPackages) {
            if (packageName.startsWith(ignoredPackage)) {
                return;
            }
        }

        if (initialized) {
            throw new UnsupportedOperationException("Cannot initialize PermissionsPluginProxyManager again for "
                                                    + packageName);
        }

        mPackageName = packageName;
        initialized = true;

        if (DEBUG_MESSAGES) {
            Log.d(TAG, "Initialized the PermissionsPluginProxyManager for package:"
                  + packageName);
        }
    }
}
