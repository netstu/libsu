package com.topjohnwu.superuser.java;

import android.os.Binder;
import android.os.IInterface;
import android.os.Parcel;

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
    protected boolean onTransact(int code, @NonNull Parcel data, @Nullable Parcel reply, int flags) {
        if (unBound)
            return false;

        try (Sockets.Handle handle = Sockets.clientGetSocket()) {
            if (handle == null)
                return false;

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
            if (rawReply.length < replySz)
                rawReply = new byte[(replySz / 4096 + 1) * 4096];
            handle.socketIn.readFully(rawReply, 0, replySz);
            if (reply != null)
                reply.unmarshall(rawReply, 0, replySz);
        } catch (IOException e) {
            return false;
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
