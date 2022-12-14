package com.example.colormatchingbracelet.bluetooth;

import android.annotation.SuppressLint;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.os.Message;
import android.util.Log;

import androidx.annotation.Nullable;

import com.example.colormatchingbracelet.Bracelet.BraceletCommand;
import com.example.colormatchingbracelet.Bracelet.BraceletInformation;
import com.example.colormatchingbracelet.Bracelet.BraceletMode;
import com.example.colormatchingbracelet.LedStrip.LedStripEffectType;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;


public class BluetoothService extends Service implements IBluetoothService {
    public final static String ACTION_GATT_CONNECTED = "com.example.colormatchingbracelet.ACTION_GATT_CONNECTED";
    public final static String ACTION_GATT_DISCONNECTED = "com.example.colormatchingbracelet.ACTION_GATT_DISCONNECTED";
    public final static String ACTION_GATT_SERVICES_DISCOVERED = "com.example.colormatchingbracelet.ACTION_GATT_SERVICES_DISCOVERED";
    public final static String ACTION_GATT_MESSAGE_RECEIVED = "com.example.colormatchingbracelet.ACTION_GATT_MESSAGE_RECEIVED";
    public final static String ACTION_BRACELETINFORMATION_UPDATE = "com.example.colormatchingbracelet.ACTION_BRACELETINFORMATION_UPDATE";

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

    private BraceletInformation braceletInformation;

    public BluetoothService() {

    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        braceletInformation = new BraceletInformation();

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
     * Start | Type | length | data | checksum
     */
    @Override
    @SuppressLint("MissingPermission")
    public void sendMessage(MessageType type, byte[] data) {
        int length = 4 + data.length;
        byte[] dataToSend = new byte[length];
        int checkSum = 0;

        //Start character:
        dataToSend[0] = '?';
        checkSum ^= 63;

        //Message type:
        dataToSend[1] = (byte) type.getValue();
        checkSum ^= type.getValue();

        //Message length:
        dataToSend[2] = (byte) data.length;
        checkSum ^= data.length;

        //Data:
        for(int i = 0; i < data.length; i++) {
            dataToSend[3 + i] = data[i];

            checkSum ^= data[i];
        }

        //Checksum:
        dataToSend[length - 1] = (byte) checkSum;

        bluetoothNotifyCharacteristic.setValue(dataToSend);

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
                setSupportedGattServices();

                BluetoothConnection.stopScan();

                broadcastUpdate(ACTION_GATT_SERVICES_DISCOVERED);
            } else {
                Log.w("BluetoothService", "onServicesDiscovered received: " + status);
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            byte[] inputRaw = characteristic.getValue();
            int[] input = new int[inputRaw.length];

            //Translate to unsigned bytes:
            for(int i =0; i < inputRaw.length; i++) {
                input[i] = Byte.toUnsignedInt(inputRaw[i]);
            }

            //Checking if minimum fields are there:
            if(input.length < 4) {
                Log.e("BluetoothService", "Not enough fields");
                return;
            }

            //Checking start character
            if(input[0] != 63) { //Start character '?' (63)
                Log.e("BluetoothService", "Invalid start character: " + (char) input[0] + ", skipping message");
                return;
            }

            //Extracting type:
            int typeId = input[1];

            //Checking if type exists:
            if(typeId > MessageType.values().length) {
                Log.e("BluetoothService", "Invalid message type: " + typeId + ", skipping message");
                return;
            }

            MessageType type = MessageType.values()[typeId];

            //Grabbing message length:
            int messageLength = input[2];

            //Checking checksum:
            int checkSum = input[input.length - 1];
            int checkSumComp = 0;

            for(int i = 0; i < input.length - 1; i++) {
                checkSumComp ^= input[i];
            }

            if(checkSumComp != checkSum) {
                Log.e("BluetoothService", "Invalid checksum, skipping message");
                return;
            }

            //Grabbing data:
            int[] data = Arrays.copyOfRange(input, 3,3 + messageLength);

            //Processing message:
            processMessage(type, data);

            //Broadcasting message received:
            broadcastUpdate(ACTION_GATT_MESSAGE_RECEIVED);
        }
    };

    public int getConnectionState() {
        return connectionState;
    }

    @Override
    public boolean isConnected() {
        return connectionState == STATE_CONNECTED;
    }

    @Override
    public BraceletInformation getBraceletInformation() {
        return braceletInformation;
    }

    @Override
    public void updateBraceletInformation(BraceletInformation newBraceletInformation) {
        braceletInformation = newBraceletInformation;

        broadcastUpdate(ACTION_BRACELETINFORMATION_UPDATE);
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
    //TODO check UUID of device :)
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

                    //Enable notifications from client:
                    bluetoothGatt.setCharacteristicNotification(bluetoothWriteCharacteristic, true);

                    BluetoothGattDescriptor desc = bluetoothWriteCharacteristic.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805F9B34FB"));

                    desc.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                    bluetoothGatt.writeDescriptor(desc);
                }
            }
        }
    }

    //#region Message Processing

    /**
     * Check type of message and pass data to correct function;
     */
    private void processMessage(MessageType type, int[] data) {
        switch(type) {
            case STATUS:
                processStatusMessage(data);
                break;
        }
    }

    private void processStatusMessage(int[] data) {
        BraceletInformation newBraceletInfo = new BraceletInformation();

        newBraceletInfo.mode = BraceletMode.values()[data[0]];
        newBraceletInfo.batteryPercentage = data[1];
        newBraceletInfo.ledStripPowerState = data[2] == '1';
        newBraceletInfo.ledStripEffectCurrent = LedStripEffectType.values()[data[3]];
        newBraceletInfo.ledStripBrightness = data[4];
        newBraceletInfo.numGestures = data[5];

        updateBraceletInformation(newBraceletInfo);
    }

    //#endregion
}
