package com.topjohnwu.superuser.java;

import android.net.LocalServerSocket;
import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import android.util.SparseArray;

import com.topjohnwu.superuser.internal.InternalUtils;
import com.topjohnwu.superuser.internal.UiThreadHandler;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.Closeable;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;

public class Sockets {

    public static class Handle implements Closeable {
        public LocalSocket socket;
        public DataInputStream socketIn;
        public DataOutputStream socketOut;

        void setupStreams() throws IOException {
            socketIn = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
            socketOut = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
        }

        @Override
        public void close() {
            // Return the handle back to the pool
            socketPool.offer(this);
        }
    }

    // Client side
    private static Queue<Handle> socketPool;
    private static LocalServerSocket serverSocket;

    static LocalSocket newMasterSocket(String name) throws IOException {
        serverSocket = new LocalServerSocket(name);
        return serverSocket.accept();
    }

    static Handle clientGetSocket() throws IOException {
        if (socketPool == null)
            socketPool = new ConcurrentLinkedQueue<>();
        Handle handle = socketPool.poll();
        if (handle == null) {
            Handle newHandle = new Handle();
            try {
                UiThreadHandler.synchronousWorker(() -> {
                    RootIPC.newSocketPair(newHandle.hashCode());
                    newHandle.socket = serverSocket.accept();
                    newHandle.setupStreams();
                    return null;
                });
            } catch (ExecutionException e) {
                InternalUtils.stackTrace(e);
                throw (IOException) e.getCause();
            }
            handle = newHandle;
        }
        return handle;
    }

    static void clearPool() {
        socketPool.clear();
    }

    // Server side
    private static SparseArray<Handle> socketMap;
    private static LocalSocketAddress masterAddress;

    static Handle serverGetSocket(int client) {
        return socketMap.get(client);
    }

    static LocalSocket connectMasterSocket(String name) throws IOException {
        LocalSocket master = new LocalSocket();
        masterAddress = new LocalSocketAddress(name, LocalSocketAddress.Namespace.ABSTRACT);
        master.connect(masterAddress);
        return master;
    }

    static void newServer(int hash) {
        if (socketMap == null)
            socketMap = new SparseArray<>();
        try {
            Handle handle = new Handle();
            handle.socket = new LocalSocket();
            handle.socket.connect(masterAddress);
            handle.setupStreams();
            socketMap.put(hash, handle);
        } catch (IOException e) {
            InternalUtils.stackTrace(e);
        }
    }
}
