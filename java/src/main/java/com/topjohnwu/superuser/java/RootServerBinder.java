package com.topjohnwu.superuser.java;

import android.os.IBinder;
import android.os.Parcel;

import com.topjohnwu.superuser.Shell;

class RootServerBinder {

    private IBinder binder;
    private byte[] rawData;

    RootServerBinder(IBinder b) {
        binder = b;
        rawData = new byte[4096];
    }

    void transact(int sockHash) {
        Sockets.Handle handle = Sockets.serverGetSocket(sockHash);
        if (handle == null)
            return;
        Shell.EXECUTOR.submit(() -> {
            // Read code
            int code = handle.socketIn.readInt();

            Parcel data = Parcel.obtain();
            Parcel reply = Parcel.obtain();
            try {
                // Read data
                int dataSz = handle.socketIn.readInt();
                if (dataSz > rawData.length)
                    rawData = new byte[(dataSz / 4096 + 1) * 4096];
                handle.socketIn.readFully(rawData, 0, dataSz);
                data.unmarshall(rawData, 0, dataSz);

                // Actual invocation
                if (!binder.transact(code, data, reply, 0)) {
                    handle.socketOut.writeInt(-1);
                    return null;
                }

                // Write reply
                byte[] rawReply = reply.marshall();
                handle.socketOut.writeInt(rawReply.length);
                handle.socketOut.write(rawReply);
                handle.socketOut.flush();
            } finally {
                data.recycle();
                reply.recycle();
            }
            return null;
        });
    }
}
