package com.example.colormatchingbracelet.Bracelet;

public enum BraceletMode {
    NORMAL(0),
    EFFECT(1),
    GESTURE(2),
    GESTURE_EFFECT(3);

    private final int value;

    BraceletMode(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }

}
