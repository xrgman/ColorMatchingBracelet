package com.example.colormatchingbracelet.bluetooth;

public enum MessageType {
    INIT(0),
    STATUS(1),
    DEBUG(2),
    LEDSTRIP(3);

    private final int value;

    MessageType(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }
}
