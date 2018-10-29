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

        // Column name for the packages supported by the plugin
        public static final String COLUMN_NAME_SUPPORTED_PACKAGES = "supported_packages";

        // Column name for the APIs supported by the plugin
        public static final String COLUMN_NAME_SUPPORTED_APIS = "supported_apis";

        // Column name for the target packages of the plugin
        public static final String COLUMN_NAME_TARGET_PACKAGES = "target_packages";

        // Column name for the target APIs of the plugin
        public static final String COLUMN_NAME_TARGET_APIS = "target_apis";

        // Column name for the active status of plugin stored as integer
        // 1 - active, 0 - inactive
        public static final String COLUMN_NAME_IS_ACTIVE = "is_active";


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
                PluginEntry.COLUMN_NAME_PACKAGE_NAME + " TEXT NOT NULL UNIQUE," +
                PluginEntry.COLUMN_NAME_SUPPORTED_PACKAGES + " TEXT," + 
                PluginEntry.COLUMN_NAME_SUPPORTED_APIS + " TEXT," + 
                PluginEntry.COLUMN_NAME_TARGET_PACKAGES + " TEXT," +                 
                PluginEntry.COLUMN_NAME_TARGET_APIS + " TEXT," +                                 
                PluginEntry.COLUMN_NAME_IS_ACTIVE + " INTEGER NOT NULL)";
            db.execSQL(SQL_CREATE_PLUGIN_TABLE);

            if(PermissionsPluginOptions.DEBUG){
                Log.d(PermissionsPluginOptions.TAG,"Created table " + PluginEntry.TABLE_NAME + " in db "+DATABASE_NAME);
            }
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {

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
                int supportePkgIndex = cursor.getColumnIndex(PluginEntry.COLUMN_NAME_SUPPORTED_PACKAGES);
                int supporteAPIIndex = cursor.getColumnIndex(PluginEntry.COLUMN_NAME_SUPPORTED_APIS);
                int targetPkgIndex = cursor.getColumnIndex(PluginEntry.COLUMN_NAME_TARGET_PACKAGES);
                int targetAPIIndex = cursor.getColumnIndex(PluginEntry.COLUMN_NAME_TARGET_APIS);
                int isActiveIndex = cursor.getColumnIndex(PluginEntry.COLUMN_NAME_IS_ACTIVE);        

                while(cursor.moveToNext()){
                    String packageName = cursor.getString(packageNameIndex);
                    PermissionsPlugin plugin = new PermissionsPlugin(packageName);
                    
                    plugin.id = cursor.getInt(idIndex);
                    
                    // Parse supported packages
                    String supportedPackages = cursor.getString(supportePkgIndex);
                    plugin.supportedPackages = new ArrayList<String>(Arrays.asList(supportedPackages.split(",")));

                    // Parse supported APIs
                    String supportedAPIs = cursor.getString(supporteAPIIndex);
                    plugin.supportedAPIs = new ArrayList<String>(Arrays.asList(supportedAPIs.split(",")));

                    // Parse target packages
                    String targetPackages = cursor.getString(targetPkgIndex);
                    plugin.targetPackages = new ArrayList<String>(Arrays.asList(targetPackages.split(",")));

                    // Parse target apis
                    String targetAPIs = cursor.getString(targetAPIIndex);
                    plugin.targetAPIs = new ArrayList<String>(Arrays.asList(targetAPIs.split(",")));

                    plugin.isActive = cursor.getInt(isActiveIndex)==1?true:false;

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

            // Put supported packages as a string in the db
            values.put(PluginEntry.COLUMN_NAME_SUPPORTED_PACKAGES, String.join(",",plugin.supportedPackages));

            // Put supported APIs as a string in the db
            values.put(PluginEntry.COLUMN_NAME_SUPPORTED_APIS, String.join(",",plugin.supportedAPIs));

            // Put target packages as a string in the db
            values.put(PluginEntry.COLUMN_NAME_TARGET_PACKAGES, String.join(",",plugin.targetPackages));

            // Put target apis as a string in the db
            values.put(PluginEntry.COLUMN_NAME_TARGET_APIS, String.join(",",plugin.targetAPIs));

            values.put(PluginEntry.COLUMN_NAME_IS_ACTIVE, plugin.isActive?1:0);

            // Insert the new row, returning the primary key value of the new row
            long newRowId = db.insert(PluginEntry.TABLE_NAME, null, values);

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

            // Prepare selection criteria to select the plugin row
            String selection = PluginEntry._ID + " = ? ";
            String[] selectionArgs = new String[]{String.valueOf(plugin.id)};

            // Delete the row identified by the plugin id
            int deletedRows = db.delete(PluginEntry.TABLE_NAME, selection, selectionArgs);

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
     */
    public int updatePlugin(PermissionsPlugin plugin){

        if(null == plugin || -1 == plugin.id){
            Log.e(PermissionsPluginOptions.TAG,"Failed to update plugin due to invalid plugin");
            return 0;
        }

        try{
            // Gets the data repository in write mode
            SQLiteDatabase db = mPluginDbHelper.getWritableDatabase();

            // Create a new map of values, where column names are the keys
            ContentValues values = new ContentValues();

            values.put(PluginEntry.COLUMN_NAME_PACKAGE_NAME, plugin.packageName);

            // Put supported packages as a string in the db
            values.put(PluginEntry.COLUMN_NAME_SUPPORTED_PACKAGES, String.join(",",plugin.supportedPackages));

            // Put supported APIs as a string in the db
            values.put(PluginEntry.COLUMN_NAME_SUPPORTED_APIS, String.join(",",plugin.supportedAPIs));

            // Put target packages as a string in the db
            values.put(PluginEntry.COLUMN_NAME_TARGET_PACKAGES, String.join(",",plugin.targetPackages));

            // Put target apis as a string in the db
            values.put(PluginEntry.COLUMN_NAME_TARGET_APIS, String.join(",",plugin.targetAPIs));

            values.put(PluginEntry.COLUMN_NAME_IS_ACTIVE, plugin.isActive?1:0);

            // Prepare selection criteria to select the plugin row
            String selection = PluginEntry._ID + " = ? ";
            String[] selectionArgs = new String[]{String.valueOf(plugin.id)};

            // Update the row identified by the plugin id
            int updatedRows = db.update(PluginEntry.TABLE_NAME, values, selection, selectionArgs);

            return updatedRows;

        }catch(SQLiteException e){
            Log.e(PermissionsPluginOptions.TAG,"Failed to open plugin db " + DATABASE_NAME + ". SQLiteException: "+ e);
            return 0;
        }

    }

}