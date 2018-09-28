package com.android.permissionsplugin;

import android.app.PermissionsPluginProxyCallbacks;

public class PermissionsBridge {
    public static PermissionsPluginProxyCallbacks mProxyCallbacks;

    public static void setProxyCallbacks(PermissionsPluginProxyCallbacks proxyCallbacks) {
        mProxyCallbacks = proxyCallbacks;
    }
}
