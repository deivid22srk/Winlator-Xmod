package com.winlator.xmod.xenvironment.components;

import com.winlator.xmod.xenvironment.EnvironmentComponent;
import com.winlator.xmod.xconnector.XConnectorEpoll;
import com.winlator.xmod.xconnector.UnixSocketConfig;
import com.winlator.xmod.xserver.XClientConnectionHandler;
import com.winlator.xmod.xserver.XClientRequestHandler;
import com.winlator.xmod.xserver.XServer;

public class XServerComponent extends EnvironmentComponent {
    private XConnectorEpoll connector;
    private final XServer xServer;
    private final UnixSocketConfig socketConfig;

    public XServerComponent(XServer xServer, UnixSocketConfig socketConfig) {
        this.xServer = xServer;
        this.socketConfig = socketConfig;
    }

    @Override
    public void start() {
        if (connector != null) return;
        connector = new XConnectorEpoll(socketConfig, new XClientConnectionHandler(xServer), new XClientRequestHandler());
        connector.setInitialInputBufferCapacity(262144);
        connector.setCanReceiveAncillaryMessages(true);
        connector.start();
    }

    @Override
    public void stop() {
        if (connector != null) {
            connector.stop();
            connector = null;
        }
    }

    public XServer getXServer() {
        return xServer;
    }
}
