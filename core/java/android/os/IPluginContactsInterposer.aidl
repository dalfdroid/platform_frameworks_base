package android.os;

import android.net.Uri;
import android.os.Bundle;
import android.database.CursorWindow;

/**
 * The contacts interposer component in a plugin must implement this interface.
 *
 * {@hide}
 */
interface IPluginContactsInterposer {

    /**
     * This will be called when there are contacts data for the plugin to modify.
     *
     * @param The package name of the app that will receive the contacts data.
     * @param The url specified by the app when requesting the content provider.
     * @param The projections specified by the app when requesting the content provider.
     * @param The query arguments specified by the app when requesting the
     * content provider.
     * @param The cursor window with the data that will be provided to the app.
     * @param The columns in the cursor window.
     * @param The number of rows in the cursor window.
     *
     * @return A new cursor window with the perturbed data, or null if there is
     * no need to perturb the data.
     */
    CursorWindow modifyData(String targetAppPkg,
        in Uri url, in String[] projection, in Bundle queryArgs,
        in CursorWindow window, in String[] columns, int count);
}
