package com.example.colormatchingbracelet.LedStrip;

import com.example.colormatchingbracelet.bluetooth.IBluetoothService;
import com.example.colormatchingbracelet.bluetooth.MessageType;


public class LedStripCommand {


    public static void sendPowerMessage(IBluetoothService bluetoothService, boolean powerState) {
        byte[] data = new byte[2];

        //Adding type of led strip command:
        data[0] = (byte) LedStripCommandType.POWER.getValue();

        //Adding power state:
        data[1] = (byte) (powerState ? 1 : 0);

        bluetoothService.sendMessage(MessageType.LEDSTRIP, data);
    }

    public static void sendBrightnessLevel(IBluetoothService bluetoothService, int brightness) {
        byte[] data = new byte[2];

        //Adding type of led strip command:
        data[0] = (byte) LedStripCommandType.BRIGHTNESS.getValue();

        //Adding power state:
        data[1] = (byte) brightness;

        bluetoothService.sendMessage(MessageType.LEDSTRIP, data);
    }

    public static void sendEffect(IBluetoothService bluetoothService, LedStripEffectType type) {
        byte[] data = new byte[2];

        //Adding type of led strip command:
        data[0] = (byte) LedStripCommandType.EFFECT.getValue();

        //Adding effect type:
        data[1] = (byte) type.getValue();

        bluetoothService.sendMessage(MessageType.LEDSTRIP, data);
    }

    //For whole strip at once:
    public static void sendColor(IBluetoothService bluetoothService, int color) {
        byte[] data = new byte[5];

        //Adding type of led strip command:
        data[0] = (byte) LedStripCommandType.COLOR.getValue();

        //Adding color to set:
        data[1] = (byte) (color >> 24);
        data[2] = (byte) ((color >> 16) & 0xFF);
        data[3] = (byte) ((color >> 8) & 0xFF);
        data[4] = (byte) (color & 0xFF);

        bluetoothService.sendMessage(MessageType.LEDSTRIP, data);
    }
}
