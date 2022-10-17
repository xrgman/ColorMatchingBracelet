package com.example.colormatchingbracelet.ui.home;

import static android.content.Context.LAYOUT_INFLATER_SERVICE;

import android.Manifest;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.Image;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.Preview;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.example.colormatchingbracelet.Bracelet.BraceletInformation;
import com.example.colormatchingbracelet.LedStrip.LedStripCommand;
import com.example.colormatchingbracelet.LedStrip.LedStripCommandType;
import com.example.colormatchingbracelet.LedStrip.LedStripEffectType;
import com.example.colormatchingbracelet.MainActivity;
import com.example.colormatchingbracelet.R;
import com.example.colormatchingbracelet.bluetooth.BluetoothService;
import com.example.colormatchingbracelet.bluetooth.IBluetoothService;
import com.example.colormatchingbracelet.databinding.FragmentHomeBinding;
import com.google.android.material.slider.Slider;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.lifecycle.LifecycleOwner;

import com.google.common.util.concurrent.ListenableFuture;

import java.util.concurrent.ExecutionException;

public class HomeFragment extends Fragment {
    private FragmentHomeBinding binding;
    private IBluetoothService bluetoothServiceLink;

    private ImageView powerButton;
    private Slider brightnessSlider;
    private TextView disconnectedTxt;
    private ImageView colorScanButton;

    //Effect buttons:
    private Button effectRainbowButton;
    private Button effectFadeButton;
    private Button effectCircleButton;

    private boolean powerState = false;

    private ListenableFuture<ProcessCameraProvider> cameraProviderFuture;
    private PreviewView previewView;

    private final BroadcastReceiver gattUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();

            switch (action) {
                case BluetoothService.ACTION_GATT_CONNECTED:
                    setBluetoothEnabled(true);
                case BluetoothService.ACTION_GATT_DISCONNECTED:
                    setBluetoothEnabled(false);
                    break;
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

        colorScanButton = root.findViewById(R.id.colorScanButton);
        colorScanButton.setOnClickListener(view -> {
            //Open popup with camera view and color scanner :)
            createScanColorDialog();
        });

        disconnectedTxt = root.findViewById(R.id.disconnectedTxt);

        brightnessSlider = root.findViewById(R.id.brightnessSlider);
        brightnessSlider.addOnChangeListener((slider, value, fromUser) -> {
            LedStripCommand.sendBrightnessLevel(bluetoothServiceLink, (int) value);
        });

        effectRainbowButton = root.findViewById(R.id.effectRainbowBtn);
        effectRainbowButton.setOnClickListener(view -> {
            LedStripEffectType type = bluetoothServiceLink.getBraceletInformation().ledStripEffectCurrent == LedStripEffectType.NONE ? LedStripEffectType.RAINBOW : LedStripEffectType.NONE;

            LedStripCommand.sendEffect(bluetoothServiceLink, type);
        });

        effectCircleButton = root.findViewById(R.id.effectCircleBtn);
        effectCircleButton.setOnClickListener(view -> {
            LedStripEffectType type = bluetoothServiceLink.getBraceletInformation().ledStripEffectCurrent == LedStripEffectType.NONE ? LedStripEffectType.CIRCLE : LedStripEffectType.NONE;

            LedStripCommand.sendEffect(bluetoothServiceLink, type);
        });

        effectFadeButton = root.findViewById(R.id.effectFadeBtn);
        effectFadeButton.setOnClickListener(view -> {
            LedStripEffectType type = bluetoothServiceLink.getBraceletInformation().ledStripEffectCurrent == LedStripEffectType.NONE ? LedStripEffectType.FADE : LedStripEffectType.NONE;

            LedStripCommand.sendEffect(bluetoothServiceLink, type);
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

        disconnectedTxt.setText(enabled ? "" : "Disconnected");

        setLedStripControlsEnabled(enabled && bluetoothServiceLink.getBraceletInformation().ledStripPowerState);
    }

    //Todo make lists
    private void setLedStripControlsEnabled(boolean enabled) {
        brightnessSlider.setEnabled(enabled);
        //colorScanButton.setEnabled(enabled); TODO enable

        //Effect buttons:
        effectRainbowButton.setEnabled(enabled);
        effectCircleButton.setEnabled(enabled);
        effectFadeButton.setEnabled(enabled);
    }

    private void updateLayout() {
        BraceletInformation braceletInformation = bluetoothServiceLink.getBraceletInformation();

        if(braceletInformation != null) {
            if(powerState != braceletInformation.ledStripPowerState) {
                powerButton.setImageResource(braceletInformation.ledStripPowerState && bluetoothServiceLink.isConnected() ? R.drawable.ic_power_on : R.drawable.ic_power);

                //Turn controls on or off:
                setLedStripControlsEnabled(braceletInformation.ledStripPowerState);
                powerState = braceletInformation.ledStripPowerState;
            }

            brightnessSlider.setValue(braceletInformation.ledStripBrightness);



            //Setting effect button pushed:
            switch(braceletInformation.ledStripEffectCurrent) {
                case RAINBOW:
                    effectRainbowButton.setPressed(true);
                    effectCircleButton.setPressed(false);
                    effectFadeButton.setPressed(false);
                    break;
                case CIRCLE:
                    effectCircleButton.setPressed(true);
                    effectRainbowButton.setPressed(false);
                    effectFadeButton.setPressed(false);
                    break;
                case FADE:
                    effectFadeButton.setPressed(true);
                    effectRainbowButton.setPressed(false);
                    effectCircleButton.setPressed(false);
                    break;
            }
        }
    }

    private void createScanColorDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());

        LayoutInflater inflater = (LayoutInflater) getActivity().getSystemService(LAYOUT_INFLATER_SERVICE);
        View layout = inflater.inflate(R.layout.scan_color_dialog, null);

        builder.setView(layout);
        //builder.setCancelable(false);

        AlertDialog dialog = builder.create();

        //Asking camera permission
        if (ContextCompat.checkSelfPermission(getActivity(), Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(getActivity(), new String[] { Manifest.permission.CAMERA }, 1);
        }

        //Setting up preview:
        cameraProviderFuture = ProcessCameraProvider.getInstance(getActivity());

        previewView = layout.findViewById(R.id.previewView);

        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                bindPreview(cameraProvider);

            } catch (ExecutionException | InterruptedException e) {
                // No errors need to be handled for this Future.
                // This should never be reached.
            }
        }, ContextCompat.getMainExecutor(getActivity()));


        //Registering calibration button
//        Button calibrationButton = layout.findViewById(R.id.calibrateButton);
//        Button startButton = layout.findViewById(R.id.startButton);


        //Showing calibration dialog:
        dialog.show();
    }

    void bindPreview(@NonNull ProcessCameraProvider cameraProvider) {
        Preview preview = new Preview.Builder()
                .build();

        CameraSelector cameraSelector = new CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                .build();

        preview.setSurfaceProvider(previewView.getSurfaceProvider());

        Camera camera = cameraProvider.bindToLifecycle((LifecycleOwner)this, cameraSelector, preview);

    }
}