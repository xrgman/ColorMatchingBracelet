package com.example.colormatchingbracelet.LedStrip;

public enum LedStripEffectType {
    NONE(0),
    RAINBOW(1),
    CIRCLE(2),
    FADE(3);

    private final int value;

    LedStripEffectType(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }
}


