package com.topjohnwu.superuser.java;

import android.content.ComponentName;
import android.content.Context;
import android.content.ServiceConnection;
import android.net.LocalSocket;
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

import androidx.annotation.WorkerThread;

public class RootIPC {

    static final String BIND = "bind";
    static final String UNBIND = "unbind";
    static final String NEWSOCK = "req";
    static final String IPC = "ipc";

    private static Map<ServiceConnection, RootClientBinder> connMap = new HashMap<>();
    private static Writer socketOut;

    public static synchronized void bindService(
            Class<? extends RootService> cls, ServiceConnection conn) {
        RootClientBinder cachedBinder = connMap.get(conn);
        if (cachedBinder != null)
            return;
        Shell.EXECUTOR.submit(() -> {
            try {
                bindService(InternalUtils.getContext(), cls, conn);
            } catch (IOException e) {
                InternalUtils.stackTrace(e);
                serverError();
            }
            return null;
        });
    }

    public static synchronized void unbindService(ServiceConnection conn) {
        RootClientBinder binder = connMap.get(conn);
        if (binder != null) {
            connMap.remove(conn);
            binder.unBind();
            String msg = String.format("%s|%s\n", UNBIND, binder.getBoundService().getName());
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

    static synchronized void startIPC(int socketHash, Class<? extends RootService> cls) throws IOException {
        String msg = String.format(Locale.US, "%s|%d|%s\n", IPC, socketHash, cls.getName());
        socketOut.write(msg);
        socketOut.flush();
    }

    @WorkerThread
    static synchronized Void serverError() throws IOException {
        Sockets.clearPool();
        Context context = InternalUtils.getContext();
        for (Map.Entry<ServiceConnection, RootClientBinder> e : connMap.entrySet()) {
            ComponentName name = new ComponentName(context, e.getValue().getBoundService());
            UiThreadHandler.run(() -> e.getKey().onServiceDisconnected(name));
        }

        // Rebind services
        socketOut = null;
        for (Map.Entry<ServiceConnection, RootClientBinder> e : connMap.entrySet()) {
            bindService(context, e.getValue().getBoundService(), e.getKey());
        }
        return null;
    }

    @WorkerThread
    private static synchronized void bindService(
            Context context, Class<? extends RootService> cls, ServiceConnection conn)
            throws IOException {
        if (socketOut == null)
            startRootServer(context);
        String msg = String.format("%s|%s\n", BIND, cls.getName());
        socketOut.write(msg);
        socketOut.flush();
        RootClientBinder binder = new RootClientBinder(cls);
        connMap.put(conn, binder);
        ComponentName name = new ComponentName(context, cls);
        UiThreadHandler.run(() -> conn.onServiceConnected(name, binder));
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
