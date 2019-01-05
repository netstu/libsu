package com.topjohnwu.superuser.java;

import android.net.LocalSocket;
import android.os.IBinder;
import android.os.Parcel;
import android.util.SparseArray;

import com.topjohnwu.superuser.Shell;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.concurrent.Future;

public abstract class RootService {

    private class Pair {
        Pair(Future future, LocalSocket socket) {
            this.future = future;
            this.socket = socket;
        }
        Future future;
        LocalSocket socket;
    }

    private SparseArray<Pair> bindSessions;

    public RootService() {
        bindSessions = new SparseArray<>();
        onCreate();
    }

    public void onCreate() {}

    public abstract IBinder onBind();

    public boolean onUnbind() {
        return false;
    }

    public void onDestroy () {}

    void bindSession(int session) {
        LocalSocket socket = new LocalSocket();
        Future future = Shell.EXECUTOR.submit(() -> {
            socket.connect(EntryPoint.masterAddress);
            DataInputStream dis = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
            DataOutputStream dos = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
            IBinder binder = onBind();
            byte[] rawData = new byte[4096];
            while (true) {
                Parcel data = Parcel.obtain();
                Parcel reply = Parcel.obtain();
                // Read code
                int code = dis.readInt();
                try {
                    // Read data
                    int dataSz = dis.readInt();
                    if (dataSz > rawData.length)
                        rawData = new byte[(dataSz / 4096 + 1) * 4096];
                    dis.readFully(rawData, 0, dataSz);
                    data.unmarshall(rawData, 0, dataSz);

                    // Actual invocation
                    binder.transact(code, data, reply, 0);

                    // Write reply
                    byte[] rawReply = reply.marshall();
                    dos.writeInt(rawReply.length);
                    dos.write(rawReply);
                    dos.flush();
                } finally {
                    data.recycle();
                    reply.recycle();
                }
            }
        });
        bindSessions.put(session, new Pair(future, socket));
    }

    void unBindSession(int session) {
        Pair pair = bindSessions.get(session);
        if (pair != null) {
            pair.future.cancel(true);
            try {
                pair.socket.close();
            } catch (IOException ignored) {}
            bindSessions.remove(session);
        }
        if (bindSessions.size() == 0)
            EntryPoint.serviceMap.remove(this.getClass());
    }

}

