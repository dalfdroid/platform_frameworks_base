package android.app;

import android.util.Log;

import java.lang.reflect.Method;

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
    private ClassLoader mClassLoader;

    /* package */ PermissionsPluginProxyManager() {
        // Nothing to do here at the moment
    }

    /**
     * Initialize the proxy manager. This must be called just once per
     * application instance. If the current app is an internal android or google
     * app, no initialization will be done.
     *
     * @param packageName The current application's package name.
     * @param classLoader The base class loader used by the app.
     */
    /* package */ void initialize(String packageName, ClassLoader classLoader) {

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
        mClassLoader = classLoader;
        initializeHooks();

        initialized = true;
        if (DEBUG_MESSAGES) {
            Log.d(TAG, "Initialized the PermissionsPluginProxyManager for package: "
                  + mPackageName);
        }
    }

    private void initializeHooks() {
        if (!ApiInterceptor.initialize(mPackageName)) {
            Log.d(TAG, "Unable to initialize the API interceptor for package: " +
                  mPackageName);
            throw new IllegalStateException("Unable to intialize the API interceptors!");
        }
        hookFusedLocationProviderClient();
    }

    private void hookFusedLocationProviderClient() {
        Class<?> clazz = null;

        try {
            clazz = Class.forName("com.google.android.gms.location.FusedLocationProviderClient",
                                  true, mClassLoader);
        } catch (ClassNotFoundException ex) {
            if (DEBUG_MESSAGES) {
                Log.d(TAG, "hookFusedLocationProviderClient: not hooking for package: "
                      + mPackageName);
            }
            return;
        }

        Method methodRequestLocationUpdates = null;
        for (Method m : clazz.getDeclaredMethods()) {
            if (m.getName().equals("requestLocationUpdates") && (m.getParameterCount() == 3)) {
                methodRequestLocationUpdates = m;
            }
        }

        if (methodRequestLocationUpdates == null) {
            if (DEBUG_MESSAGES) {
                Log.d(TAG, "hookFusedLocationProviderClient: cannot find requestLocationUpdates for package: "
                      + mPackageName);
            }
            return;
        }

        Method targetHook = null;
        Method targetBackup = null;
        for (Method m : PermissionsPluginProxyManager.class.getDeclaredMethods()) {
            String name = m.getName();
            if (name.equals("FusedLocationHook_targetHook")) {
                targetHook = m;
            } else if (name.equals("FusedLocationHook_targetBackup")) {
                targetBackup = m;
            }
        }

        if (targetHook == null || targetBackup == null) {
            if (DEBUG_MESSAGES) {
                Log.d(TAG, "hookFusedLocationProviderClient: cannot find necessary hooks for package: "
                      + mPackageName + ", targetHook: " + targetHook + ", targetBackup: " + targetBackup);
            }
            return;
        }

        ApiInterceptor.hookMethod(methodRequestLocationUpdates, targetHook, targetBackup);
    }

    public static Object FusedLocationHook_targetHook(Object thiz, Object argA, Object argB, Object argC) {
        Log.d(TAG, "FusedLocationHooks.targetHook is running. Arguments are: " + argA + " - " + argB + " - " + argC);
        return FusedLocationHook_targetBackup(thiz, argA, argB, argC);
    }

    public static Object FusedLocationHook_targetBackup(Object thiz, Object argA, Object argB, Object argC) {
        /** The code here should never be run. It will be replaced during runtime by the hooking mechanism. */
        return null;
    }

}
