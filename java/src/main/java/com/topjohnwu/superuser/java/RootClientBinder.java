package com.topjohnwu.superuser.java;

import android.os.Binder;
import android.os.DeadObjectException;
import android.os.IInterface;
import android.os.Parcel;
import android.os.RemoteException;

import com.topjohnwu.superuser.Shell;

import java.io.IOException;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Client side Binder (non-root)
 */
class RootClientBinder extends Binder {

    private Class<? extends RootService> cls;

    private byte[] rawReply;
    private boolean unBound = false;

    RootClientBinder(Class<? extends RootService> cls) {
        rawReply = new byte[4096];
        this.cls = cls;
    }

    @Override
    protected boolean onTransact(int code, @NonNull Parcel data, @Nullable Parcel reply,
                                 int flags) throws RemoteException {
        if (unBound)
            throw new DeadObjectException();

        try (Sockets.Handle handle = Sockets.clientGetSocket()) {
            // Notify server we are going to start a new IPC session
            RootIPC.startIPC(handle.hashCode(), cls);

            // Write code
            handle.socketOut.writeInt(code);

            // Write data
            byte[] rawData = data.marshall();
            handle.socketOut.writeInt(rawData.length);
            handle.socketOut.write(rawData);
            handle.socketOut.flush();

            // Read reply
            int replySz = handle.socketIn.readInt();
            if (replySz < 0)
                return false;
            if (rawReply.length < replySz)
                rawReply = new byte[(replySz / 4096 + 1) * 4096];
            handle.socketIn.readFully(rawReply, 0, replySz);
            if (reply != null)
                reply.unmarshall(rawReply, 0, replySz);
        } catch (IOException e) {
            Shell.EXECUTOR.submit(RootIPC::serverError);
            throw new DeadObjectException();
        }
        return true;
    }

    @Nullable
    @Override
    public IInterface queryLocalInterface(@NonNull String descriptor) {
        return null;
    }

    void unBind() {
        unBound = true;
    }

    Class<? extends RootService> getBoundService() {
        return cls;
    }
}
