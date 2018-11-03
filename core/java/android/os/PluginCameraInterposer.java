package android.os;

import android.hardware.CameraStreamInfo;
import android.util.ArrayMap;
import android.util.Log;
import android.view.InterposableSurface;
import android.view.Surface;

import com.android.permissionsplugin.PermissionsPluginOptions;

import java.util.Objects;

/**
 * @hide
 */
public abstract class PluginCameraInterposer extends Binder
        implements IPluginCameraInterposer {

    private ArrayMap<StreamRecord, InterposableSurface>
        mStreams;

    public PluginCameraInterposer() {
        attachInterface(this, descriptor);
    }

    private final synchronized Surface reportCameraStream(String packageName,
            CameraStreamInfo streamInfo) {

        if (mStreams == null) {
            mStreams = new ArrayMap<>();
        }

        int streamId = streamInfo.getStreamId();

        StreamRecord record = new StreamRecord(streamId, packageName);
        InterposableSurface ipSurface = mStreams.get(record);
        if (ipSurface != null) {
            Log.d(PermissionsPluginOptions.TAG, "Unexpectedly receiving a camera stream report"
                  + " packageName: " + packageName
                  + ", stream info: " + streamInfo
                  + ". Returning previous interposable surface.");
            return ipSurface.getNewSurface();
        }

        int width = streamInfo.getWidth();
        int height = streamInfo.getHeight();
        int format = streamInfo.getFormat();

        if (!this.shouldInterpose(packageName, streamId, width, height, format)) {
            if (PermissionsPluginOptions.DEBUG) {
                Log.d(PermissionsPluginOptions.TAG, "Plugin WON'T interpose camera stream  "
                      + " created for package: " + packageName
                      + ", stream id: " + streamId
                      + ", width: " + width
                      + ", height: " + height
                      + ", format: " + format
                      + ", surface; " + streamInfo.getSurface());
            }

            return null;
        }

        ipSurface = new InterposableSurface(packageName, this, streamId, width,
            height, format, streamInfo.getSurface());

        if (!ipSurface.isInitialized()) {
            if (PermissionsPluginOptions.DEBUG) {
                Log.d(PermissionsPluginOptions.TAG, "Plugin coudl not interpose on camera stream"
                      + " created for package: " + packageName
                      + ", stream id: " + streamId
                      + ", width: " + width
                      + ", height: " + height
                      + ", format: " + format
                      + ", surface: " + streamInfo.getSurface());
            }

            this.couldNotInterpose(packageName, streamId);
            return null;
        }

        if (PermissionsPluginOptions.DEBUG) {
            Log.d(PermissionsPluginOptions.TAG, "Plugin will interpose on camera stream"
                  + " created for package: " + packageName
                  + ", stream id: " + streamId
                  + ", width: " + width
                  + ", height: " + height
                  + ", format: " + format
                  + ", surface: " + streamInfo.getSurface());
        }

        mStreams.put(record, ipSurface);
        return ipSurface.getNewSurface();
    }

    private final synchronized void reportSurfaceDisconnection(String packageName,
            CameraStreamInfo streamInfo) {

        if (mStreams == null) {
            return;
        }

        int streamId = streamInfo.getStreamId();

        StreamRecord record = new StreamRecord(streamId, packageName);
        InterposableSurface ipSurface = mStreams.get(record);
        if (ipSurface == null) {
            Log.d(PermissionsPluginOptions.TAG, "Unexpectedly receiving a surface disconnection report"
                  + " packageName: " + packageName
                  + ", stream info: " + streamInfo
                  + ". Doing nothing.");
            return;
        }

        this.streamDisconnecting(packageName, streamId);
        if (PermissionsPluginOptions.DEBUG) {
            Log.d(PermissionsPluginOptions.TAG, "Going to close interposable surface"
                  + " created for package: " + packageName
                  + ", stream id: " + streamId);
        }

        ipSurface.close();
        mStreams.remove(record);
    }

    private static final class StreamRecord {
        private final int mStreamId;
        private final String mPackageName;
        private StreamRecord(int streamId, String packageName) {
            mStreamId = streamId;
            mPackageName = packageName;
        }

        @Override
        public boolean equals(Object o) {
            // Auto-generated using IntelliJ Idea
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            StreamRecord that = (StreamRecord) o;
            return mStreamId == that.mStreamId &&
                Objects.equals(mPackageName, that.mPackageName);
        }

        @Override
        public int hashCode() {
            // Auto-generated using IntelliJ Idea
            return Objects.hash(mStreamId, mPackageName);
        }
    }

    /**
     * Cast an IBinder object into an PluginCameraInterposerProxy object.
     */
    public static PluginCameraInterposerProxy asInterface(android.os.IBinder obj) {
        if (obj == null) {
            return null;
        }

        IInterface iin = obj.queryLocalInterface(descriptor);
        return new PluginCameraInterposerProxy(obj);
    }

    @Override
    public IBinder asBinder()
    {
        return this;
    }

    @Override
    public boolean onTransact(int code, Parcel data, Parcel reply, int flags)
            throws RemoteException {

        switch (code) {
        case TRANSACTION_reportCameraStream:
            {
                data.enforceInterface(descriptor);

                String packageName = data.readString();
                CameraStreamInfo cameraStreamInfo = CameraStreamInfo.CREATOR.
                    createFromParcel(data);

                Surface _result = this.reportCameraStream(packageName, cameraStreamInfo);
                reply.writeNoException();

                if (_result != null) {
                    reply.writeInt(1);
                    _result.writeToParcel(reply, flags);
                } else {
                    reply.writeInt(0);
                }

                return true;
            }

        case TRANSACTION_reportSurfaceDisconnection:
            {
                data.enforceInterface(descriptor);

                String packageName = data.readString();
                CameraStreamInfo cameraStreamInfo = CameraStreamInfo.CREATOR.
                    createFromParcel(data);

                this.reportSurfaceDisconnection(packageName, cameraStreamInfo);
                reply.writeNoException();
                return true;
            }
        }

        return super.onTransact(code, data, reply, flags);
    }

    static final int TRANSACTION_reportCameraStream = (TRANSACTION_FIRST_HIDDEN + 0);
    static final int TRANSACTION_reportSurfaceDisconnection = (TRANSACTION_FIRST_HIDDEN + 1);
}

final class PluginCameraInterposerProxy implements IPluginCameraInterposer
{
    private IBinder mRemote;

    public PluginCameraInterposerProxy(IBinder remote)
    {
        mRemote = remote;
    }

    @Override
    public IBinder asBinder()
    {
        return mRemote;
    }

    public String getInterfaceDescriptor()
    {
        return descriptor;
    }

    @Override
    public boolean shouldInterpose(String packageName, int streamId, int width, int height, int format) {
        String errorMsg = "shouldInterpose is not meant to be called directly by the proxy!";
        throw new UnsupportedOperationException(errorMsg);
    }

    @Override
    public void streamDisconnecting(String packageName, int streamId) {
        String errorMsg = "streamDisconnecting is not meant to be called directly by the proxy!";
        throw new UnsupportedOperationException(errorMsg);
    }

    @Override
    public void couldNotInterpose(String packageName, int streamId) {
        String errorMsg = "couldNotInterpose is not meant to be called directly by the proxy!";
        throw new UnsupportedOperationException(errorMsg);
    }

    @Override
    public void onFrameAvailable(String packageName, int streamId, int width,
            int height, int stride, int format, long framePtr) {
        String errorMsg = "onFrameAvailable is not meant to be called directly by the proxy!";
        throw new UnsupportedOperationException(errorMsg);
    }

    public Surface reportCameraStream(String packageName, CameraStreamInfo cameraStreamInfo)
            throws RemoteException{
        Parcel _data = Parcel.obtain();
        Parcel _reply = Parcel.obtain();
        Surface _result = null;

        int transactionId = PluginCameraInterposer.TRANSACTION_reportCameraStream;
        try {
            _data.writeInterfaceToken(descriptor);
            _data.writeString(packageName);
            cameraStreamInfo.writeToParcel(_data, 0);
            mRemote.transact(transactionId, _data, _reply, 0);
            _reply.readException();

            int hasResult = _reply.readInt();
            if (hasResult != 0) {
                _result = new Surface();
                _result.readFromParcel(_reply);
            }
        }
        finally {
            _reply.recycle();
            _data.recycle();
        }

        return _result;
    }

    public void reportSurfaceDisconnection(String packageName,
            CameraStreamInfo cameraStreamInfo) throws RemoteException {
        Parcel _data = Parcel.obtain();
        Parcel _reply = Parcel.obtain();

        int transactionId = PluginCameraInterposer.TRANSACTION_reportSurfaceDisconnection;
        try {
            _data.writeInterfaceToken(descriptor);
            _data.writeString(packageName);
            cameraStreamInfo.writeToParcel(_data, 0);
            mRemote.transact(transactionId, _data, _reply, 0);
            _reply.readException();
        }
        finally {
            _reply.recycle();
            _data.recycle();
        }
    }
}
