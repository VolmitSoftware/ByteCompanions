package com.volmit.bytecompanions.spi.storage;

public interface StorageConnection {

    void connect();
    void disconnect();

    boolean isConnected();

}
