package android.app;

import com.android.permissionsplugin.api.IPermissionsPluginProxy;
import com.android.permissionsplugin.api.LocationInterposer;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.split.DefaultSplitAssetLoader;
import android.content.pm.PackageManager;
import android.content.pm.PackageParser;
import android.content.res.AssetManager;
import android.location.Location;
import android.util.Log;

import dalvik.system.DexClassLoader;

import java.io.File;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * This is the manager of the app proxies belonging to the different plugins.
 * @hide
 */
public class PermissionsPluginProxyManager {

    private static final String TAG = "heimdall";
    private static final boolean DEBUG_MESSAGES = true;

    private static final String BRIDGE_PATH="/system/framework/permissionspluginhelper.jar";
    private static final String BRIDGE_PACKAGE = "com.android.permissionsplugin";

    private static final String BRIDGE_MAIN_CLASS = BRIDGE_PACKAGE + ".PermissionsBridge";

    /** File name in an APK for the permissions plugin manifest file. */
    private static final String PERMISSIONS_PLUGIN_MANIFEST_FILENAME = "PermissionsPlugin.json";

    private static final String JSON_KEY_ROOT = "permissionsplugin";
    private static final String JSON_KEY_PKG = "supportsPkg";
    private static final String JSON_KEY_INTERPOSED_APIS = "interposesOn";
    private static final String JSON_KEY_PROXY_CLASS = "proxyMain";

    private static final String API_LOCATION = "location";

    /** Singleton instance of this class for the app-process. */
    private static PermissionsPluginProxyManager current;

    /** A map between a plugin package name and its location interposer. */
    private Map<String, LocationInterposer> locationInterposers = new HashMap<>();

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

    private Map<String, ClassLoader> mPluginsClassLoader = new HashMap<>();
    private Map<String, Class<?>> mPluginsClasses = new HashMap<>();

    private PackageManager mPm = null;

    /* package */ PermissionsPluginProxyManager() {
        // Nothing to do here at the moment
    }

    /**
     * Initialize the proxy manager. This must be called just once per
     * application instance. If the current app is an internal android or google
     * app, no initialization will be done.
     *
     * @param packageName The current application's package name.
     * @param context The context of the application
     */
    /* package */ void initialize(String packageName, Context context) {

        for (String ignoredPackage : ignoredPackages) {
            if (packageName.startsWith(ignoredPackage)) {
                return;
            }
        }

        if (initialized) {
            throw new UnsupportedOperationException("Cannot initialize PermissionsPluginProxyManager again for "
                                                    + packageName);
        }

        ClassLoader classLoader = context.getClassLoader();
        mPm = context.getPackageManager();

        try {
            mBridgeClassLoader = new DexClassLoader(BRIDGE_PATH, "", null, classLoader);
            Class<?> bridgeClass = Class.forName(BRIDGE_MAIN_CLASS, true, mBridgeClassLoader);

        } catch (Exception ex) {
            Log.d(TAG, "Unable to load bridge: " + ex);
            return;
        }

        mPackageName = packageName;
        mClassLoader = classLoader;
        initializeHooks();

        current = this;

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
    }

    private boolean loadPlugin(String pluginPackageName) {
        try {
            // Get the package.
            ApplicationInfo ai = mPm.getApplicationInfo(pluginPackageName, 0);
            String pluginPath = ai.sourceDir;
            File f = new File(pluginPath);
            PackageParser.PackageLite pkgLite = PackageParser.parsePackageLite(f, 0);

            // Load the JSON manifest
            DefaultSplitAssetLoader loader = new DefaultSplitAssetLoader(pkgLite, 0);
            InputStream is = loader.getBaseAssetManager()
                .open(PERMISSIONS_PLUGIN_MANIFEST_FILENAME, AssetManager.ACCESS_BUFFER);

            int size = is.available();
            byte[] buffer = new byte[size];
            is.read(buffer);
            is.close();

            // Parse the JSON
            String rawJson = new String(buffer, "UTF-8");
            JSONObject json = new JSONObject(rawJson);
            JSONObject root = json.getJSONObject(JSON_KEY_ROOT);

            String pkg = (String) root.getJSONArray(JSON_KEY_PKG).get(0);
            JSONArray apiArray = root.getJSONArray(JSON_KEY_INTERPOSED_APIS);
            String mainClass = root.getString(JSON_KEY_PROXY_CLASS);

            return instantiatePlugin(pluginPath, pluginPackageName, pkg,
                mainClass, apiArray);

        } catch (Exception ex) {
            Log.d(TAG, "Unexpected exception while loading requested plugin " +
                  pluginPackageName + ": " + ex + ", stack trace: " +
                  Log.getStackTraceString(ex));
            return false;
        }
    }

    private boolean instantiatePlugin(String pluginPath, String pluginPackage,
        String supportedPackage, String mainClass, JSONArray apiArray) {

        if (mPluginsClassLoader.containsKey(pluginPackage)) {
            Log.d(TAG, "Plugin " + pluginPackage + " already instantiated!");
            return false;
        }

        try {
            DexClassLoader pluginClassLoader = new DexClassLoader(pluginPath,
                "", null, mClassLoader);

            Class<?> proxyMainClass = Class.forName(mainClass, true, pluginClassLoader);
            IPermissionsPluginProxy proxyMain = (IPermissionsPluginProxy)
                proxyMainClass.newInstance();

            proxyMain.initialize(mPackageName);
            for (int i = 0; i < apiArray.length(); i++) {
                String api = apiArray.optString(i);

                if (api.equals(API_LOCATION)) {
                    LocationInterposer interposer = (LocationInterposer)
                        proxyMain.getLocationInterposer();

                    synchronized (locationInterposers) {
                        locationInterposers.put(pluginPackage, interposer);
                    }
                } else {
                    Log.d(TAG, "Found unsupported API " + api + " while loading plugin "
                          + pluginPackage + " for app " + mPackageName);
                    continue;
                }
            }

            return true;

        } catch (Exception ex) {
            Log.d(TAG, "Unexpected exception while instantiating plugin " +
                  pluginPackage + ": " + ex + ", stack trace: " +
                  Log.getStackTraceString(ex));
            return false;
        }
    }

    /**
     * @hide
     */
    public static void modifyLocation(Location location) {
        if (current == null) {
            return;
        }

        Map<String, LocationInterposer> locInterposers = current.locationInterposers;

        synchronized (locInterposers) {
            for (LocationInterposer interposer : locInterposers.values()) {
                interposer.modifyLocationData(location);
            }
        }
    }
}
