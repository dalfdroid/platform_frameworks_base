package android.view;

import android.os.IPluginCameraInterposer;
import android.util.Log;

import com.android.permissionsplugin.PermissionsPluginOptions;

/**
 * This is a special surface object that allows us to modify camera frames
 * rendered to a new surface target before they are are delivered to the app.
 *
 * @hide
 */
public class InterposableSurface implements AutoCloseable {

    /**
     * Indicates if this interposable surface is valid for use.
     */
    private boolean mInitialized;

    private final String mPackageName;
    private final IPluginCameraInterposer mInterposer;

    private final int mStreamId;
    private final int mWidth;
    private final int mHeight;
    private final int mFormat;

    /**
     * The new rendering target that a camera stream should render into.
     */
    private final Surface mSourceSurface;

    /**
     * The surface where frames should rendered after they have been modified.
     */
    private final Surface mDestinationSurface;

    /**
     * These fields are used by native code, do not access or modify.
     */
    private long mNativeContext;

    /**
     * Creates a new instance of the interposable surface. See also {@link
     * #isInitialized}.
     *
     * @param packageName The package name of the app that is being interposed
     * on.
     * @param interposer The camera interposer in charge of this surface.
     * @param streamId The id of the camera stream for which this interposable
     * surface is being created.
     * @param width The width of the camera frames that will be rendered.
     * @param height The height of the camera frames that will be rendered.
     * @param format The format of the camera frames that will be rendered.
     * @param destinationSurface The surface object where the frames should
     * finally be rendered after they have been modified.
     */
    public InterposableSurface(String packageName, IPluginCameraInterposer interposer,
            int streamId, int width, int height, int format,
            Surface destinationSurface) {
        mPackageName = packageName;
        mInterposer = interposer;
        mStreamId = streamId;
        mWidth = width;
        mHeight = height;
        mFormat = format;
        mDestinationSurface = destinationSurface;

        mInitialized = nativeInit(mStreamId, mDestinationSurface);
        if (mInitialized) {
            mSourceSurface = nativeGetSourceSurface();
        } else {
            mSourceSurface = null;
        }
    }

    /**
     * When an instance of this object is created, some internal routines run to
     * initialize this object. If the initialization fails, then this will
     * return false and true otherwise. An interposable surfaces should not be
     * counted on for use when this method returns true.
     *
     * @return true if this interposable surface was successfully initialized,
     * and false otherwise.
     */
    public boolean isInitialized() {
        return mInitialized;
    }

    /**
     * Returns the new surface target that the camera stream should render into
     * so that camera frames may be interposed on before they are delivered to
     * the app.
     */
    public Surface getNewSurface() {
        return mSourceSurface;
    }

    /**
     * Releases all resources created by this interposable surface.
     */
    @Override
    public void close() {
        nativeClose();
        mInitialized = false;
    }

    private void onFrameAvailable(int stride, long nativePtr) {
        mInterposer.onFrameAvailable(mPackageName, mStreamId, mWidth, mHeight,
            stride, mFormat, nativePtr);
    }

    private synchronized native boolean nativeInit(int streamId, Surface mTargetSurface);
    private synchronized native Surface nativeGetSourceSurface();
    private synchronized native void nativeClose();
}
