package com.topjohnwu.superuser.java;

import android.net.LocalSocket;
import android.net.LocalSocketAddress;

import com.topjohnwu.superuser.Shell;
import com.topjohnwu.superuser.internal.InternalUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class EntryPoint {

    static Map<Class, RootService> serviceMap = Collections.synchronizedMap(new HashMap<>());
    static LocalSocketAddress masterAddress;

    static final String BIND = "bind";
    static final String UNBIND = "unbind";

    public static void main(String[] args) {
        Shell.Config.verboseLogging(true);
        if (args.length < 1)
            System.exit(1);
        System.out.close();
        System.err.close();
        new EntryPoint().run(args);
    }

    private EntryPoint() {}

    public void run(String[] args) {
        LocalSocket master = new LocalSocket();
        masterAddress = new LocalSocketAddress(args[0], LocalSocketAddress.Namespace.ABSTRACT);
        try {
            master.connect(masterAddress);
            BufferedReader reader = new BufferedReader(new InputStreamReader(master.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                // <action>|<service class name>|<session>
                String[] split = line.split("\\|");
                if (split.length != 3)
                    continue;
                int session = Integer.parseInt(split[2]);
                try {
                    Class<?> cls = Class.forName(split[1]);
                    RootService service = serviceMap.get(cls);
                    if (split[0].equals(BIND)) {
                        if (service == null) {
                            if (!RootService.class.isAssignableFrom(cls))
                                continue;
                            service = (RootService) cls.newInstance();
                            serviceMap.put(cls, service);
                        }
                        service.bindSession(session);
                    } else if (split[0].equals(UNBIND)) {
                        if (service != null)
                            service.unBindSession(session);
                    }
                } catch (Exception e) {
                    InternalUtils.stackTrace(e);
                }
            }
        } catch (IOException e) {
            System.exit(1);
        }
        System.exit(0);
    }
}
