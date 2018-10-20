package android.os;

import android.net.Uri;

/**
 * A record of an object written to a Parcel that needs to be perturbed.
 *
 * @hide
 */
public class PerturbableObject extends ParcelObject {

    /** The type of perturbable data. */
    public final Perturbable mPerturbableType;

    /** The parcelable object written to the parcel. */
    public final Parcelable mParcelable;

    /** The write flags used when writing this object to the parcel. */
    public final int mWriteFlags;

    /** The perturbed parcel. */
    public Parcelable mPerturbed;

    /** Metadata about the original parcelable object. */
    public Object mMetadata;

    public PerturbableObject(Perturbable type, Parcelable object, int startPos,
            int writeFlags) {
        this(type, object, startPos, writeFlags, /** metadata */ null);
    }

    public PerturbableObject(Perturbable type, Parcelable object, int startPos,
            int writeFlags, Object metadata) {
        super(object, startPos,ParcelObject.PERTURBABLE_OBJECT);
        mPerturbableType = type;
        mParcelable = object;
        mWriteFlags = writeFlags;
        mMetadata = metadata;
    }

    public void setPerturbedObject(Parcelable perturbed) {
        mPerturbed = perturbed;
    }

    /**
     * Returns the latest parcelable object in this record. This will be the
     * perturbed object, if one is available. Otherwise, it will be the original
     * parcelable object.
     *
     * @return The latest parcelable object available.
     */
    public Parcelable getLatestParcelable() {
        return (mPerturbed != null ? mPerturbed : mParcelable);
    }

    @Override
    public String toString() {
        return "[PerturbableObject] mParcelable: " + mParcelable
            + ", mPerturbableType: " + mPerturbableType
            + ", mPerturbed: " + mPerturbed
            + ", mStartPos: " + mStartPos
            + ", mEndPos: " + mEndPos;
    }

    /** {@hide} */
    public static class QueryMetadata {
        public Uri url;
        public String[] projection;
        public Bundle queryArgs;
        public String[] columnNames;
        public int count;
    }
}
