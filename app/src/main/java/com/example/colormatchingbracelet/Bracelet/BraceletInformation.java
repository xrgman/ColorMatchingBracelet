package com.example.colormatchingbracelet.Bracelet;

import android.graphics.Color;

import com.example.colormatchingbracelet.LedStrip.LedStripEffectType;

import java.util.List;

public class BraceletInformation {
    public int batteryPercentage;
    public boolean ledStripPowerState;
    public int ledStripBrightness;
    public LedStripEffectType ledStripEffectCurrent;

    public List<Color> ledStripColors;
}
