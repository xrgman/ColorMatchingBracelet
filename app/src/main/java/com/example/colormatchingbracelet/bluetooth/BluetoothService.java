package com.example.colormatchingbracelet.bluetooth;

import android.Manifest;
import android.app.Activity;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;

import java.util.UUID;

public class BluetoothService extends Service {

    private BluetoothManager bluetoothManager;
    private BluetoothAdapter bluetoothAdapter;



    private final IBinder mBinder = new LocalBinder();

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    public class LocalBinder extends Binder {

        public BluetoothService getService() {
            return BluetoothService.this;
        }
    }

    public boolean initialize() {
        if (bluetoothManager == null) {
            bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);

            if (bluetoothManager == null) {
                Log.e("BluetoothService", "Unable to initialize BluetoothManager.");

                return false;
            }
        }

        //Grabbing bluetooth adapter:
        bluetoothAdapter = bluetoothManager.getAdapter();

        if (bluetoothAdapter == null) {
            Log.e("BluetoothService", "Unable to obtain a BluetoothAdapter.");

            return false;
        }

        //Checking if bluetooth is enabled:
//        if (!bluetoothAdapter.isEnabled()) {
//            Intent enableBt = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
//
//            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
//                //Already requested
//            }
//
//            startActivity(enableBt);
//        }

        Log.i("BluetoothService", "Initialize BluetoothLeService success!");

        return true;
    }


}
