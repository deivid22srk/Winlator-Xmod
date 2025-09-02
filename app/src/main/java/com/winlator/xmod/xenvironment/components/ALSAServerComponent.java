package com.winlator.xmod.xenvironment.components;

import com.winlator.xmod.alsaserver.ALSAClientConnectionHandler;
import com.winlator.xmod.alsaserver.ALSARequestHandler;
import com.winlator.xmod.xconnector.UnixSocketConfig;
import com.winlator.xmod.xconnector.XConnectorEpoll;
import com.winlator.xmod.xenvironment.EnvironmentComponent;

public class ALSAServerComponent extends EnvironmentComponent {
    private XConnectorEpoll connector;
    private final UnixSocketConfig socketConfig;

    public ALSAServerComponent(UnixSocketConfig socketConfig) {
        this.socketConfig = socketConfig;
    }

    @Override
    public void start() {
        if (connector != null) return;
        connector = new XConnectorEpoll(socketConfig, new ALSAClientConnectionHandler(), new ALSARequestHandler());
        connector.setMultithreadedClients(true);
        connector.start();
    }

    @Override
    public void stop() {
        if (connector != null) {
            connector.stop();
            connector = null;
        }
    }
}
