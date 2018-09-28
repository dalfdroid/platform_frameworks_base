package android.app;

import android.util.Log;

import dalvik.system.DexClassLoader;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * This is the manager of the app proxies belonging to the different plugins.
 * @hide
 */
public class PermissionsPluginProxyManager implements
        PermissionsPluginProxyCallbacks {

    private static final String TAG = "heimdall";
    private static final boolean DEBUG_MESSAGES = true;

    private static final String BRIDGE_PATH="/system/framework/permissionspluginhelper.jar";
    private static final String BRIDGE_PACKAGE = "com.android.permissionsplugin";
    private static final String BRIDGE_MAIN_CLASS = BRIDGE_PACKAGE + ".PermissionsBridge";
    private static final String BRIDGE_LOCATION_HOOK_CLASS = BRIDGE_PACKAGE + ".LocationHooks";

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

    private String mPackageName = null;
    private boolean initialized = false;
    private ClassLoader mClassLoader = null;
    private DexClassLoader mBridgeClassLoader = null;

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

        try {
            mBridgeClassLoader = new DexClassLoader(BRIDGE_PATH, "", null, classLoader);
            Class<?> clazz = Class.forName(BRIDGE_MAIN_CLASS, true, mBridgeClassLoader);
            boolean proxyCallbacksSet = false;
            for (Method m : clazz.getDeclaredMethods()) {
                if (m.getName().equals("setProxyCallbacks")) {
                    m.invoke(null, this);
                    proxyCallbacksSet = true;
                    break;
                }
            }

            if (!proxyCallbacksSet) {
                Log.d(TAG, "Unable to load bridge because the proxy callbacks were not set.");
                return;
            }

        } catch (Exception ex) {
            Log.d(TAG, "Unable to load bridge: " + ex);
            return;
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
        Class<?> classToHook = null;
        Class<?> permissionsHookClass = null;

        try {
            classToHook = Class.forName("com.google.android.gms.location.FusedLocationProviderClient",
                                  true, mClassLoader);
        } catch (ClassNotFoundException ex) {
            if (DEBUG_MESSAGES) {
                Log.d(TAG, "hookFusedLocationProviderClient: not hooking for package: "
                      + mPackageName);
            }
            return;
        }

        try {
            permissionsHookClass = Class.forName(BRIDGE_LOCATION_HOOK_CLASS, true,
                                                 mBridgeClassLoader);
        } catch (ClassNotFoundException ex) {
            if (DEBUG_MESSAGES) {
                Log.d(TAG, "hookFusedLocationProviderClient: could not bridge hook class."
                      + mPackageName);
            }
            return;
        }

        Method methodRequestLocationUpdates = null;
        for (Method m : classToHook.getDeclaredMethods()) {
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
        for (Method m : permissionsHookClass.getDeclaredMethods()) {
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

    @Override
    public Object modifyLocationData(Object locationResult) {
        if (DEBUG_MESSAGES) {
            Log.d(TAG, "modifyLocationData called for: " + locationResult);
        }
        return locationResult;
    }
}
