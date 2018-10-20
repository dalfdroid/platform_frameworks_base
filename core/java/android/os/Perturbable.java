package android.os;

import android.net.Uri;

/**
 * An enum of perturbable object types.
 *
 * {@hide}
 */
public enum Perturbable {
    /** Instances of the {@link android.location.Location} class. */
    LOCATION,

    /** Instances of the {@link android.database.CursorWindow} class. */
    CONTACTS;

    public static Perturbable getReturnTypeFor(Uri url) {
        if (url != null) {
            if (url.toString().contains("contacts")) {
                return Perturbable.CONTACTS;
            }
        }

        return null;
    }
}
