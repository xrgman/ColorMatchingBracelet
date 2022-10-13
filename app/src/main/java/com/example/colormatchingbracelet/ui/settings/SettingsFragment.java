package com.example.colormatchingbracelet.ui.settings;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import com.example.colormatchingbracelet.MainActivity;
import com.example.colormatchingbracelet.R;
import com.example.colormatchingbracelet.bluetooth.BluetoothConnection;
import com.example.colormatchingbracelet.bluetooth.BluetoothConnectionCallback;
import com.example.colormatchingbracelet.bluetooth.BluetoothService;
import com.example.colormatchingbracelet.bluetooth.IBluetoothService;
import com.example.colormatchingbracelet.bluetooth.MessageType;
import com.example.colormatchingbracelet.databinding.FragmentSettingsBinding;

public class SettingsFragment extends Fragment implements BluetoothConnectionCallback {
    private FragmentSettingsBinding binding;
    private IBluetoothService bluetoothServiceLink;

    private TextView connStatusBlt;
    private Button connectBltBtn;
    private Button test;

    private final BroadcastReceiver gattUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();

            switch (action) {
                case BluetoothService.ACTION_GATT_CONNECTED:
                    setConnectionStatus(true);
                    break;
                case BluetoothService.ACTION_GATT_DISCONNECTED:
                    setConnectionStatus(false);
                    break;
            }
        }
    };

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {

        binding = FragmentSettingsBinding.inflate(inflater, container, false);
        View root = binding.getRoot();

        connStatusBlt = root.findViewById(R.id.bluetoothConnStatus);
        connectBltBtn = root.findViewById(R.id.buttonConnBlt);

        Handler handler = new Handler();

        handler.postDelayed((Runnable) () -> BluetoothConnection.stopScan(), 1000 * 20);

        test = root.findViewById(R.id.sendTestMsg);
        //test.setEnabled(bluetoothServiceLink.getConnectionState() == BluetoothService.STATE_CONNECTED);

        test.setOnClickListener(view -> {
            if(bluetoothServiceLink.getConnectionState() == BluetoothService.STATE_CONNECTED) {
                bluetoothServiceLink.sendMessage(MessageType.DEBUG, "Letsgooo frm");
            }
        });

        //Restoring connection status:
        setConnectionStatus(bluetoothServiceLink.getConnectionState() == BluetoothService.STATE_CONNECTED);



        return root;
    }

    @Override
    public void onResume() {
        super.onResume();

        requireActivity().registerReceiver(gattUpdateReceiver, MainActivity.makeGattUpdateIntentFilter());
    }

    @Override
    public void onPause() {
        super.onPause();

        requireActivity().unregisterReceiver(gattUpdateReceiver);
    }


    @Override
    public void onAttach(Context context) {
        super.onAttach(context);

        bluetoothServiceLink = (IBluetoothService) context;
    }

    @Override
    public void onDetach() {
        super.onDetach();

        bluetoothServiceLink = null;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    /***
     * Called upon detecting a bluetooth device after starting scanning.
     * @param address - The found bluetooth device.
     */
    @Override
    public void onDeviceFound(String address) {
        bluetoothServiceLink.connectToDevice(address);
        //BluetoothConnection.connect(getContext(), address);
    }

    private void setConnectionStatus(boolean connected) {
        connStatusBlt.setText(connected ? "Connected" : "Disconnected");

        connStatusBlt.setTextColor(connected ? Color.GREEN : Color.RED);

        //Changing button:
        if(connected) {
            test.setEnabled(true);
            connectBltBtn.setText("Disconnect");

            connectBltBtn.setOnClickListener(view -> {
                bluetoothServiceLink.disconnect();
            });
        }
        else{
            test.setEnabled(false);
            connectBltBtn.setText("Connect");

            connectBltBtn.setOnClickListener(view -> {
                BluetoothConnection.startScan(getContext(), this);
            });
        }
    }
}