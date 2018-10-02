package com.android.permissionsplugin;

import com.android.permissionsplugin.api.IPermissionsPluginProxy;
import com.android.permissionsplugin.api.LocationInterposer;

public class PermissionsBridge {

    /**
     * This is called by the framework via reflection to register the existence
     * of a location plugin.
     *
     * @param pluginPackage The package name of the plugin.
     * @param proxyMain An instance of the plugin main proxy class.
     */
    public static void registerLocationPlugin(String pluginPackage,
            IPermissionsPluginProxy proxyMain) {

        LocationInterposer interposer = (LocationInterposer) proxyMain.getLocationInterposer();

        synchronized(LocationHooks.locationInterposers) {
            LocationHooks.locationInterposers.put(pluginPackage, interposer);
        }
    }
}
