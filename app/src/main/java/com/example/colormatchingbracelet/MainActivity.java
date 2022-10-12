package com.example.colormatchingbracelet;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.IBinder;
import android.view.View;
import android.widget.TextView;

import com.example.colormatchingbracelet.bluetooth.IBluetoothService;
import com.example.colormatchingbracelet.bluetooth.MessageType;
import com.google.android.material.navigation.NavigationView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.navigation.NavController;
import androidx.navigation.NavDestination;
import androidx.navigation.Navigation;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.appcompat.app.AppCompatActivity;

import com.example.colormatchingbracelet.databinding.ActivityMainBinding;

import com.example.colormatchingbracelet.bluetooth.BluetoothService;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity implements IBluetoothService {

    private AppBarConfiguration mAppBarConfiguration;
    private ActivityMainBinding binding;

    private NavigationView navigationView;

    //Setting up bluetoothService:
    private BluetoothService bluetoothService;
    private ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            bluetoothService = ((BluetoothService.LocalBinder) service).getService();

            if(bluetoothService != null) {
                if(!bluetoothService.initialize()) {
                    finish(); //Stop for now :)
                }
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            bluetoothService = null;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setSupportActionBar(binding.appBarMain2.toolbar);

        DrawerLayout drawer = binding.drawerLayout;
        navigationView = binding.navView;
        // Passing each menu ID as a set of Ids because each
        // menu should be considered as top level destinations.
        mAppBarConfiguration = new AppBarConfiguration.Builder(R.id.nav_home, R.id.nav_settings)
                .setOpenableLayout(drawer)
                .build();

        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment_content_main2);
        NavigationUI.setupActionBarWithNavController(this, navController, mAppBarConfiguration);
        NavigationUI.setupWithNavController(navigationView, navController);

        navController.addOnDestinationChangedListener((navController1, navDestination, bundle) -> {
            setNavigationBarValues(navigationView);
        });

        //Requesting permissions:
        requestMissingPermissions();

        //Setting up service:
        Intent gattServiceIntent = new Intent(this, BluetoothService.class);
        bindService(gattServiceIntent, serviceConnection, Context.BIND_AUTO_CREATE);

    }



    @Override
    public void onResume() {
        super.onResume();

        registerReceiver(gattUpdateReceiver, makeGattUpdateIntentFilter());
    }

    @Override
    public void onPause() {
        super.onPause();

        unregisterReceiver(gattUpdateReceiver);
    }

    @Override
    public boolean onSupportNavigateUp() {
        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment_content_main2);
        return NavigationUI.navigateUp(navController, mAppBarConfiguration) || super.onSupportNavigateUp();
    }

    private final BroadcastReceiver gattUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();

            switch (action) {
                case BluetoothService.ACTION_GATT_CONNECTED:
                    break;
                case BluetoothService.ACTION_GATT_DISCONNECTED:
                case BluetoothService.ACTION_GATT_SERVICES_DISCOVERED:
                    setNavigationBarValues(navigationView);
                    break;
                default:
                    break;
            }
        }
    };

    /**
     * This function makes sure all needed permissions are requested:
     */
    private void requestMissingPermissions() {
        List<String> missingPermissions = new ArrayList<>();

        //Bluetooth:
        if (ContextCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.BLUETOOTH) != PackageManager.PERMISSION_GRANTED) {
            missingPermissions.add(Manifest.permission.BLUETOOTH);
        }

        //Bluetooth admin:
        if (ContextCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.BLUETOOTH_ADMIN) != PackageManager.PERMISSION_GRANTED) {
            missingPermissions.add(Manifest.permission.BLUETOOTH_ADMIN);
        }

        //Bluetooth connect
        if (ContextCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            missingPermissions.add(Manifest.permission.BLUETOOTH_CONNECT);
        }

        //Bluetooth scan
        if (ContextCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
            missingPermissions.add(Manifest.permission.BLUETOOTH_SCAN);
        }

        //Asking permissions:
        if(missingPermissions.size() > 0) {
            ActivityCompat.requestPermissions(this, missingPermissions.toArray(new String[missingPermissions.size()]), 1);
        }
    }


    @Override
    public boolean connectToDevice(String address) {
        return bluetoothService.connectToDevice(address);
    }

    @Override
    public void disconnect() {
        bluetoothService.disconnect();
    }

    @Override
    public void sendMessage(MessageType type, String message) {
        bluetoothService.sendMessage(type, message);
    }

    @Override
    public int getConnectionState() {
        return bluetoothService != null ? bluetoothService.getConnectionState() : -1;
    }

    private void setNavigationBarValues(NavigationView navigationView) {
        View headerView = navigationView.getHeaderView(0);
        TextView navUsername = (TextView) headerView.findViewById(R.id.navBarConnStatus);
        navUsername.setText(getConnectionState() == BluetoothService.STATE_CONNECTED ? "Connected" : "Disconnected");
    }


    public static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(BluetoothService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(BluetoothService.ACTION_GATT_SERVICES_DISCOVERED);
        return intentFilter;
    }
}