package android.os;


/**
 * A representation of the abstract class that a camera interposer in the plugin
 * should extend. The camera interposer should not simply implement the
 * IPluginCameraInterposer interface. It *MUST* extend this class.
 */
public abstract class PluginCameraInterposer extends Binder
        implements IPluginCameraInterposer {

    // Note to Dalf developers: unlike the other classes that are symlinked into
    // the helper library directory, so that we can build the plugin library,
    // this one is a dummy copy of the class with some binder methods marked as
    // final. This is because the actual PluginCameraInterposer class depends on
    // a lot of hidden APIs and classes, and we do not want to expose any of
    // them to the plugin.
    //
    // During runtime, the plugin will load the actual PluginCameraInterposer
    // and not this dummy class because the plugin should have only compiled
    // against the helper library (using gradle's compileOnly feature). If the
    // plugin uses "implementation" instead of "compileOnly" when linking
    // against the helper library, then it will load this dummy class and
    // nothing will work.
    //
    // On a final note, methods that are given a dummy implementation means that
    // plugin developer does not have to provide an implementation of those
    // methods. This is a convenience for them.

    @Override
    public final IBinder asBinder() {
        return null;
    }
}
