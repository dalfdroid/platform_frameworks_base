package com.android.permissionsplugin;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.ArrayList;
import java.util.List;
    
public class PermissionsPlugin implements Parcelable{

    public static final String ALL_PACKAGES = "*";

    // Row id of this plugin in plugin db
    public long id;

    // Package name of the plugin
    public String packageName;

    // Main class of the plugin
    public String proxyClass;

    // Packages (apps) supported by this plugin
    public ArrayList<String> supportedPackages;

    // Apis supported by this plugin
    public ArrayList<String> supportedAPIs;

    // Flag to check if plugin is active
    public Boolean isActive;

//        // Apps selected by user to apply this plugin to
//        // must be a subset of the supportedPackages
//        public ArrayList<String> targetPackages;
//
//        // APIs selected by user for this plugin
//        // must be a subset of the supportedAPIs
//        public ArrayList<String> targetAPIs;

    public PermissionsPlugin(String packageName){
        this.packageName = packageName;

        // Set default values
        id = -1;
        isActive = false;
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
        proxyClass = dest.readString();
        supportedPackages = dest.createStringArrayList();
        supportedAPIs = dest.createStringArrayList();
        isActive = (dest.readInt() == 1);
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeLong(id);
        dest.writeString(packageName);
        dest.writeString(proxyClass);
        dest.writeStringList(supportedPackages);
        dest.writeStringList(supportedAPIs);
        dest.writeInt(isActive ? 1 : 0);
    }


    @Override
    public int describeContents() {
        return 0;
    }
    
}