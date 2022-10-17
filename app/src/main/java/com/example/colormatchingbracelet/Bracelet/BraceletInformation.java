package com.example.colormatchingbracelet.Bracelet;

import android.graphics.Color;

import com.example.colormatchingbracelet.LedStrip.LedStripEffectType;

import java.util.List;

public class BraceletInformation {
    public int batteryPercentage;
    public boolean ledStripPowerState;
    public int ledStripBrightness; //Value from 0-100 to save space
    public LedStripEffectType ledStripEffectCurrent;

    public List<Color> ledStripColors;
}
