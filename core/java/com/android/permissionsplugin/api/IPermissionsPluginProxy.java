package com.android.permissionsplugin.api;

/**
 * This interface must bed implemented by the main proxy class in a
 * plugin. Additionally, the plugin must have a public constructor that does not
 * accept any arguments.
 */
public interface IPermissionsPluginProxy {

    /**
     * Called by the framework when it first initializes the plugin for an app
     * process.
     *
     * @param appPackage The current app's package name.
     */
    public void initialize(String appPackage);

    /**
     * If the plugin interposes on the location API, this method must return an
     * instance of a class that extends LocationInterposer. Otherwise, it may
     * return null.
     *
     * @return An instance of the LocationInterposer, or null, in case the
     * plugin does not interpose on the location API.
     */
    public IDataInterposer getLocationInterposer();
}
