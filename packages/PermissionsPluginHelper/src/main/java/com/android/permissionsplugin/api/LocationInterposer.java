package com.android.permissionsplugin.api;

import com.google.android.gms.location.LocationResult;

/**
 * A data interposer that interposes on location data and enables plugins to
 * have the opportunity to modify location data before it is passed to the
 * app. If you use this interposer, you need to add the following implementation
 * dependency to your plugin's build.gradle file:
 *
 * implementation 'com.google.android.gms:play-services-location:15.0.1'
 */
public abstract class LocationInterposer implements IDataInterposer {

    /**
     * Called just before the framework sends location data to the app.
     *
     * @param LocationResult The locationResult that will be passed to the
     * app. Note that this may already be modified by other plugins that ran
     * earlier.
     *
     * @return The LocationResult that will be returned to the app. If your
     * plugin does not have any modifications to make, you *must* return the
     * input argument.
     */
    public abstract LocationResult modifyLocationData(LocationResult locationResult);

    @Override
    public String getName() {
        return "LocationInterposer";
    }

}
