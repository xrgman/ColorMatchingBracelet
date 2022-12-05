package com.example.colormatchingbracelet.Bracelet;

import android.graphics.Color;

import com.example.colormatchingbracelet.LedStrip.LedStripEffectType;

import java.util.List;

public class BraceletInformation {
    public BraceletMode mode;
    public int batteryPercentage;
    public boolean ledStripPowerState;
    public int ledStripBrightness;
    public LedStripEffectType ledStripEffectCurrent;
    public int numGestures;

    public List<Color> ledStripColors;

    public BraceletInformation() {
        mode = BraceletMode.NORMAL;
        batteryPercentage = -1;
        ledStripPowerState = false;
        ledStripBrightness = 255;
        ledStripEffectCurrent = LedStripEffectType.NONE;
        numGestures = 0;
    }

    public BraceletInformation(BraceletInformation old) {
        mode = old.mode;
        batteryPercentage = old.batteryPercentage;
        ledStripPowerState = old.ledStripPowerState;
        ledStripBrightness = old.ledStripBrightness;
        ledStripEffectCurrent = old.ledStripEffectCurrent;
        numGestures = old.numGestures;
    }
}
