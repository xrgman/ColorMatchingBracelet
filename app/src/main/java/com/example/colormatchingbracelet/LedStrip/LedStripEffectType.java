package com.example.colormatchingbracelet.LedStrip;

public enum LedStripEffectType {
    NONE(0),
    RAINBOW(1),
    TRAIL(2),
    CIRCLE(3),
    COMPASS(4),
    TEMPERATURE(5);

    private final int value;

    LedStripEffectType(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }
}


