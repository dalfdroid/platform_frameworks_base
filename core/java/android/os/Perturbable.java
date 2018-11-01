package android.os;

import android.net.Uri;

import android.provider.CalendarContract;

/**
 * An enum of perturbable object types.
 *
 * {@hide}
 */
public enum Perturbable {
    /** Instances of the {@link android.location.Location} class. */
    LOCATION,

    /** Instances of the {@link android.database.CursorWindow} class. */
    CONTACTS,

    /** Instances of the {@link android.database.CursorWindow} class. */
    CALENDAR;

    public static Perturbable getReturnTypeFor(Uri url) {
        if (url != null) {

            String authority = url.getAuthority();

            if (url.toString().contains("contacts")) {
                return Perturbable.CONTACTS;
            }

            if(authority.equals(CalendarContract.AUTHORITY)){
                return Perturbable.CALENDAR;
            }
        }

        return null;
    }
}
