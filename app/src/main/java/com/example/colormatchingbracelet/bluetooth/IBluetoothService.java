package com.example.colormatchingbracelet.bluetooth;

public interface IBluetoothService {
    boolean connectToDevice(String address);
    void disconnect();
    void sendMessage(String message);
    int getConnectionState();
}
