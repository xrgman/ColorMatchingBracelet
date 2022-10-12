package com.example.colormatchingbracelet.bluetooth;

public interface IBluetoothService {
    boolean connectToDevice(String address);
    void disconnect();
    void sendMessage(MessageType type, String message);
    int getConnectionState();
}
