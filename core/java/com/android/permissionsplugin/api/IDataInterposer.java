package com.android.permissionsplugin.api;

/**
 * A data interposer is a type of object that a plugin can use to interpose and
 * modify a certain class of data (e.g., location data). Every data interposer
 * object type must implement this interface.
 */
public interface IDataInterposer {
    public String getName();
}
