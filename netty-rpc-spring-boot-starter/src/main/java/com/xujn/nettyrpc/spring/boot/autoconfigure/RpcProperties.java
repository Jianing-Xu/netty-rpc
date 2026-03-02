package com.xujn.nettyrpc.spring.boot.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "netty-rpc")
public class RpcProperties {

    /**
     * ZooKeeper connection address.
     */
    private String zkAddress = "127.0.0.1:2181";

    /**
     * Server bind host.
     */
    private String serverAddress = "127.0.0.1";

    /**
     * Server bind port.
     */
    private int serverPort = 8080;

    /**
     * Enable running the Netty RPC server.
     */
    private boolean serverEnable = false;

    public String getZkAddress() {
        return zkAddress;
    }

    public void setZkAddress(String zkAddress) {
        this.zkAddress = zkAddress;
    }

    public String getServerAddress() {
        return serverAddress;
    }

    public void setServerAddress(String serverAddress) {
        this.serverAddress = serverAddress;
    }

    public int getServerPort() {
        return serverPort;
    }

    public void setServerPort(int serverPort) {
        this.serverPort = serverPort;
    }

    public boolean isServerEnable() {
        return serverEnable;
    }

    public void setServerEnable(boolean serverEnable) {
        this.serverEnable = serverEnable;
    }
}
