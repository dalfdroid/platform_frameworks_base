package android.os;


/**
 * The external storage interposer component in a plugin must implement this interface.
 *
 * {@hide}
 */
interface IPluginStorageInterposer {

    /**
     * This will be called when an app tries to load a file in the external
     * storage partition. The plugin can perform one of three things: (i)
     * prevent the file from being opened by returning a null or empty string,
     * (ii) allow access by returning the same filepath without any
     * modifications, or (iii) choose the file that should be opened instead by
     * providing a new filepath.
     *
     * Note that this method is called when an app *attempts* to load a file in
     * the external storage. Even if the plugin allows access, the attempt may
     * ultimately fail because the app may not have the permissions required to
     * access the external storage or the file. The ability to let a plugin know
     * if the attempt succeeded or not has not been implemented yet.
     *
     * @param The package name of the app that is trying to load a file.
     * @param The path to the file that will be opened.
     *
     * @return The path that should be opened instead. To prevent the file from
     * being opened, return null or the empty string; to allow the file to be
     * opened, return the same filepath without any modifications; to open a
     * different file instead, return a new filepath.
     */
    String beforeFileOpen(String targetAppPkg, String filepath);
}
