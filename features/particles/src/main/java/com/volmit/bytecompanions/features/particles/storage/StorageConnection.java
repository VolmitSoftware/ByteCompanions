package com.volmit.bytecompanions.features.particles.storage;

public interface StorageConnection {

    void connect();
    void disconnect();

    boolean isConnected();

}
