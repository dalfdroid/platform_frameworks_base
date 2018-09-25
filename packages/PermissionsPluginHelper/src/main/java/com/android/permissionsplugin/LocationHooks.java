package com.android.permissionsplugin;

import android.os.Looper;
import android.util.Log;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationCallback;

public class LocationHooks {
    private static final String TAG = "heimdall";

    public static Task<Void> FusedLocationHook_targetHook(FusedLocationProviderClient thiz,
                                                          LocationRequest request,
                                                          LocationCallback callback,
                                                          Looper looper) {
        Log.d(TAG, "FusedLocationHooks.targetHook." +
              " Request: " + request +
              ", Callback: " + callback +
              ", Looper: " + looper);
        return FusedLocationHook_targetBackup(thiz, request, callback, looper);
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
