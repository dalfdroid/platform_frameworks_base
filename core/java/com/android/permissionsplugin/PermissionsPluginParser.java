package com.android.permissionsplugin;

import android.content.pm.PackageParser;
import android.content.res.AssetManager;
import com.android.permissionsplugin.PermissionsPlugin;
import com.android.permissionsplugin.PermissionsPluginOptions;

import android.util.ArrayMap;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;

public class PermissionsPluginParser {

    /** File name in an APK for the permissions plugin manifest file. */
    private static final String PERMISSIONS_PLUGIN_MANIFEST_FILENAME = "PermissionsPlugin.json";

    // Constants for parsing permissions plugin JSON file
    private static final String JSON_KEY_ROOT = "permissionsplugin";
    private static final String JSON_KEY_SUPPORTED_PKGS = "supportsPkg";
    private static final String JSON_KEY_INTERPOSED_APIS = "interposesOn";
    private static final String JSON_KEY_ACTIVATE_ON_INSTALL = "activateOnInstall";

    public PermissionsPluginParser(){
    }

    /**
     * Parse plugin given by the packagename
     * @param pkg Package to be parsed
     * @return returns a PermissionsPlugin object with parsed information or null in case of error
     */
    public PermissionsPlugin parsePermissionsPlugin(PackageParser.Package pkg){

        // Check if pkg is a valid plugin
        if(pkg==null || !pkg.isPermissionsPlugin){
            Log.e(PermissionsPluginOptions.TAG,"Given package is either null or not a permissions plugin");
            return null;
        }

        PermissionsPlugin plugin = null;
        try {
            plugin = new PermissionsPlugin(pkg.packageName);

            // Add package apk to asset manager
            final AssetManager assets = new AssetManager();
            int cookie = assets.addAssetPath(pkg.baseCodePath);
            if (cookie == 0) {
                // Can not parse the plugin package due to 
                // failure in adding the apk to the asset manager.
                return null;
            }

            // Get permissions plugin manifest
            InputStream inputStream = assets.open(PERMISSIONS_PLUGIN_MANIFEST_FILENAME, AssetManager.ACCESS_BUFFER);
            int size = inputStream.available();
            byte[] buffer = new byte[size];
            inputStream.read(buffer);
            inputStream.close();

            // Parse the JSON
            String rawJson = new String(buffer, "UTF-8");
            JSONObject json = new JSONObject(rawJson);
            JSONObject root = json.getJSONObject(JSON_KEY_ROOT);

            // Parse supported packages
            JSONArray supportedPackages = root.getJSONArray(JSON_KEY_SUPPORTED_PKGS);
            for(int i=0; i<supportedPackages.length(); i++){
                plugin.supportedPackages.add(supportedPackages.getString(i));
            }

            // Parse supported APIs
            JSONArray supportedAPIs = root.getJSONArray(JSON_KEY_INTERPOSED_APIS);
            for(int i=0; i<supportedAPIs.length(); i++){
                plugin.supportedAPIs.add(supportedAPIs.getString(i));
            }

            // Add each supported package as the target package
            // and activate the plugin for all package/APIs if activateOnInstall flag is set.
            boolean activateOnInstall = root.optBoolean(JSON_KEY_ACTIVATE_ON_INSTALL,false);
            for (String package : plugin.supportedPackages) {
                if (activateOnInstall) {
                    plugin.targetPackageToAPIs.put(package,new ArrayList<>(plugin.supportedAPIs));
                } else {
                    plugin.targetPackageToAPIs.put(package,new ArrayList<>());
                }
            }

        }catch (Exception e){
            Log.e(PermissionsPluginOptions.TAG,"Failed to parse plugin " + pkg.packageName);
            e.printStackTrace();
        }

        return plugin;
    }


}
