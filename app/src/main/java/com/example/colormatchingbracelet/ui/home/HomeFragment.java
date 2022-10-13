package com.example.colormatchingbracelet.ui.home;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import com.example.colormatchingbracelet.Bracelet.BraceletInformation;
import com.example.colormatchingbracelet.LedStrip.LedStripCommand;
import com.example.colormatchingbracelet.LedStrip.LedStripCommandType;
import com.example.colormatchingbracelet.MainActivity;
import com.example.colormatchingbracelet.R;
import com.example.colormatchingbracelet.bluetooth.BluetoothService;
import com.example.colormatchingbracelet.bluetooth.IBluetoothService;
import com.example.colormatchingbracelet.databinding.FragmentHomeBinding;
import com.google.android.material.slider.Slider;

public class HomeFragment extends Fragment {
    private FragmentHomeBinding binding;
    private IBluetoothService bluetoothServiceLink;

    private ImageView powerButton;
    private Slider brightnessSlider;

    private boolean powerState = false;

    private final BroadcastReceiver gattUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();

            switch (action) {
                case BluetoothService.ACTION_GATT_MESSAGE_RECEIVED:
                    updateLayout();
                    break;
            }
        }
    };

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {

        binding = FragmentHomeBinding.inflate(inflater, container, false);
        View root = binding.getRoot();

        powerButton = root.findViewById(R.id.powerButton);
        powerButton.setOnClickListener(view -> {
            LedStripCommand.sendPowerMessage(bluetoothServiceLink, !bluetoothServiceLink.getBraceletInformation().ledStripPowerState);
        });

        brightnessSlider = root.findViewById(R.id.brightnessSlider);
        brightnessSlider.addOnChangeListener((slider, value, fromUser) -> {
            int valueToSend = (int) ((value/100)*255);

            LedStripCommand.sendBrightnessLevel(bluetoothServiceLink, valueToSend);
        });

        setBluetoothEnabled(bluetoothServiceLink.isConnected());

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
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
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

    private void setBluetoothEnabled(boolean enabled) {
        powerButton.setEnabled(enabled);

        setLedStripControlsEnabled(enabled && bluetoothServiceLink.getBraceletInformation().ledStripPowerState);
    }

    private void setLedStripControlsEnabled(boolean enabled) {
        brightnessSlider.setEnabled(enabled);
    }

    private void updateLayout() {
        BraceletInformation braceletInformation = bluetoothServiceLink.getBraceletInformation();

        if(braceletInformation != null) {
            if(powerState != braceletInformation.ledStripPowerState) {
                powerButton.setImageResource(braceletInformation.ledStripPowerState ? R.drawable.ic_power_on : R.drawable.ic_power);

                //Turn controls on or off:
                setLedStripControlsEnabled(braceletInformation.ledStripPowerState);
                powerState = braceletInformation.ledStripPowerState;
            }


        }

    }
}