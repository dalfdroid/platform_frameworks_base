package com.android.permissionsplugin;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.ArrayList;
import java.util.List;

public class PermissionsPlugin implements Parcelable{

    public static final String ALL_PACKAGES = "*";

    // Constants for APIs supported by plugin
    public static final String PLUGIN_API_LOCATION = "location";

    // Row id of this plugin in plugin db
    public long id;

    // Package name of the plugin
    public String packageName;

    // Packages (apps) supported by this plugin
    // '*' means all apps are supported
    public ArrayList<String> supportedPackages;

    // Apis supported by this plugin
    public ArrayList<String> supportedAPIs;

    // Apps selected by user to apply this plugin to
    // must be a subset of the supportedPackages
    public ArrayList<String> targetPackages;

    // APIs selected by user for this plugin
    // must be a subset of the supportedAPIs
    public ArrayList<String> targetAPIs;

    // Flag to check if plugin is active
    public Boolean isActive;

    public PermissionsPlugin(String packageName){
        this.packageName = packageName;

        // Set default values
        id = -1;
        isActive = false;

        // Initialize data
        supportedPackages = new ArrayList<>();
        supportedAPIs = new ArrayList<>();
        targetPackages = new ArrayList<>();
        targetAPIs = new ArrayList<>();
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
        targetPackages = dest.createStringArrayList();
        targetAPIs = dest.createStringArrayList();
        isActive = (dest.readInt() == 1);
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeLong(id);
        dest.writeString(packageName);
        dest.writeStringList(supportedPackages);
        dest.writeStringList(supportedAPIs);
        dest.writeStringList(targetPackages);
        dest.writeStringList(targetAPIs);
        dest.writeInt(isActive ? 1 : 0);
    }


    @Override
    public int describeContents() {
        return 0;
    }

}