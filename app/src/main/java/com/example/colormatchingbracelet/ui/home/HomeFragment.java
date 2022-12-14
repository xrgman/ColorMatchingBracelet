package com.example.colormatchingbracelet.ui.home;

import static android.content.Context.LAYOUT_INFLATER_SERVICE;

import static com.example.colormatchingbracelet.LedStrip.LedStripEffectType.RAINBOW;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.hardware.camera2.CameraManager;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.camera.core.Camera;
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
import com.example.colormatchingbracelet.Utils;
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
    private Button effectFadeButton;

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
            BraceletMode mode = bluetoothServiceLink.getBraceletInformation().mode;

            if (mode == BraceletMode.EFFECT || mode == BraceletMode.GESTURE_EFFECT) {
                byte[] data = new byte[1];
                data[0] = (byte) bluetoothServiceLink.getBraceletInformation().ledStripEffectCurrent.getValue();

                BraceletCommand.sendModeChange(bluetoothServiceLink, bluetoothServiceLink.getBraceletInformation().mode == BraceletMode.GESTURE_EFFECT ? BraceletMode.EFFECT : BraceletMode.GESTURE_EFFECT, data);
            } else {
                BraceletCommand.sendModeChange(bluetoothServiceLink, bluetoothServiceLink.getBraceletInformation().mode == BraceletMode.GESTURE ? BraceletMode.NORMAL : BraceletMode.GESTURE, null);
            }
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
        effectRainbowButton.setOnTouchListener((view, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) return true;
            if (event.getAction() != MotionEvent.ACTION_UP) return false;

            LedStripEffectType effect = bluetoothServiceLink.getBraceletInformation().ledStripEffectCurrent == LedStripEffectType.RAINBOW ? LedStripEffectType.NONE : LedStripEffectType.RAINBOW;
            LedStripCommand.sendEffect(bluetoothServiceLink, effect);

            updateButtons();

            return true;
        });

        effectTrailButton = root.findViewById(R.id.effectTrailBtn);
        effectTrailButton.setOnTouchListener((view, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) return true;
            if (event.getAction() != MotionEvent.ACTION_UP) return false;

            LedStripEffectType effect = bluetoothServiceLink.getBraceletInformation().ledStripEffectCurrent == LedStripEffectType.TRAIL ? LedStripEffectType.NONE : LedStripEffectType.TRAIL;
            LedStripCommand.sendEffect(bluetoothServiceLink, effect);

            updateButtons();

            return true;
        });

        effectCircleButton = root.findViewById(R.id.effectCircleBtn);
        effectCircleButton.setOnTouchListener((view, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) return true;
            if (event.getAction() != MotionEvent.ACTION_UP) return false;

            LedStripEffectType effect = bluetoothServiceLink.getBraceletInformation().ledStripEffectCurrent == LedStripEffectType.CIRCLE ? LedStripEffectType.NONE : LedStripEffectType.CIRCLE;
            LedStripCommand.sendEffect(bluetoothServiceLink, effect);

            updateButtons();

            return true;
        });

        effectCompassButton = root.findViewById(R.id.effectCompassBtn);
        effectCompassButton.setOnTouchListener((view, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) return true;
            if (event.getAction() != MotionEvent.ACTION_UP) return false;

            LedStripEffectType effect = bluetoothServiceLink.getBraceletInformation().ledStripEffectCurrent == LedStripEffectType.COMPASS ? LedStripEffectType.NONE : LedStripEffectType.COMPASS;
            LedStripCommand.sendEffect(bluetoothServiceLink, effect);

            updateButtons();

            return true;
        });

        effectFadeButton = root.findViewById(R.id.effectFadeBtn);
        effectFadeButton.setOnTouchListener((view, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) return true;
            if (event.getAction() != MotionEvent.ACTION_UP) return false;

            LedStripEffectType effect = bluetoothServiceLink.getBraceletInformation().ledStripEffectCurrent == LedStripEffectType.FADE ? LedStripEffectType.NONE : LedStripEffectType.FADE;
            LedStripCommand.sendEffect(bluetoothServiceLink, effect);

            updateButtons();

            return true;
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
        colorScanButton.setEnabled(true);
        reactToGesturesButton.setEnabled(enabled);

        //Effect buttons:
        effectRainbowButton.setEnabled(enabled);
        effectTrailButton.setEnabled(enabled);
        effectCircleButton.setEnabled(enabled);
        effectCompassButton.setEnabled(enabled);
        effectFadeButton.setEnabled(enabled);

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
                reactToGesturesButton.setImageResource(braceletInformation.mode == BraceletMode.GESTURE || braceletInformation.mode == BraceletMode.GESTURE_EFFECT ? R.drawable.ic_waving_hand : R.drawable.ic_waving_hand_off);
            }

            // updateButtons();
        }

        previousBraceletInformation = new BraceletInformation(braceletInformation);
    }

    private void updateButtons() {
        effectRainbowButton.setPressed(false);
        effectTrailButton.setPressed(false);
        effectCircleButton.setPressed(false);
        effectCompassButton.setPressed(false);
        effectFadeButton.setPressed(false);

        switch (bluetoothServiceLink.getBraceletInformation().ledStripEffectCurrent) {
            case RAINBOW:
                effectRainbowButton.setPressed(true);
                break;
            case TRAIL:
                effectTrailButton.setPressed(true);
                break;
            case CIRCLE:
                effectCircleButton.setPressed(true);
                break;
            case COMPASS:
                effectCompassButton.setPressed(true);
                break;
            case FADE:
                effectFadeButton.setPressed(true);
                break;
        }
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
        androidx.camera.core.Camera[] cameras = {null};

        cameraProviderFuture.addListener(() -> {
            try {
                cameraProvider = cameraProviderFuture.get();

                cameras[0] = bindPreview(cameraProvider, previewView);

            } catch (ExecutionException | InterruptedException e) {
                //Should not happen :)
            }
        }, ContextCompat.getMainExecutor(getActivity()));


        //Click event, select color clicked by the user:
        previewView.setOnTouchListener((view, motionEvent) -> {
            if(motionEvent.getAction() == MotionEvent.ACTION_DOWN) {
                int x = (int) motionEvent.getX();
                int y = (int) motionEvent.getY();

                //Grab current image and extract color:
                Bitmap current = previewView.getBitmap();

                //Approach 0:
                //selectedColor = current.getPixel(x, y);

                //Approach 1:
                //selectedColor = Utils.getColorRegionRgb(current, x, y);

                //Approach 2:
                //selectedColor = Utils.getColorRegionHsvMaxV(current, x, y);

                //Approach 3:
                selectedColor = Utils.getColorRgbMapColors(current, x, y);


                currentColorView.setBackgroundColor(selectedColor);
            }

            return false;
        });

        //Register flash button
        Button flashButton = layout.findViewById(R.id.enableFlashButton);
        flashButton.setOnClickListener(view -> {
            Camera camera = cameras[0];

            if(camera != null) {
                //Check current torch status:
                int flashEnabled = camera.getCameraInfo().getTorchState().getValue();

                camera.getCameraControl().enableTorch(flashEnabled <= 0);

                flashButton.setTextColor(flashEnabled <= 0 ? Color.GREEN : Color.RED);
            }
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

    androidx.camera.core.Camera  bindPreview(@NonNull ProcessCameraProvider cameraProvider, PreviewView previewView) {
        Preview preview = new Preview.Builder()
                .build();

        CameraSelector cameraSelector = new CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                .build();

        preview.setSurfaceProvider(previewView.getSurfaceProvider());

        return cameraProvider.bindToLifecycle((LifecycleOwner)this, cameraSelector, preview);
    }
}