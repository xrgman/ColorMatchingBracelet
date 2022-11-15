import serial

ser = serial.Serial('COM3', 115200)
index = 0

while True:
    if ser.readline().startswith(b'gesture'):
        with open("gestures/junk_" + str(index) + ".csv", "wb") as file:
            for _ in range(50):
                file.write(ser.readline())
        index += 1
