package com.android.permissionsplugin;

import android.content.Context;

import android.provider.BaseColumns;

import android.content.ContentValues;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteException;

import android.util.Log;
import android.util.ArrayMap;

import com.android.permissionsplugin.PermissionsPlugin;
import com.android.permissionsplugin.PermissionsPluginOptions;

import java.util.List;
import java.util.Arrays;
import java.util.ArrayList;

public class PermissionsPluginDb{

    // Name of the permisisons plugin database
    private static final String DATABASE_NAME = "permissions_plugin.db";

    // When changing the database schema increment the version
    private static final int DATABASE_VERSION = 1;

    /* Inner class that defines the permissions plugin table contents */
    private static class PluginEntry implements BaseColumns {
        // Permissions plugin table name
        public static final String TABLE_NAME = "plugin";

        // Column name for the plugin package stored as string
        public static final String COLUMN_NAME_PACKAGE_NAME = "package_name";

    }

    /* Inner class that defines the target package table contents */
    private static class TargetPackageEntry implements BaseColumns {
    
        // Target package table name
        public static final String TABLE_NAME = "target_package";
        
        // Column name for the plugin id stored as long
        // TODO: plugin_id should be a foreign key from the plugin table
        public static final String COLUMN_NAME_PLUGIN_ID = "plugin_id";

        // Column name for the packages supported by the plugin
        public static final String COLUMN_NAME_SUPPORTED_PACKAGES = "supported_packages";

        // Column name for the APIs supported by the plugin
        public static final String COLUMN_NAME_SUPPORTED_APIS = "supported_apis";

        // Column name for the select status of target package stored as integer
        // 1 - active, 0 - inactive
        public static final String COLUMN_NAME_IS_SELECTED = "is_selected"; 

    }


    private Context mContext;
    private PluginDbHelper mPluginDbHelper;


    private class PluginDbHelper extends SQLiteOpenHelper {

        public PluginDbHelper(Context context) {
            super(context, DATABASE_NAME, null, DATABASE_VERSION);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {

            // SQL query to create plugin table
            final String SQL_CREATE_PLUGIN_TABLE =
                "CREATE TABLE " + PluginEntry.TABLE_NAME + " (" +
                PluginEntry._ID + " INTEGER PRIMARY KEY," +
                PluginEntry.COLUMN_NAME_PACKAGE_NAME + " TEXT NOT NULL UNIQUE)";
            db.execSQL(SQL_CREATE_PLUGIN_TABLE);

            // SQL query to create plugin table
            final String SQL_CREATE_TARGET_PACKAGE_TABLE =
                "CREATE TABLE " + TargetPackageEntry.TABLE_NAME + " (" +
                TargetPackageEntry._ID + " INTEGER PRIMARY KEY," +
                TargetPackageEntry.COLUMN_NAME_PLUGIN_ID + " INTEGER NOT NULL," +
                TargetPackageEntry.COLUMN_NAME_SUPPORTED_PACKAGES + " TEXT," + 
                TargetPackageEntry.COLUMN_NAME_SUPPORTED_APIS + " TEXT," + 
                TargetPackageEntry.COLUMN_NAME_IS_SELECTED + " INTEGER NOT NULL)";
            db.execSQL(SQL_CREATE_TARGET_PACKAGE_TABLE);

            if(PermissionsPluginOptions.DEBUG){
                Log.d(PermissionsPluginOptions.TAG,"Created table " + PluginEntry.TABLE_NAME + " in db "+DATABASE_NAME);
                Log.d(PermissionsPluginOptions.TAG,"Created table " + TargetPackageEntry.TABLE_NAME + " in db "+DATABASE_NAME);
            }
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {

            // SQL query to drop target package table
            final String SQL_DELETE_TARGET_PACKAGE_TABLE = "DROP TABLE IF EXISTS " + TargetPackageEntry.TABLE_NAME;

            db.execSQL(SQL_DELETE_TARGET_PACKAGE_TABLE);
            
            // SQL query to drop plugin table
            final String SQL_DELETE_PLUGIN_TABLE = "DROP TABLE IF EXISTS " + PluginEntry.TABLE_NAME;

            db.execSQL(SQL_DELETE_PLUGIN_TABLE);

            // Create a new table
            onCreate(db);
        }
    }


    public PermissionsPluginDb(Context context){
        mContext = context;
        mPluginDbHelper = new PluginDbHelper(context);
    }


    /**
     * Load permissions plugins from plugin db.
     * Returns a package to plugin mapping.
     * Keys are package names and values are plugins.
     */
    public ArrayMap<String,PermissionsPlugin> loadPlugins(){

        final ArrayMap<String, PermissionsPlugin> plugins = new ArrayMap<String, PermissionsPlugin>();

        try{
            // Gets the data repository in read mode
            SQLiteDatabase db = mPluginDbHelper.getReadableDatabase();

            // Retrieve all plugins from the db
            Cursor cursor = db.query(PluginEntry.TABLE_NAME,null,null,null,null,null,null);

            if(null != cursor){
                // Get the index of the columns we are interested in
                int idIndex = cursor.getColumnIndex(PluginEntry._ID);
                int packageNameIndex = cursor.getColumnIndex(PluginEntry.COLUMN_NAME_PACKAGE_NAME);

                while(cursor.moveToNext()){
                    String packageName = cursor.getString(packageNameIndex);
                    PermissionsPlugin plugin = new PermissionsPlugin(packageName);
                    
                    plugin.id = cursor.getInt(idIndex);
                    
                    plugin.supportedPackages = retrieveSupportedPackages(plugin.id);
                
                    plugin.supportedAPIs = retrieveSupportedAPIs(plugin.id);

                    plugin.targetPackageToAPIs = retrieveTargetPackageInfo(plugin.id);

                    plugins.put(plugin.packageName,plugin);
                }
            }else{
                Log.e(PermissionsPluginOptions.TAG,"Failed to retrieve plugins from plugin db "+DATABASE_NAME);
            }

        }catch(SQLiteException e){
            Log.e(PermissionsPluginOptions.TAG,"Failed to open plugin db " + DATABASE_NAME + ". SQLiteException: "+ e);
        }

        return plugins;
    }

    /**
     * Insert given plugin into plugin db.
     * Return row id of the newly inserted plugin if successful
     * otherwise return -1. 
     */
    public long insertPlugin(PermissionsPlugin plugin){

        if(null == plugin){
            return -1;
        }

        try{
            // Gets the data repository in write mode
            SQLiteDatabase db = mPluginDbHelper.getWritableDatabase();

            // Create a new map of values, where column names are the keys
            ContentValues values = new ContentValues();

            values.put(PluginEntry.COLUMN_NAME_PACKAGE_NAME, plugin.packageName);
            
            // Insert the new row, returning the primary key value of the new row
            long newRowId = db.insert(PluginEntry.TABLE_NAME, null, values);

            // Insert target package info in the target package table
            for (String pkg : plugin.supportedPackages) {
                for (String api : plugin.supportedAPIs) {
                    ContentValues packageValues = new ContentValues();
                    packageValues.put(TargetPackageEntry.COLUMN_NAME_PLUGIN_ID,newRowId);
                    packageValues.put(TargetPackageEntry.COLUMN_NAME_SUPPORTED_PACKAGES,pkg);
                    packageValues.put(TargetPackageEntry.COLUMN_NAME_SUPPORTED_APIS,api);
                    
                    boolean selected = (plugin.targetPackageToAPIs.containsKey(pkg) && 
                                            plugin.targetPackageToAPIs.get(pkg).contains(api));
                    packageValues.put(TargetPackageEntry.COLUMN_NAME_IS_SELECTED,selected?1:0);
                
                    db.insert(TargetPackageEntry.TABLE_NAME,null,packageValues);
                }
            }

            return newRowId;

        }catch(SQLiteException e){
            Log.e(PermissionsPluginOptions.TAG,"Failed to open plugin db " + DATABASE_NAME + ". SQLiteException: "+ e);
            return -1;
        }

    }

    /**
     * Delete given plugin from plugin db.
     * Return 1 if successful and 0 otherwise.
     * It uses plugin.id (row id of the plugin) to 
     * identify the plugin record in the db and delete it.     
     */
    public int deletePlugin(PermissionsPlugin plugin){

        if(null == plugin || -1 == plugin.id){
            return 0;
        }

        try{
            // Gets the data repository in write mode
            SQLiteDatabase db = mPluginDbHelper.getWritableDatabase();

            // Delete all rows from the target package table 
            // that belongs to this plugin
            // Prepare selection criteria to select the plugin row
            String selection = TargetPackageEntry.COLUMN_NAME_PLUGIN_ID + " = ? ";
            String[] selectionArgs = new String[]{String.valueOf(plugin.id)};

            // Delete the row identified by the plugin id
            db.delete(TargetPackageEntry.TABLE_NAME, selection, selectionArgs);

            // Prepare selection criteria to select the plugin row
            String packageSelection = PluginEntry._ID + " = ? ";
            String[] packageSelectionArgs = new String[]{String.valueOf(plugin.id)};

            // Delete the row identified by the plugin id
            int deletedRows = db.delete(PluginEntry.TABLE_NAME, packageSelection, packageSelectionArgs);

            return deletedRows;

        }catch(SQLiteException e){
            Log.e(PermissionsPluginOptions.TAG,"Failed to open plugin db " + DATABASE_NAME + ". SQLiteException: "+ e);
            return 0;
        }

    }

    /**
     * Update given plugin in plugin db.
     * Return 1 if successful and 0 otherwise.
     * It uses plugin.id (row id of the plugin) to 
     * identify the plugin record in the db and update it. 
     * Note: Only target package/APIs can be updated.    
     */
    public int updatePlugin(PermissionsPlugin plugin){

        if(null == plugin || -1 == plugin.id){
            Log.e(PermissionsPluginOptions.TAG,"Failed to update plugin due to invalid plugin");
            return 0;
        }

        try{
            // Gets the data repository in write mode
            SQLiteDatabase db = mPluginDbHelper.getWritableDatabase();

            int updatedRows = 0;

            // Update target package info of this plugin
            for (String pkg : plugin.supportedPackages) {
                for (String api : plugin.supportedAPIs) {
                    ContentValues packageValues = new ContentValues();
                    
                    boolean selected = (plugin.targetPackageToAPIs.containsKey(pkg) && 
                                            plugin.targetPackageToAPIs.get(pkg).contains(api));
                    packageValues.put(TargetPackageEntry.COLUMN_NAME_IS_SELECTED,selected?1:0);
                
                    // Prepare selection criteria to select the desired row
                    String packageSelection = TargetPackageEntry.COLUMN_NAME_PLUGIN_ID + " = ? "
                                                + " AND " + TargetPackageEntry.COLUMN_NAME_SUPPORTED_PACKAGES + " = ? "
                                                + " AND " + TargetPackageEntry.COLUMN_NAME_SUPPORTED_APIS + " =  ? ";
                          
                    String[] packageSelectionArgs = new String[]{String.valueOf(plugin.id),pkg,api};
            
                    updatedRows = db.update(TargetPackageEntry.TABLE_NAME, packageValues, packageSelection, packageSelectionArgs);
                }
            }
             
            return updatedRows;

        }catch(SQLiteException e){
            Log.e(PermissionsPluginOptions.TAG,"Failed to open plugin db " + DATABASE_NAME + ". SQLiteException: "+ e);
            return 0;
        }

    }

    public ArrayList<String> retrieveSupportedPackages(long pluginId) {
        ArrayList<String> supportedPackages = new ArrayList<>();

        // Gets the data repository in read mode
        SQLiteDatabase db = mPluginDbHelper.getReadableDatabase();

        // Prepare selection criteria to select the rows associated with given plugin id
        String selection = TargetPackageEntry.COLUMN_NAME_PLUGIN_ID + " = ? ";
        String[] selectionArgs = new String[]{String.valueOf(pluginId)};

        // Get the columns we are interested in
        String[] columns = new String[]{TargetPackageEntry.COLUMN_NAME_SUPPORTED_PACKAGES};

        // Retrieve distinct supported packages of given plugin from target package table
        Cursor cursor = db.query(true,TargetPackageEntry.TABLE_NAME,columns,selection,selectionArgs,null,null,null,null);
        int supportedPkgIndex = cursor.getColumnIndex(TargetPackageEntry.COLUMN_NAME_SUPPORTED_PACKAGES);

        // Populate list of supported packages
        while (cursor.moveToNext()) {
            String pkg = cursor.getString(supportedPkgIndex);
            supportedPackages.add(pkg);
        }

        return supportedPackages;
    }

    public ArrayList<String> retrieveSupportedAPIs(long pluginId) {
        ArrayList<String> supportedAPIs = new ArrayList<>();

        // Gets the data repository in read mode
        SQLiteDatabase db = mPluginDbHelper.getReadableDatabase();

        // Prepare selection criteria to select the rows associated with given plugin id
        String selection = TargetPackageEntry.COLUMN_NAME_PLUGIN_ID + " = ? ";
        String[] selectionArgs = new String[]{String.valueOf(pluginId)};

        // Get the columns we are interested in
        String[] columns = new String[]{TargetPackageEntry.COLUMN_NAME_SUPPORTED_APIS};

        // Retrieve distinct supported packages of given plugin from target package table
        Cursor cursor = db.query(true,TargetPackageEntry.TABLE_NAME,columns,selection,selectionArgs,null,null,null,null);
        int supportedAPIsIndex = cursor.getColumnIndex(TargetPackageEntry.COLUMN_NAME_SUPPORTED_APIS);

        // Populate map of target package to target APIs
        while (cursor.moveToNext()) {
            String api = cursor.getString(supportedAPIsIndex);
            supportedAPIs.add(api);
        }

        return supportedAPIs;
    }

    public ArrayMap<String, ArrayList<String>> retrieveTargetPackageInfo(long pluginId) {

        ArrayMap<String, ArrayList<String>> targetPackageToAPIs = new ArrayMap<>();

        // Gets the data repository in read mode
        SQLiteDatabase db = mPluginDbHelper.getReadableDatabase();

        // Prepare selection criteria to select the rows associated with given plugin id
        String selection = TargetPackageEntry.COLUMN_NAME_PLUGIN_ID + " = ? ";
        String[] selectionArgs = new String[]{String.valueOf(pluginId)};

        // Get the columns we are interested in
        String[] columns = new String[]{TargetPackageEntry.COLUMN_NAME_SUPPORTED_PACKAGES,
                                        TargetPackageEntry.COLUMN_NAME_SUPPORTED_APIS,
                                        TargetPackageEntry.COLUMN_NAME_IS_SELECTED};

        // Retrieve selected packages and APIs of given plugin from target package table
        Cursor cursor = db.query(TargetPackageEntry.TABLE_NAME,columns,selection,selectionArgs,null,null,null,null);
        int supportedPackagesIndex = cursor.getColumnIndex(TargetPackageEntry.COLUMN_NAME_SUPPORTED_PACKAGES);
        int supportedAPIsIndex = cursor.getColumnIndex(TargetPackageEntry.COLUMN_NAME_SUPPORTED_APIS);
        int isSelectedIndex = cursor.getColumnIndex(TargetPackageEntry.COLUMN_NAME_IS_SELECTED);

        // Populate list of supported packages
        while (cursor.moveToNext()) {
            String pkg = cursor.getString(supportedPackagesIndex);
            String api = cursor.getString(supportedAPIsIndex);
            boolean selected = cursor.getInt(isSelectedIndex)==1?true:false;

            if (!targetPackageToAPIs.containsKey(pkg)) {
                targetPackageToAPIs.put(pkg,new ArrayList<String>());
            }
            
            if (selected) {
                targetPackageToAPIs.get(pkg).add(api);
            }
        }

        return targetPackageToAPIs;

    }

}
