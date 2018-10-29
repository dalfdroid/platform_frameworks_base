package android.os;

import android.util.Log;

import com.android.permissionsplugin.PermissionsPluginOptions;

/**
 * The main abstract class that every plugin service must extend.
 *
 * {@hide}
 */
public abstract class PluginService extends Binder implements IPluginService
{
    public PluginService()
    {
        attachInterface(this, descriptor);
    }

    /**
     * Cast an IBinder object into an IPluginService interface,
     * generating a proxy if needed.
     */
    public static IPluginService asInterface(android.os.IBinder obj)
    {
        if (obj == null) {
            return null;
        }

        IInterface iin = obj.queryLocalInterface(descriptor);

        if (((iin != null) && (iin instanceof IPluginService))) {
            return (IPluginService) iin;
        }

        return new PluginServiceProxy(obj);
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

        case TRANSACTION_getLocationInterposer:
            {
                data.enforceInterface(descriptor);
                IPluginLocationInterposer _result = this.getLocationInterposer();
                reply.writeNoException();

                IBinder val = null;
                if (_result != null) {
                    val = _result.asBinder();
                }
                reply.writeStrongBinder(val);

                return true;
            }

        case TRANSACTION_getContactsInterposer:
            {
                data.enforceInterface(descriptor);
                IPluginContactsInterposer _result = this.getContactsInterposer();
                reply.writeNoException();

                IBinder val = null;
                if (_result != null) {
                    val = _result.asBinder();
                }
                reply.writeStrongBinder(val);

                return true;
            }

        case TRANSACTION_getCalendarInterposer:
            {
                data.enforceInterface(descriptor);
                IPluginCalendarInterposer _result = this.getCalendarInterposer();
                reply.writeNoException();

                IBinder val = null;
                if (_result != null) {
                    val = _result.asBinder();
                }
                reply.writeStrongBinder(val);

                return true;
            }

        }

        return super.onTransact(code, data, reply, flags);
    }
}

final class PluginServiceProxy implements IPluginService
{
    private IBinder mRemote;

    public PluginServiceProxy(IBinder remote)
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
    public IPluginLocationInterposer getLocationInterposer() throws RemoteException
    {
        String errorMsg = "Use getLocationInterposerRaw() instead!";
        Log.d(PermissionsPluginOptions.TAG, errorMsg);
        throw new RemoteException(errorMsg);
    }

    @Override
    public IPluginContactsInterposer getContactsInterposer() throws RemoteException
    {
        String errorMsg = "Use getContactsInterposerRaw() instead!";
        Log.d(PermissionsPluginOptions.TAG, errorMsg);
        throw new RemoteException(errorMsg);
    }

    @Override
    public IPluginCalendarInterposer getCalendarInterposer() throws RemoteException
    {
        String errorMsg = "Use getCalendarInterposerRaw() instead!";
        Log.d(PermissionsPluginOptions.TAG, errorMsg);
        throw new RemoteException(errorMsg);
    }


    /**
     * Returns the raw IBinder of the location interposer of this
     * plugin. Transform it into the interface version manually.
     */
    public IBinder getLocationInterposerRaw() throws RemoteException
    {
        Parcel _data = Parcel.obtain();
        Parcel _reply = Parcel.obtain();
        IBinder _result;

        try {
            _data.writeInterfaceToken(descriptor);
            mRemote.transact(TRANSACTION_getLocationInterposer, _data, _reply, 0);
            _reply.readException();
            _result = _reply.readStrongBinder();
        }
        finally {
            _reply.recycle();
            _data.recycle();
        }
        return _result;
    }

    /**
     * Returns the raw IBinder of the contacts interposer of this
     * plugin. Transform it into the interface version manually!
     */
    public IBinder getContactsInterposerRaw() throws RemoteException {
        Parcel _data = Parcel.obtain();
        Parcel _reply = Parcel.obtain();
        IBinder _result;

        try {
            _data.writeInterfaceToken(descriptor);
            mRemote.transact(TRANSACTION_getContactsInterposer, _data, _reply, 0);
            _reply.readException();

            _result = _reply.readStrongBinder();
        }
        finally {
            _reply.recycle();
            _data.recycle();
        }
        return _result;
    }

    /**
     * Returns the raw IBinder of the calendar interposer of this
     * plugin. Transform it into the interface version manually!
     */
    public IBinder getCalendarInterposerRaw() throws RemoteException {
        Parcel _data = Parcel.obtain();
        Parcel _reply = Parcel.obtain();
        IBinder _result;

        try {
            _data.writeInterfaceToken(descriptor);
            mRemote.transact(TRANSACTION_getCalendarInterposer, _data, _reply, 0);
            _reply.readException();

            _result = _reply.readStrongBinder();
        }
        finally {
            _reply.recycle();
            _data.recycle();
        }
        return _result;
    }

}
