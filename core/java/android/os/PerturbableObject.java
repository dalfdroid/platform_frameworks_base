package android.os;

/**
 * A record of an object written to a Parcel that needs to be perturbed.
 *
 * @hide
 */
public class PerturbableObject {
    /** {@hide} */ public Perturbable type;
    /** {@hide} */ public Parcelable object;
    /** {@hide} */ public int writeFlags;
    /** {@hide} */ public int parcelStartPos;
    /** {@hide} */ public int parcelEndPos;
}
