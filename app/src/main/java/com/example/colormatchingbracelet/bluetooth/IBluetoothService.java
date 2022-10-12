package com.example.colormatchingbracelet.bluetooth;

import com.example.colormatchingbracelet.Bracelet.BraceletInformation;

public interface IBluetoothService {
    boolean connectToDevice(String address);
    void disconnect();
    void sendMessage(MessageType type, String message);
    int getConnectionState();
    boolean isConnected();
    BraceletInformation getBraceletInformation();
}
