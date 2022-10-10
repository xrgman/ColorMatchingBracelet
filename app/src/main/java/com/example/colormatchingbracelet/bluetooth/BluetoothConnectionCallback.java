package com.example.colormatchingbracelet.bluetooth;

import android.bluetooth.BluetoothDevice;

public interface BluetoothConnectionCallback {
    void onDeviceFound(String address);
}
