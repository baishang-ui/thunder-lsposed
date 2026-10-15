package io.github.baishangui.thunder;

import android.os.Bundle;
import android.os.IBinder;
import android.os.Parcel;
import android.os.RemoteException;

/** One handle transfer per animation; matrices never travel over Binder. */
final class StatusBarOverlayIpc {
    static final String DESCRIPTOR = "io.github.baishangui.thunder.statusbar.overlay.v1";
    static final int CONNECT = IBinder.FIRST_CALL_TRANSACTION;
    static final int ACQUIRE = CONNECT + 1;
    static final int COMMITTED = CONNECT + 2;
    static final int REJECT = CONNECT + 3;
    static Bundle call(IBinder endpoint, int code, IBinder client, int task, long generation) throws RemoteException {
        Parcel data = Parcel.obtain(), reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(DESCRIPTOR);
            data.writeStrongBinder(client); data.writeInt(task); data.writeLong(generation);
            if (!endpoint.transact(code, data, reply, 0)) throw new RemoteException("overlay transaction unsupported");
            reply.readException();
            return reply.readBundle(StatusBarOverlayIpc.class.getClassLoader());
        } finally { reply.recycle(); data.recycle(); }
    }
    private StatusBarOverlayIpc() { }
}
