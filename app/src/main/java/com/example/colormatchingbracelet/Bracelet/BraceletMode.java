package com.example.colormatchingbracelet.Bracelet;

public enum BraceletMode {
    NORMAL(0),
    EFFECT(1),
    EFFECT_NO_COLOR_CHANGE(2),
    GESTURE(3);

    private final int value;

    BraceletMode(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }

    public boolean canChangeColor() {
        return value == 0 ||
                value == 1 ||
                value == 4;
    }
}
