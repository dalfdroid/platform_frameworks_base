package com.android.permissionsplugin;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.ArrayList;
import java.util.List;
    
public class PermissionsPlugin implements Parcelable{

    public static final String ALL_PACKAGES = "*";

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
        packageName = dest.readString().intern();
        proxyClass = dest.readString().intern();
        supportedPackages = dest.createStringArrayList();
        internStringArrayList(supportedPackages);
        supportedAPIs = dest.createStringArrayList();
        internStringArrayList(supportedAPIs);
        isActive = (dest.readInt() == 1);
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
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
    
    private static void internStringArrayList(List<String> list) {
        if (list != null) {
            final int N = list.size();
            for (int i = 0; i < N; ++i) {
                list.set(i, list.get(i).intern());
            }
        }
    }

}