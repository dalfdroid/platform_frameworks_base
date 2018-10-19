package android.os;

/**
 * A record of an object written to a {#link Parcel}.
 *
 * @hide
 */
public class ParcelObject {

    /** The object written to the parcel. */
    public final Object mObject;

    /** The starting position of the object in the parcel. */
    public final int mStartPos;

    /** The type of the parcel object. */
    public final int mObjectType;

    /** The ending position of the object in the parcel. */
    public int mEndPos;

    public ParcelObject(Object object, int startPos, int type) {
        mObject = object;
        mStartPos = startPos;
        mObjectType = type;
    }

    public void setEndPos(int endPos) {
        mEndPos = endPos;
    }

    @Override
    public String toString() {
        return "[ParcelObject] mObject: " + mObject
            + ", mObjectType: " + mObjectType
            + ", mStartPos: " + mStartPos
            + ", mEndPos: " + mEndPos;
    }

    public static final int BINDER_OBJECT = 1;
    public static final int PERTURBABLE_OBJECT = 2;
}
