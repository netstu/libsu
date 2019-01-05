package com.topjohnwu.superuser.java;

import android.content.ComponentName;
import android.content.Context;
import android.content.ServiceConnection;
import android.net.LocalServerSocket;
import android.net.LocalSocket;
import android.os.Process;

import com.topjohnwu.superuser.Shell;
import com.topjohnwu.superuser.ShellUtils;
import com.topjohnwu.superuser.internal.InternalUtils;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class RootIPC {

    private static Map<ServiceConnection, RootBinder> connMap = new HashMap<>();

    static LocalServerSocket serverSocket;
    private static Writer socketOut;

    public static void bindService(Context context, Class<? extends RootService> cls, ServiceConnection conn) {
        if (serverSocket == null)
            startRootServer(context);
        if (serverSocket == null)
            return;
        ComponentName name = new ComponentName(context, cls);
        String msg = String.format(Locale.US, "%s|%s|%d\n",
                EntryPoint.BIND, cls.getName(), conn.hashCode());
        try {
            socketOut.write(msg);
            socketOut.flush();
            RootBinder binder = new RootBinder(cls);
            connMap.put(conn, binder);
            conn.onServiceConnected(name, binder);
        } catch (IOException e) {
            InternalUtils.stackTrace(e);
            conn.onServiceDisconnected(name);
        }
    }

    public static void unbindService (ServiceConnection conn) {
        RootBinder binder = connMap.get(conn);
        if (binder != null) {
            connMap.remove(conn);
            String msg = String.format(Locale.US, "%s|%s|%d\n", EntryPoint.UNBIND,
                    binder.getBoundService().getName(), conn.hashCode());
            try {
                socketOut.write(msg);
                socketOut.flush();
            } catch (IOException e) {
                InternalUtils.stackTrace(e);
            }
            binder.unBind();
        }
    }

    private static void startRootServer(Context context) {
        try {
            serverSocket = new LocalServerSocket(ShellUtils.genRandomAlphaNumString(16).toString());
            String cmd = String.format(Locale.US,
                    "(LD_LIBRARY_PATH=/system/lib:/vendor/lib CLASSPATH=%s /proc/%d/exe /system/bin %s %s)&",
                    context.getPackageCodePath(), Process.myPid(), EntryPoint.class.getName(),
                    serverSocket.getLocalSocketAddress().getName());
            Shell.su(cmd).exec();
            LocalSocket socket = serverSocket.accept();
            socketOut = new OutputStreamWriter(socket.getOutputStream());
        } catch (IOException e) {
            InternalUtils.stackTrace(e);
        }
    }
}
