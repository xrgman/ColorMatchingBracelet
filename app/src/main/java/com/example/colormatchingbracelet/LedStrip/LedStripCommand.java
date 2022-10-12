package com.example.colormatchingbracelet.LedStrip;

public enum LedStripCommand {
    POWER(0),
    COLOR(1);

    private final int value;

    LedStripCommand(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }
}