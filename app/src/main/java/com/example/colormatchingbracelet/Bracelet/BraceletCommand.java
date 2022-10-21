package com.example.colormatchingbracelet.Bracelet;

import com.example.colormatchingbracelet.bluetooth.IBluetoothService;
import com.example.colormatchingbracelet.bluetooth.MessageType;

public class BraceletCommand {

    public static void sendStatusRequest(IBluetoothService bluetoothService) {
        bluetoothService.sendMessage(MessageType.STATUS, new byte[0]);
    }

    public static void sendModeChange(IBluetoothService bluetoothService, BraceletMode newMode, byte[] additionalData) {
        byte[] data = new byte[1 + (additionalData != null && additionalData.length > 0 ? additionalData.length + 1 : 0)];

        //Adding new mode:
        data[0] = (byte) newMode.getValue();

        //Adding optional additional data:
        if(additionalData != null && additionalData.length > 0) {
            data[1] = (byte) additionalData.length;

            for(int i = 0; i < additionalData.length; i ++) {
                data[2 + i] = additionalData[i];
            }
        };

        bluetoothService.sendMessage(MessageType.MODE, data);

        //Update mode:
        BraceletInformation braceletInformation = bluetoothService.getBraceletInformation();
        braceletInformation.mode = newMode;

        bluetoothService.updateBraceletInformation(braceletInformation);
    }
}
