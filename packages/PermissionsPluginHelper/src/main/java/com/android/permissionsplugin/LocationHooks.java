package com.android.permissionsplugin;

import android.os.Looper;
import android.util.Log;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationAvailability;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;

import com.android.permissionsplugin.api.LocationInterposer;

import java.util.Map;
import java.util.HashMap;

public class LocationHooks {
    private static final String TAG = "heimdall";

    public static Map<String, LocationInterposer> locationInterposers = new HashMap<>();

    public static Task<Void> FusedLocationHook_targetHook(FusedLocationProviderClient thiz,
                                                          LocationRequest request,
                                                          final LocationCallback callback,
                                                          Looper looper) {
        Log.d(TAG, "FusedLocationHooks.targetHook." +
              " Request: " + request +
              ", Callback: " + callback +
              ", Looper: " + looper);

        LocationCallback wrappedCB = new LocationCallback() {

            private final LocationCallback originalCB = callback;
            private boolean processed = false;

            @Override
            public void onLocationResult(LocationResult locationResult) {
                LocationResult modifiedResult = locationResult;

                synchronized (locationInterposers) {
                    for (LocationInterposer interposer : locationInterposers.values()) {
                        modifiedResult = interposer.modifyLocationData(modifiedResult);
                    }
                }

                originalCB.onLocationResult(modifiedResult);
            }

            @Override
            public void onLocationAvailability(LocationAvailability locationAvailability) {
                originalCB.onLocationAvailability(locationAvailability);
            }
        };

        return FusedLocationHook_targetBackup(thiz, request, wrappedCB, looper);
    }

    public static Task<Void> FusedLocationHook_targetBackup(FusedLocationProviderClient thiz,
                                                        LocationRequest request,
                                                        LocationCallback callback,
                                                        Looper looper) {
        /** The code here should never be run. It will be replaced during
         * runtime by the hooking mechanism. */
        return null;
    }
}
