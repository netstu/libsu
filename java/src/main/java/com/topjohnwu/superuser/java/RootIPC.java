package com.topjohnwu.superuser.java;

import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.net.LocalSocket;
import android.os.Parcel;
import android.os.Process;

import com.topjohnwu.superuser.Shell;
import com.topjohnwu.superuser.ShellUtils;
import com.topjohnwu.superuser.internal.InternalUtils;
import com.topjohnwu.superuser.internal.UiThreadHandler;

import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import androidx.annotation.WorkerThread;

public class RootIPC {

    static final String BIND = "bind";
    static final String UNBIND = "unbind";
    static final String NEWSOCK = "req";
    static final String IPC = "ipc";

    private static Map<ServiceConnection, RootClientBinder> connMap = new HashMap<>();
    private static Writer socketOut;

    public static synchronized void bindService(Intent intent, ServiceConnection conn) {
        RootClientBinder cachedBinder = connMap.get(conn);
        if (cachedBinder != null)
            return;
        Shell.EXECUTOR.execute(() -> {
            if (socketOut == null) {
                try {
                    startRootServer(InternalUtils.getContext());
                } catch (IOException e) {
                    return;
                }
            }
            Future bind = Shell.EXECUTOR.submit(() -> bindService0(intent, conn));
            try {
                // At most wait for 3 seconds
                bind.get(3, TimeUnit.SECONDS);
            } catch (ExecutionException e) {
                InternalUtils.stackTrace(e);
                try {
                    serverError();
                } catch (IOException ignored) {}
            } catch (TimeoutException e) {
                InternalUtils.stackTrace(e);
                bind.cancel(true);
            } catch (InterruptedException ignored) {}
        });
    }

    public static synchronized void unbindService(ServiceConnection conn) {
        RootClientBinder binder = connMap.get(conn);
        if (binder != null) {
            connMap.remove(conn);
            binder.unBind();
            String msg = String.format("%s|%s\n", UNBIND, binder.getIntent().getComponent().getClassName());
            try {
                socketOut.write(msg);
                socketOut.flush();
            } catch (IOException e) {
                InternalUtils.stackTrace(e);
                Shell.EXECUTOR.submit(RootIPC::serverError);
            }
        }
    }

    static synchronized void newSocketPair(int hash) throws IOException {
        String msg = String.format(Locale.US, "%s|%d\n", NEWSOCK, hash);
        socketOut.write(msg);
        socketOut.flush();
    }

    static synchronized void startIPC(int socketHash, String cls) throws IOException {
        String msg = String.format(Locale.US, "%s|%d|%s\n", IPC, socketHash, cls);
        socketOut.write(msg);
        socketOut.flush();
    }

    @WorkerThread
    static synchronized Void serverError() throws IOException {
        Sockets.clearPool();
        for (Map.Entry<ServiceConnection, RootClientBinder> e : connMap.entrySet()) {
            UiThreadHandler.run(() ->
                    e.getKey().onServiceDisconnected(e.getValue().getIntent().getComponent()));
        }

        // Rebind services
        socketOut = null;
        for (Map.Entry<ServiceConnection, RootClientBinder> e : connMap.entrySet()) {
            bindService0(e.getValue().getIntent(), e.getKey());
        }
        return null;
    }

    @WorkerThread
    private static void writeIntent(Intent intent, Sockets.Handle handle) throws IOException {
        Parcel parcel = Parcel.obtain();
        try {
            // Pass intent to server
            intent.writeToParcel(parcel, 0);
            byte[] rawIntent = parcel.marshall();
            handle.socketOut.writeInt(rawIntent.length);
            handle.socketOut.write(rawIntent);
            handle.socketOut.flush();
        } finally {
            parcel.recycle();
        }
    }

    @WorkerThread
    private static synchronized Void bindService0(Intent intent, ServiceConnection conn)
            throws IOException {
        try (Sockets.Handle handle = Sockets.clientGetSocket()) {
            String msg = String.format(Locale.US, "%s|%d\n", BIND, handle.hashCode());
            socketOut.write(msg);
            socketOut.flush();

            // Pass intent to server
            writeIntent(intent, handle);

            // Wait for ack
            if (!handle.socketIn.readBoolean())
                return null;
        }
        RootClientBinder binder = new RootClientBinder(intent);
        connMap.put(conn, binder);
        UiThreadHandler.run(() -> conn.onServiceConnected(intent.getComponent(), binder));
        return null;
    }

    @WorkerThread
    private static synchronized void startRootServer(Context context) throws IOException {
        String app_process = new File("/proc/self/exe").getCanonicalPath();
        String LD_LIBRARY_PATH = app_process.contains("64") ?
                "/system/lib64:/vendor/lib64" :
                "/system/lib:/vendor/lib";
        String socketName = ShellUtils.genRandomAlphaNumString(16).toString();
        String cmd = String.format(Locale.US,
                "(LD_LIBRARY_PATH=%s CLASSPATH=%s %s /system/bin " +
                        "--nice-name=`cat /proc/%d/cmdline`:root %s %s)&",
                LD_LIBRARY_PATH, context.getPackageCodePath(), app_process,
                Process.myPid(), EntryPoint.class.getName(), socketName);
        Shell.su(cmd).exec();

        // Establish connection after server started
        LocalSocket socket = Sockets.newMasterSocket(socketName);
        socketOut = new OutputStreamWriter(socket.getOutputStream());
    }
}
