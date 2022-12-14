package com.example.colormatchingbracelet.bluetooth;

public enum MessageType {
    STATUS(0),
    DEBUG(1),
    LEDSTRIP(2),
    MODE(3),
    CALIBRATE(4),
    ADD_GESTURE(5),
    REMOVE_GESTURE(6);

    private final int value;

    MessageType(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }
}
