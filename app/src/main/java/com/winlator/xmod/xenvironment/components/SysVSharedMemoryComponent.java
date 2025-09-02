package com.winlator.xmod.xenvironment.components;

import com.winlator.xmod.sysvshm.SysVSHMConnectionHandler;
import com.winlator.xmod.sysvshm.SysVSHMRequestHandler;
import com.winlator.xmod.sysvshm.SysVSharedMemory;
import com.winlator.xmod.xconnector.UnixSocketConfig;
import com.winlator.xmod.xconnector.XConnectorEpoll;
import com.winlator.xmod.xenvironment.EnvironmentComponent;
import com.winlator.xmod.xserver.SHMSegmentManager;
import com.winlator.xmod.xserver.XServer;

public class SysVSharedMemoryComponent extends EnvironmentComponent {
    private XConnectorEpoll connector;
    public final UnixSocketConfig socketConfig;
    private SysVSharedMemory sysVSharedMemory;
    private final XServer xServer;

    public SysVSharedMemoryComponent(XServer xServer, UnixSocketConfig socketConfig) {
        this.xServer = xServer;
        this.socketConfig = socketConfig;
    }

    @Override
    public void start() {
        if (connector != null) return;
        sysVSharedMemory = new SysVSharedMemory();
        connector = new XConnectorEpoll(socketConfig, new SysVSHMConnectionHandler(sysVSharedMemory), new SysVSHMRequestHandler());
        connector.start();

        xServer.setSHMSegmentManager(new SHMSegmentManager(sysVSharedMemory));
    }

    @Override
    public void stop() {
        if (connector != null) {
            connector.stop();
            connector = null;
        }

        sysVSharedMemory.deleteAll();
    }
}
