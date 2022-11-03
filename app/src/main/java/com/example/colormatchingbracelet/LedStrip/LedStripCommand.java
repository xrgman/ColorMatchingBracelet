package com.example.colormatchingbracelet.LedStrip;

import com.example.colormatchingbracelet.Bracelet.BraceletCommand;
import com.example.colormatchingbracelet.Bracelet.BraceletInformation;
import com.example.colormatchingbracelet.Bracelet.BraceletMode;
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

        //Change local power state:
        BraceletInformation braceletInformation = bluetoothService.getBraceletInformation();
        braceletInformation.ledStripPowerState = powerState;

        bluetoothService.updateBraceletInformation(braceletInformation);
    }

    public static void sendBrightnessLevel(IBluetoothService bluetoothService, int brightness) {
        byte[] data = new byte[2];

        //Adding type of led strip command:
        data[0] = (byte) LedStripCommandType.BRIGHTNESS.getValue();

        //Adding power state:
        data[1] = (byte) brightness;

        bluetoothService.sendMessage(MessageType.LEDSTRIP, data);

        //Change local brightness:
        BraceletInformation braceletInformation = bluetoothService.getBraceletInformation();
        braceletInformation.ledStripBrightness = brightness;

        bluetoothService.updateBraceletInformation(braceletInformation);
    }

    public static void sendEffect(IBluetoothService bluetoothService, LedStripEffectType type) {
        byte[] data = new byte[1];
        BraceletMode newMode = type == LedStripEffectType.NONE ? BraceletMode.NORMAL : BraceletMode.EFFECT;

        //Adding effect type:
        data[0] = (byte) type.getValue();

        BraceletCommand.sendModeChange(bluetoothService, newMode, data);

        //Change current effect:
        BraceletInformation braceletInformation = bluetoothService.getBraceletInformation();
        braceletInformation.ledStripEffectCurrent = type;

        bluetoothService.updateBraceletInformation(braceletInformation);
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
