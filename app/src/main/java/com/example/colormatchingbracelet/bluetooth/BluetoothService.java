package com.example.colormatchingbracelet.bluetooth;

import android.annotation.SuppressLint;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;

import java.util.List;
import java.util.UUID;


public class BluetoothService extends Service implements IBluetoothService {
    public final static String ACTION_GATT_CONNECTED = "com.example.colormatchingbracelet.ACTION_GATT_CONNECTED";
    public final static String ACTION_GATT_DISCONNECTED = "com.example.colormatchingbracelet.ACTION_GATT_DISCONNECTED";
    public final static String ACTION_GATT_SERVICES_DISCOVERED = "com.example.colormatchingbracelet.ACTION_GATT_SERVICES_DISCOVERED";

    public final static UUID UUID_SERVICE = UUID.fromString("1cf4fab1-d642-4153-a6f2-bf40db8d6f73");
    public final static UUID UUID_NOTIFY = UUID.fromString("75eb965e-a1e1-4b1d-8bb9-91e562cdb144");
    public final static UUID UUID_WRITE = UUID.fromString("aba19161-392b-4bed-9450-3a238abd0040");

    public static final int STATE_DISCONNECTED = 0;
    public static final int STATE_CONNECTED = 2;

    private int connectionState;

    private BluetoothAdapter bluetoothAdapter;
    private BluetoothGatt bluetoothGatt;
    public BluetoothGattCharacteristic bluetoothNotifyCharacteristic;
    public BluetoothGattCharacteristic bluetoothWriteCharacteristic;
    private Binder binder = new LocalBinder();

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @SuppressLint("MissingPermission")
    @Override
    public boolean onUnbind(Intent intent) {
        if (bluetoothGatt == null) {
            super.onUnbind(intent);
        }

        bluetoothGatt.close();
        bluetoothGatt = null;

        return super.onUnbind(intent);
    }

    public class LocalBinder extends Binder {
        public BluetoothService getService() {
            return BluetoothService.this;
        }
    }


    @SuppressLint("MissingPermission")
    public boolean initialize() {
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        if (bluetoothAdapter == null) {
            Log.e("BluetoothService", "Unable to obtain a BluetoothAdapter.");

            return false;
        }

        //Checking if adapter is enabled and if not enable it:
        if (!bluetoothAdapter.isEnabled()) {
            if (!bluetoothAdapter.enable()) {
                Log.e("BluetoothService", "Unable to enable bluetooth");

                return false;
            }
        }

        Log.i("BluetoothService", "Initialize BluetoothLeService success!");

        return true;
    }

    @Override
    @SuppressLint("MissingPermission")
    public boolean connectToDevice(String address) {
        if(bluetoothAdapter == null || address == null) {
            Log.w("BluetoothService", "BluetoothAdapter not initialized or unspecified address.");

            return false;
        }

        try {
            //Grabbing device:
            final BluetoothDevice device = bluetoothAdapter.getRemoteDevice(address);

            //Connecting to the device:
            bluetoothGatt = device.connectGatt(this, false, bluetoothGattCallback);

            return true;
        }
        catch(Exception e) {
            Log.e("BluetoothService", e.getMessage());

            return false;
        }
    }

    @SuppressLint("MissingPermission")
    @Override
    public void disconnect() {
        if (bluetoothAdapter == null || bluetoothGatt == null) {
            Log.e("BluetoothService","BluetoothAdapter or Gatt not initialized");
            return;
        }

        bluetoothGatt.disconnect();
    }


    /**
     * TODO wrapper maken waar type enzo wordt gegeven en dan length blabla protocol.
     * @param message
     */
    @Override
    @SuppressLint("MissingPermission")
    public void sendMessage(String message) {
        byte[] data = message.getBytes();

        bluetoothNotifyCharacteristic.setValue(data);

        bluetoothGatt.writeCharacteristic(bluetoothNotifyCharacteristic);
    }

    private final BluetoothGattCallback bluetoothGattCallback = new BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            if (newState == BluetoothProfile.STATE_CONNECTED) { // successfully connected to the GATT Server
                connectionState = STATE_CONNECTED;
                broadcastUpdate(ACTION_GATT_CONNECTED);

                bluetoothGatt.discoverServices();
            }
            else if (newState == BluetoothProfile.STATE_DISCONNECTED) { // disconnected from the GATT Server
                connectionState = STATE_DISCONNECTED;
                broadcastUpdate(ACTION_GATT_DISCONNECTED);

                BluetoothConnection.stopScan();
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                broadcastUpdate(ACTION_GATT_SERVICES_DISCOVERED);

                setSupportedGattServices();

                BluetoothConnection.stopScan();
            } else {
                Log.w("BluetoothService", "onServicesDiscovered received: " + status);
            }
        }
    };

    public int getConnectionState() {
        return connectionState;
    }

    /**
     * Send update of changes back to main activity.
     * @param action
     */
    private void broadcastUpdate(final String action) {
        final Intent intent = new Intent(action);
        sendBroadcast(intent);
    }

    @SuppressLint("MissingPermission")
    private void setSupportedGattServices() {
        if (bluetoothGatt == null) {
            return;
        }

        List<BluetoothGattService> gattServices = bluetoothGatt.getServices();

        for (BluetoothGattService gattService : gattServices) {
            List<BluetoothGattCharacteristic> gattCharacteristics = gattService.getCharacteristics();

            for (BluetoothGattCharacteristic gattCharacteristic : gattCharacteristics) {

                String uuid = gattCharacteristic.getUuid().toString();
                Log.i("BluetoothLeService","uuid : " + uuid);

                if(uuid.equalsIgnoreCase(UUID_NOTIFY.toString())) {
                    bluetoothNotifyCharacteristic = gattCharacteristic;
                }
                else if(uuid.equalsIgnoreCase(UUID_WRITE.toString())) {
                    bluetoothWriteCharacteristic = gattCharacteristic;
                }
            }
        }
    }
}
