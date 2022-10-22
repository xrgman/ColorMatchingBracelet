package com.example.colormatchingbracelet.bluetooth;

public enum MessageType {
    STATUS(0),
    DEBUG(1),
    LEDSTRIP(2),
    MODE(3);

    private final int value;

    MessageType(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }
}
