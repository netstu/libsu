package com.topjohnwu.superuser.java;

import android.content.Intent;
import android.net.LocalSocket;
import android.os.Parcel;

import com.topjohnwu.superuser.Shell;
import com.topjohnwu.superuser.internal.InternalUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

public class EntryPoint {

    private static Map<Class, RootService> serviceMap = new HashMap<>();

    public static void main(String[] args) {
        Shell.Config.verboseLogging(true);
        if (args.length < 1)
            System.exit(1);

        /* Close STDOUT/STDERR since it belongs to the parent shell
         * All communication goes through the master socket */
        System.out.close();
        System.err.close();

        new EntryPoint().run(args);
    }

    private EntryPoint() {}

    /**
     * This function handles requests from the client.
     * Requests are formatted in "action|arg1|arg2...", separated with newline
     */
    private void run(String[] args) {
        try {
            LocalSocket master = Sockets.connectMasterSocket(args[0]);
            BufferedReader reader = new BufferedReader(new InputStreamReader(master.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                try {
                    String[] tok = line.split("\\|");
                    if (tok.length < 2)
                        continue;
                    int sockHash;
                    Class<?> cls;
                    RootService service;
                    switch (tok[0] /* <action> */) {
                        case RootIPC.NEWSOCK:
                            // <socket hash>
                            sockHash = Integer.parseInt(tok[1]);
                            Sockets.newSocket(sockHash);
                            break;
                        case RootIPC.BIND:
                            // <socket hash>
                            sockHash = Integer.parseInt(tok[1]);
                            Shell.EXECUTOR.execute(() -> handleBind(sockHash));
                            break;
                        case RootIPC.UNBIND:
                            // <service class>
                            cls = Class.forName(tok[1]);
                            service = serviceMap.get(cls);
                            if (service != null && service.unBind()) {
                                service.onUnbind();
                                service.onDestroy();
                                serviceMap.remove(cls);
                            }
                            break;
                        case RootIPC.IPC:
                            // <socket hash>|<service class>
                            sockHash = Integer.parseInt(tok[1]);
                            cls = Class.forName(tok[2]);
                            service = serviceMap.get(cls);
                            if (service != null)
                                service.serverBinder.transact(sockHash);
                            break;
                    }
                } catch (Exception e) {
                    // Never crash, just log and continue listen for requests
                    InternalUtils.stackTrace(e);
                }
            }
        } catch (IOException e) {
            // Socket connection error or I/O error, cannot proceed further
            System.exit(1);
        }
        // The other end of the socket is closed, terminate
        System.exit(0);
    }

    private byte[] rawIntent = new byte[4096];

    private Intent readIntent(Sockets.Handle handle) throws IOException {
        Parcel parcel = Parcel.obtain();
        Intent intent;
        try {
            int intentSz = handle.socketIn.readInt();
            if (rawIntent.length < intentSz)
                rawIntent = new byte[(intentSz / 4096 + 1) * 4096];
            handle.socketIn.readFully(rawIntent, 0, intentSz);
            parcel.unmarshall(rawIntent, 0, intentSz);
            parcel.setDataPosition(0);
            intent = new Intent();
            intent.readFromParcel(parcel);
        } finally {
            parcel.recycle();
        }
        return intent;
    }

    private void handleBind(int hash) {
        Sockets.Handle handle = Sockets.serverGetSocket(hash);
        if (handle == null)
            return;
        try {
            Intent intent = readIntent(handle);
            Class<?> cls = Class.forName(intent.getComponent().getClassName());
            RootService service = serviceMap.get(cls);

            if (service == null) {
                service = (RootService) cls.newInstance();
                serviceMap.put(cls, service);
            }
            service.addBind(intent);

            // ack
            handle.socketOut.writeBoolean(true);
            handle.socketOut.flush();
        } catch (Exception e) {
            InternalUtils.stackTrace(e);
            try {
                // ack
                handle.socketOut.writeBoolean(false);
                handle.socketOut.flush();
            } catch (IOException ignored) {}
        }
    }
}
