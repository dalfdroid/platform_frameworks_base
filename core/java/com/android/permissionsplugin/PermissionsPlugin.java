package com.android.permissionsplugin;

import android.os.Parcel;
import android.os.Parcelable;
import android.os.PluginProxy;

import android.util.ArrayMap;
import java.util.ArrayList;
import java.util.List;

public class PermissionsPlugin implements Parcelable{

    public static final String ALL_PACKAGES = "*";

    // Row id of this plugin in plugin db
    public long id;

    // Package name of the plugin
    public String packageName;

    // Packages (apps) supported by this plugin
    // '*' means all apps are supported
    public ArrayList<String> supportedPackages;

    // Apis supported by this plugin
    public ArrayList<String> supportedAPIs;

    // A map of target package to APIs selected by user.
    // Keys are target packages and must be subset of supportedPackages.
    // Values are list of targetAPIs for the target package and must be
    // subset of supportedAPIs.
    // Note: Currently we consider '*' (indicating all packages)
    // as a special package name and use it just like any other package/app.
    // The actual interpretation of '*' happens in PackageManagerService.
    // The permissions plugin object, parser and db are agnostic to the meaning of '*'.
    public ArrayMap<String, ArrayList<String>> targetPackageToAPIs;

    public PermissionsPlugin(String packageName){
        this.packageName = packageName;

        // Set default values
        id = -1;

        // Initialize data
        supportedPackages = new ArrayList<>();
        supportedAPIs = new ArrayList<>();
        targetPackageToAPIs = new ArrayMap<>();
    }

    @Override
    public String toString() {
        return packageName;
    }

    public static final Parcelable.Creator CREATOR = new Parcelable.Creator<PermissionsPlugin>() {
        public PermissionsPlugin createFromParcel(Parcel in) {
            return new PermissionsPlugin(in);
        }
        public PermissionsPlugin[] newArray(int size) {
            return new PermissionsPlugin[size];
        }
    };

    public PermissionsPlugin(Parcel dest) {
        id = dest.readLong();
        packageName = dest.readString();
        supportedPackages = dest.createStringArrayList();
        supportedAPIs = dest.createStringArrayList();

        // Read targetPackageToAPIs ArrayMap
        // by first reading size of the map
        // and then reading key value pairs
        int n = dest.readInt();
        targetPackageToAPIs = new ArrayMap<>(n);
        for (int i=0; i<n; i++) {
            String key = dest.readString();
            ArrayList<String> value = dest.createStringArrayList();
            targetPackageToAPIs.put(key,value);    
        }
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeLong(id);
        dest.writeString(packageName);
        dest.writeStringList(supportedPackages);
        dest.writeStringList(supportedAPIs);

        // Write targetPackageToAPIs ArrayMap
        // by first writing the size of the map
        // and then writing key value pairs 
        dest.writeInt(targetPackageToAPIs.size());
        for (String key : targetPackageToAPIs.keySet()) {
            dest.writeString(key);
            dest.writeStringList(targetPackageToAPIs.get(key));
        }
    }


    @Override
    public int describeContents() {
        return 0;
    }

}
