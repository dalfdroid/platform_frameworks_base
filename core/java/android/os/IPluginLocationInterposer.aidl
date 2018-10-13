package android.os;

import android.location.Location;

/**
 * The location interposer component in a plugin must implement this interface.
 *
 * {@hide}
 */
interface IPluginLocationInterposer {

    /**
     * This will be called when there is location for the plugin to modify.
     *
     * @param targetAppPkg The package name of the target app that will be
     * delivered the modified location.
     * @param originalLocation The original location data that was going to be
     * sent to the application.
     *
     * @return The modified location.
     */
    Location modifyLocation(String targetAppPkg, in Location originalLocation);
}
