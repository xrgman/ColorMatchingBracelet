package com.example.colormatchingbracelet;

import android.Manifest;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;

import com.google.android.material.navigation.NavigationView;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.appcompat.app.AppCompatActivity;

import com.example.colormatchingbracelet.databinding.ActivityMainBinding;

import com.example.colormatchingbracelet.bluetooth.BluetoothService;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private AppBarConfiguration mAppBarConfiguration;
    private ActivityMainBinding binding;

    private BluetoothService bluetoothLeService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setSupportActionBar(binding.appBarMain2.toolbar);

        DrawerLayout drawer = binding.drawerLayout;
        NavigationView navigationView = binding.navView;
        // Passing each menu ID as a set of Ids because each
        // menu should be considered as top level destinations.
        mAppBarConfiguration = new AppBarConfiguration.Builder(
                R.id.nav_home, R.id.nav_settings)
                .setOpenableLayout(drawer)
                .build();
        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment_content_main2);
        NavigationUI.setupActionBarWithNavController(this, navController, mAppBarConfiguration);
        NavigationUI.setupWithNavController(navigationView, navController);

        //Requesting permissions:
        requestMissingPermissions();

        initService();
    }

    @Override
    public boolean onSupportNavigateUp() {
        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment_content_main2);
        return NavigationUI.navigateUp(navController, mAppBarConfiguration) || super.onSupportNavigateUp();
    }

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

        //Coarse location:
//        if (ContextCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
//            missingPermissions.add(Manifest.permission.ACCESS_FINE_LOCATION);
//        }

        if(missingPermissions.size() > 0) {
            ActivityCompat.requestPermissions(this, missingPermissions.toArray(new String[missingPermissions.size()]), 1);
        }
    }

    private void initService() {
        if (bluetoothLeService == null) {
            Intent gattServiceIntent = new Intent(this, BluetoothService.class);
            bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);
        }
    }

    private final ServiceConnection mServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            bluetoothLeService = ((BluetoothService.LocalBinder) service).getService();
            if (!bluetoothLeService.initialize()) {
                Log.e("MainActivity","Unable to initialize Bluetooth");
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            bluetoothLeService = null;
        }
    };
}