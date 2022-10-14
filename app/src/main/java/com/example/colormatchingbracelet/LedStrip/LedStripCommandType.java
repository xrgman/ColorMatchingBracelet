package com.example.colormatchingbracelet.LedStrip;

public enum LedStripCommandType {
    POWER(0),
    COLOR(1),
    BRIGHTNESS(2),
    EFFECT(3);

    private final int value;

    LedStripCommandType(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }
}