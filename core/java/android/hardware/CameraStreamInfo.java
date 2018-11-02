package android.hardware;

import android.os.Parcel;
import android.os.Parcelable;
import android.view.Surface;

/**
 * This class represents information about a camera output stream created by
 * libcameraservice.
 *
 * @hide
 */
public final class CameraStreamInfo implements Parcelable {

    private int mStreamId;
    private int mWidth;
    private int mHeight;
    private int mFormat;
    private Surface mSurface;

    /**
     * Returns the camera stream id.
     * @return the camera stream id.
     */
    public int getStreamId() {
        return mStreamId;
    }

    /**
     * Returns the width of the camera stream.
     * @return the width of the camera stream.
     */
    public int getWidth() {
        return mWidth;
    }

    /**
     * Returns the height of the camera stream.
     * @return the height of the camera stream.
     */
    public int getHeight() {
        return mHeight;
    }

    /**
     * Returns the format of the camera stream.
     * @return the format of the camera stream.
     */
    public int getFormat() {
        return mFormat;
    }

    /**
     * Returns the target surface of the camera stream. This surface would have
     * been originally created by the app that used the camera.
     * @return the target surface of the camera stream.
     */
    public Surface getSurface() {
        return mSurface;
    }

    public static final Parcelable.Creator<CameraStreamInfo> CREATOR =
            new Parcelable.Creator<CameraStreamInfo>() {
        @Override
        public CameraStreamInfo createFromParcel(Parcel in) {
            CameraStreamInfo streamInfo = new CameraStreamInfo();
            streamInfo.readFromParcel(in);

            return streamInfo;
        }

        @Override
        public CameraStreamInfo[] newArray(int size) {
            return new CameraStreamInfo[size];
        }
    };

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mStreamId);
        dest.writeInt(mWidth);
        dest.writeInt(mHeight);
        dest.writeInt(mFormat);
        mSurface.writeToParcel(dest, flags);
    }

    private void readFromParcel(Parcel in) {
        mStreamId = in.readInt();
        mWidth = in.readInt();
        mHeight = in.readInt();
        mFormat = in.readInt();
        mSurface = new Surface();
        mSurface.readFromParcel(in);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public String toString() {
        return "[[CameraStreamInfo]"
            + " mStreamId = " + mStreamId
            + ", mWidth = " + mWidth
            + ", mHeight = " + mHeight
            + ", mFormat = " + mFormat
            + ", mSurface = " + mSurface
            + "]";
    }
}
