package com.example.colormatchingbracelet.LedStrip;

import com.example.colormatchingbracelet.bluetooth.IBluetoothService;
import com.example.colormatchingbracelet.bluetooth.MessageType;


public class LedStripCommand {


    public static void sendPowerMessage(IBluetoothService bluetoothService, boolean powerState) {
        String message = "";

        //Adding type of led strip command:
        message += (char) LedStripCommandType.POWER.getValue();

        //Adding power state:
        message += powerState ? 1 : 0;

        bluetoothService.sendMessage(MessageType.LEDSTRIP, message);
    }

    public static void sendBrightnessLevel(IBluetoothService bluetoothService, int brightness) {
        String message = "";

        //Adding type of led strip command:
        message += (char) LedStripCommandType.BRIGHTNESS.getValue();

        //Adding power state:
        message += (char) brightness;

        bluetoothService.sendMessage(MessageType.LEDSTRIP, message);
    }

    public static void sendEffect(IBluetoothService bluetoothService, LedStripEffectType type) {
        String message = "";

        //Adding type of led strip command:
        message += (char) LedStripCommandType.EFFECT.getValue();

        //Adding effect type:
        message += (char) type.getValue();

        bluetoothService.sendMessage(MessageType.LEDSTRIP, message);
    }
}
