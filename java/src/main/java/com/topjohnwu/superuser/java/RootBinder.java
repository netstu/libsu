package com.topjohnwu.superuser.java;

import android.net.LocalSocket;
import android.os.Binder;
import android.os.IInterface;
import android.os.Parcel;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Client side Binder (non-root)
 */
class RootBinder extends Binder {

    private Class<? extends RootService> cls;

    private static DataOutputStream socketOut;
    private static DataInputStream socketIn;

    private byte[] rawReply;

    RootBinder(Class<? extends RootService> cls) throws IOException {
        LocalSocket socket = RootIPC.serverSocket.accept();
        socketOut = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
        socketIn = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
        rawReply = new byte[4096];
        this.cls = cls;
    }

    @Override
    protected boolean onTransact(int code, @NonNull Parcel data, @Nullable Parcel reply, int flags) {
        try {
            // Write code
            socketOut.writeInt(code);

            // Write data
            byte[] rawData = data.marshall();
            socketOut.writeInt(rawData.length);
            socketOut.write(rawData);
            socketOut.flush();

            // Read reply
            int replySz = socketIn.readInt();
            if (rawReply.length < replySz)
                rawReply = new byte[(replySz / 4096 + 1) * 4096];
            socketIn.readFully(rawReply, 0, replySz);
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
        try {
            socketOut.close();
        } catch (IOException ignored) {}
    }

    Class<? extends RootService> getBoundService() {
        return cls;
    }
}
