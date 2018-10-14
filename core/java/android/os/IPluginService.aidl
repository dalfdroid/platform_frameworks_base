package android.os;

import android.os.IPluginLocationInterposer;

/**
 * The main interface that every plugin service must implement.
 *
 * {@hide}
 */
interface IPluginService {

    /**
     * Returns the location interposer of this plugin. May return null if the
     * plugin does not interpose on location data.
     */
    IPluginLocationInterposer getLocationInterposer();
}
