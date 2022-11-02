package com.example.colormatchingbracelet.ui.home;

import static android.content.Context.LAYOUT_INFLATER_SERVICE;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.LifecycleOwner;

import com.example.colormatchingbracelet.Bracelet.BraceletCommand;
import com.example.colormatchingbracelet.Bracelet.BraceletInformation;
import com.example.colormatchingbracelet.Bracelet.BraceletMode;
import com.example.colormatchingbracelet.LedStrip.LedStripCommand;
import com.example.colormatchingbracelet.LedStrip.LedStripEffectType;
import com.example.colormatchingbracelet.MainActivity;
import com.example.colormatchingbracelet.R;
import com.example.colormatchingbracelet.bluetooth.BluetoothService;
import com.example.colormatchingbracelet.bluetooth.IBluetoothService;
import com.example.colormatchingbracelet.databinding.FragmentHomeBinding;
import com.github.antonpopoff.colorwheel.ColorWheel;
import com.google.android.material.slider.Slider;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.concurrent.ExecutionException;

public class HomeFragment extends Fragment {
    private FragmentHomeBinding binding;
    private IBluetoothService bluetoothServiceLink;

    private ImageView powerButton;
    private Slider brightnessSlider;
    private TextView disconnectedTxt;
    private ImageView colorScanButton;
    private ImageView reactToGesturesButton;

    //Camera fields:
    private ProcessCameraProvider cameraProvider;
    private int selectedColor = Color.WHITE;

    //Effect buttons:
    private Button effectRainbowButton;
    private Button effectCircleButton;
    private Button effectTrailButton;
    private Button effectCompassButton;
    private Button effectTemperatureButton;

    private BraceletInformation previousBraceletInformation;

    private ColorWheel colorWheel;
    private boolean colorWheelInitialized;

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
                case BluetoothService.ACTION_BRACELETINFORMATION_UPDATE:
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
            createScanColorDialog();
        });

        reactToGesturesButton = root.findViewById(R.id.reactToMotionButton);
        reactToGesturesButton.setOnClickListener(view -> {
            BraceletCommand.sendModeChange(bluetoothServiceLink, bluetoothServiceLink.getBraceletInformation().mode == BraceletMode.GESTURE ? BraceletMode.NORMAL : BraceletMode.GESTURE, null);
        });

        disconnectedTxt = root.findViewById(R.id.disconnectedTxt);

        brightnessSlider = root.findViewById(R.id.brightnessSlider);
        brightnessSlider.addOnChangeListener((slider, value, fromUser) -> {
            LedStripCommand.sendBrightnessLevel(bluetoothServiceLink, (int) value);
        });

        colorWheel = root.findViewById(R.id.colorWheel);
        colorWheel.setColorChangeListener(color -> {
            if(colorWheelInitialized && colorWheel.isEnabled()) {
                LedStripCommand.sendColor(bluetoothServiceLink, color);
            }

            colorWheelInitialized = true;
            return null;
        });

        effectRainbowButton = root.findViewById(R.id.effectRainbowBtn);
        effectRainbowButton.setOnClickListener(view -> {
            LedStripEffectType type = bluetoothServiceLink.getBraceletInformation().ledStripEffectCurrent == LedStripEffectType.NONE ? LedStripEffectType.RAINBOW : LedStripEffectType.NONE;

            LedStripCommand.sendEffect(bluetoothServiceLink, type);
        });

        effectTrailButton = root.findViewById(R.id.effectTrailBtn);
        effectTrailButton.setOnClickListener(view -> {
            LedStripEffectType type = bluetoothServiceLink.getBraceletInformation().ledStripEffectCurrent == LedStripEffectType.NONE ? LedStripEffectType.TRAIL : LedStripEffectType.NONE;

            LedStripCommand.sendEffect(bluetoothServiceLink, type);
        });

        effectCircleButton = root.findViewById(R.id.effectCircleBtn);
        effectCircleButton.setOnClickListener(view -> {
            LedStripEffectType type = bluetoothServiceLink.getBraceletInformation().ledStripEffectCurrent == LedStripEffectType.NONE ? LedStripEffectType.CIRCLE : LedStripEffectType.NONE;

            LedStripCommand.sendEffect(bluetoothServiceLink, type);
        });

        effectCompassButton = root.findViewById(R.id.effectCompassBtn);
        effectCompassButton.setOnClickListener(view -> {
            LedStripEffectType type = bluetoothServiceLink.getBraceletInformation().ledStripEffectCurrent == LedStripEffectType.NONE ? LedStripEffectType.COMPASS : LedStripEffectType.NONE;

            LedStripCommand.sendEffect(bluetoothServiceLink, type);
        });

        effectTemperatureButton = root.findViewById(R.id.effectTempBtn);
        effectTemperatureButton.setOnClickListener(view -> {
            LedStripEffectType type = bluetoothServiceLink.getBraceletInformation().ledStripEffectCurrent == LedStripEffectType.NONE ? LedStripEffectType.TEMPERATURE : LedStripEffectType.NONE;

            LedStripCommand.sendEffect(bluetoothServiceLink, type);
        });

        setBluetoothEnabled(bluetoothServiceLink.isConnected());
        updateLayout();

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
        colorScanButton.setEnabled(enabled);
        reactToGesturesButton.setEnabled(enabled);

        //Effect buttons:
        effectRainbowButton.setEnabled(enabled);
        effectTrailButton.setEnabled(enabled);
        effectCircleButton.setEnabled(enabled);
        effectCompassButton.setEnabled(enabled);
        effectTemperatureButton.setEnabled(enabled);

        //Color wheel (TODO check if this actually does something):
        colorWheel.setEnabled(enabled);
    }

    private void updateLayout() {
        BraceletInformation braceletInformation = bluetoothServiceLink.getBraceletInformation();

        if(braceletInformation != null) { //TODO: can be removed after fixing message receiving
            if(previousBraceletInformation == null || previousBraceletInformation.ledStripPowerState != braceletInformation.ledStripPowerState) {
                powerButton.setImageResource(braceletInformation.ledStripPowerState && bluetoothServiceLink.isConnected() ? R.drawable.ic_power_on : R.drawable.ic_power);

                //Turn controls on or off:
                setLedStripControlsEnabled(braceletInformation.ledStripPowerState);
            }

            //On mode change:
            if(previousBraceletInformation == null || previousBraceletInformation.mode != braceletInformation.mode) {

                //Motion button:
                reactToGesturesButton.setImageResource(braceletInformation.mode == BraceletMode.GESTURE ? R.drawable.ic_waving_hand : R.drawable.ic_waving_hand_off);
            }

            //TODO find something for this:
            //brightnessSlider.setValue(5*(Math.round(((braceletInformation.ledStripBrightness*100)/255)/5)));

            //Disabling colorwheel for all colors that do not support color changing:
            colorWheel.setEnabled(braceletInformation.mode.canChangeColor());
        }

        previousBraceletInformation = new BraceletInformation(braceletInformation);
    }

    @SuppressLint("ClickableViewAccessibility")
    private void createScanColorDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());

        LayoutInflater inflater = (LayoutInflater) getActivity().getSystemService(LAYOUT_INFLATER_SERVICE);
        View layout = inflater.inflate(R.layout.scan_color_dialog, null);

        builder.setView(layout);

        AlertDialog dialog = builder.create();

        //Asking camera permission
        if (ContextCompat.checkSelfPermission(getActivity(), Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(getActivity(), new String[] { Manifest.permission.CAMERA }, 1);
        }

        //Finding current color view:
        View currentColorView = layout.findViewById(R.id.rectangle_current_color);

        //Setting up camera preview:
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(getActivity());;
        PreviewView previewView = layout.findViewById(R.id.previewView);

        cameraProviderFuture.addListener(() -> {
            try {
                cameraProvider = cameraProviderFuture.get();

                bindPreview(cameraProvider, previewView);

            } catch (ExecutionException | InterruptedException e) {
                //Should not happen :)
            }
        }, ContextCompat.getMainExecutor(getActivity()));


        //Click event, select color clicked by the user:
        previewView.setOnTouchListener((view, motionEvent) -> {
            if(motionEvent.getAction() == MotionEvent.ACTION_DOWN) {
                //TODO: take a bit larger area and take average color of that :)
                int x = (int) motionEvent.getX();
                int y = (int) motionEvent.getY();

                //Grab current image and extract color:
                Bitmap current = previewView.getBitmap();

                selectedColor = current.getPixel(x, y);

                currentColorView.setBackgroundColor(selectedColor);
            }

            return false;
        });

        //Register set color button:
        Button setColorButton = layout.findViewById(R.id.setColorButton);

        setColorButton.setOnClickListener(view -> {
            LedStripCommand.sendColor(bluetoothServiceLink, selectedColor);
        });

        //Register exit dialog button, does the same as just clicking next to the dialog:
        Button exitDialogButton = layout.findViewById(R.id.exitScanDialogButton);

        exitDialogButton.setOnClickListener(view -> {
            dialog.dismiss();
        });

        //Action when canceling dialog, unbind the camera:
        dialog.setOnDismissListener(dialogInterface -> {
            cameraProvider.unbindAll();
        });

        //Showing calibration dialog:
        dialog.show();
    }

    void bindPreview(@NonNull ProcessCameraProvider cameraProvider, PreviewView previewView) {
        Preview preview = new Preview.Builder()
                .build();

        CameraSelector cameraSelector = new CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                .build();

        preview.setSurfaceProvider(previewView.getSurfaceProvider());

        cameraProvider.bindToLifecycle((LifecycleOwner)this, cameraSelector, preview);
    }
}