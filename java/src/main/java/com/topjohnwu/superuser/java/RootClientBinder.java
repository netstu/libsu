package com.topjohnwu.superuser.java;

import android.content.Intent;
import android.os.Binder;
import android.os.DeadObjectException;
import android.os.IInterface;
import android.os.Parcel;
import android.os.RemoteException;

import com.topjohnwu.superuser.Shell;
import com.topjohnwu.superuser.ShellUtils;

import java.io.IOException;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Client side Binder (non-root)
 */
class RootClientBinder extends Binder {

    private boolean unBound = false;
    private Intent intent;

    RootClientBinder(Intent i) {
        intent = i;
    }

    @Override
    protected boolean onTransact(int code, @NonNull Parcel data, @Nullable Parcel reply,
                                 int flags) throws RemoteException {
        if (unBound)
            throw new DeadObjectException();

        try (Sockets.Handle handle = Sockets.clientGetSocket()) {
            // Clear up potential garbage inputs
            ShellUtils.cleanInputStream(handle.socketIn);

            // Notify server we are going to start an IPC session
            RootIPC.startIPC(handle.hashCode(), intent.getComponent().getClassName());

            // Write code
            handle.socketOut.writeInt(code);

            // Write data
            byte[] rawData = data.marshall();
            handle.socketOut.writeInt(rawData.length);
            handle.socketOut.write(rawData);
            handle.socketOut.flush();

            // Read reply
            if (reply != null) {
                int replySz = handle.socketIn.readInt();
                byte[] rawReply = new byte[replySz];
                handle.socketIn.readFully(rawReply);
                reply.unmarshall(rawReply, 0, replySz);
            } else {
                ShellUtils.cleanInputStream(handle.socketIn);
            }
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

    Intent getIntent() {
        return intent;
    }
}
