#include <Adafruit_NeoPixel.h>
#include <MPU9250.h>

MPU9250 mpu;
Adafruit_NeoPixel ledstrip(10, 12);

float gesture[150];

void setup() {
  Serial.begin(115200);
  
  Wire.begin();

  MPU9250Setting mpuSetting;
  mpuSetting.accel_fs_sel = ACCEL_FS_SEL::A2G;

  if (!mpu.setup(0x68, mpuSetting)) {
    Serial.println("Failed to initialize MPU");
  }

  pinMode(4, INPUT);

  for (int i = 0; i < 10; i++) {
    ledstrip.setPixelColor(i, 0, 0, 0);
  }

  ledstrip.show();

  Serial.println("Calibrating MPU");

  mpu.calibrateAccelGyro();

  Serial.println("Calibrated MPU");
}

void loop() {
  if (digitalRead(4) == HIGH) {
    for (int i = 0; i < 10; i++) {
      ledstrip.setPixelColor(i, 255, 0, 0);
      ledstrip.show();
      delay(150);
    }

    for (int i = 0; i < 10; i++) {
      ledstrip.setPixelColor(i, 0, 255, 0);
    }

    ledstrip.show();

    unsigned long time;

    for (int i = 0; i < 50; i++) {
      time = millis();

      mpu.update();

      gesture[i * 3] = mpu.getAccX() - mpu.getAccBiasX() / (float) MPU9250::CALIB_ACCEL_SENSITIVITY;
      gesture[i * 3 + 1] = mpu.getAccY() - mpu.getAccBiasY() / (float) MPU9250::CALIB_ACCEL_SENSITIVITY;
      gesture[i * 3 + 2] = mpu.getAccZ() - mpu.getAccBiasZ() / (float) MPU9250::CALIB_ACCEL_SENSITIVITY;


      while (time + 30 > millis());
    }

    for (int i = 0; i < 10; i++) {
      ledstrip.setPixelColor(i, 0, 0, 0);
    }

    ledstrip.show();

    Serial.println("gesture");

    for (int i = 0; i < 50; i++) {
      Serial.print(gesture[i * 3]);
      Serial.print(',');
      Serial.print(gesture[i * 3 + 1]);
      Serial.print(',');
      Serial.println(gesture[i * 3 + 2]);
    }
  }
}
 