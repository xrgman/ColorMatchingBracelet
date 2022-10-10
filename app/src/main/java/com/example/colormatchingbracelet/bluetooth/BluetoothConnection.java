package com.example.colormatchingbracelet.bluetooth;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.ParcelUuid;
import android.util.Log;

import androidx.core.app.ActivityCompat;

import com.example.colormatchingbracelet.Utils;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class BluetoothConnection {
    public static ParcelUuid braceletUUID = ParcelUuid.fromString("1cf4fab1-d642-4153-a6f2-bf40db8d6f73");
    public static String braceletName = "Color Matching Bracelet";

    private static BluetoothLeScanner bluetoothScanner;
    private static ProgressDialog scanDialog;

    private static boolean deviceFound = false;
    private boolean isConnected;

    public BluetoothConnection() {
        isConnected = false;
    }

    @SuppressLint("MissingPermission")
    public static void startScan(Context context) {
        BluetoothManager bluetoothManager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        BluetoothAdapter bluetoothAdapter = bluetoothManager.getAdapter();

        //Checking if adapter is enabled and if not enable it:
        if (!bluetoothAdapter.isEnabled()) {
            if (!bluetoothAdapter.enable()) {
                //Do something
            }
        }

        //Create scan filter for bracelet UUID:
        List<ScanFilter> filters = new ArrayList<>();

        //filters.add(new ScanFilter.Builder().setServiceUuid(braceletUUID).build());
        //filters.add(new ScanFilter.Builder().setDeviceAddress("94:B9:7E:DE:A0:4E").build());
        filters.add(new ScanFilter.Builder().setDeviceName(braceletName).build()); //Filter on uuid did not work, so we do it on name for now :(

        //Create scan settings:
        ScanSettings scanSettings = new ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_POWER)
                .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
                .setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE)
                .setNumOfMatches(ScanSettings.MATCH_NUM_ONE_ADVERTISEMENT)
                .setReportDelay(0L)
                .build();

        //Creating scanner and scan for bracelet:
        bluetoothScanner = bluetoothAdapter.getBluetoothLeScanner();

        bluetoothScanner.startScan(filters, scanSettings, scanCallback);

        scanDialog = Utils.CreateProgressDialog(context, "Scanning", true, false);
        scanDialog.setMessage("Searching bracelet...");

        Log.i("BluetoothScan", "Bluetooth scan started");
    }

    @SuppressLint("MissingPermission")
    public static void stopScan() {
        if (bluetoothScanner != null) {
            bluetoothScanner.stopScan(scanCallback);

            if(scanDialog != null) {
                scanDialog.cancel();
            }

            Log.i("BluetoothScan", "Bluetooth scan ended");
        } else {
            Log.e("BluetoothScan","mBluetoothAdapter is null.");
        }
    }

    private static ScanCallback scanCallback = new ScanCallback() {

        @SuppressLint("MissingPermission")
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            if(!deviceFound) {
                deviceFound = true;


                BluetoothDevice device = result.getDevice();


                super.onScanResult(callbackType, result);

                stopScan();
            }
        }

        @Override
        public void onScanFailed(int errorCode) {
            super.onScanFailed(errorCode);
        }
    };




}
