package com.topjohnwu.libsuexample;

import android.content.Intent;
import android.os.IBinder;
import android.os.Process;

import com.topjohnwu.superuser.java.RootService;

public class TestService extends RootService {

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    private ITest.Stub mBinder = new ITest.Stub() {
        @Override
        public int myPid() {
            return Process.myPid();
        }
    };
}
