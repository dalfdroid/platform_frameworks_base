package com.android.permissionsplugin.api;

import android.location.Location;

/**
 * A data interposer that interposes on location data and enables plugins to
 * have the opportunity to modify location data before it is passed to the
 * app.
 *
 * @hide
 */
public abstract class LocationInterposer implements IDataInterposer {

    /**
     * Called just before the framework sends location data to the app.
     *
     * @param Location The location that will be passed to the app. Note that
     * this may already be modified by other plugins that ran earlier.
     *
     */
    public abstract void modifyLocationData(Location location);

    @Override
    public String getName() {
        return "LocationInterposer";
    }
}
