package com.example.colormatchingbracelet.LedStrip;

import com.example.colormatchingbracelet.bluetooth.BluetoothService;
import com.example.colormatchingbracelet.bluetooth.MessageType;


public class LedStripControll {


    public static void sendPowerMessage(BluetoothService bluetoothService, boolean powerState) {
        String message = "";

        //Adding type of ledstrip command:
        message += (char) LedStripCommand.POWER.getValue();

        //Adding power state:
        message += powerState;

        bluetoothService.sendMessage(MessageType.LEDSTRIP, message);
    }
}
