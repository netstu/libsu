package com.topjohnwu.superuser.java;

import android.os.IBinder;
import android.os.Process;

public abstract class RootService {

    private int bindCount;
    RootServerBinder serverBinder;

    public RootService() {
        bindCount = 0;
        if (Process.myUid() != 0)
            throw new IllegalStateException(
                    "RootService should only be constructed in root process!");
        onCreate();
    }

    public void onCreate() {}

    public abstract IBinder onBind();

    public boolean onUnbind() {
        return false;
    }

    public void onDestroy () {}

    void addBind() {
        if (bindCount == 0)
            serverBinder = new RootServerBinder(onBind());
        bindCount++;
    }

    boolean unBind() {
        bindCount--;
        return bindCount == 0;
    }
}

