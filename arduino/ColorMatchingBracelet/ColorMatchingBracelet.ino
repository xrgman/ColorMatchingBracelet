#include <EEPROM.h>

#include <BLEDevice.h>
#include <BLEServer.h>
#include <BLEUtils.h>
#include <BLE2902.h>

#include <MPU9250.h> // github.com/hideakitai/MPU9250

#include <Adafruit_NeoPixel.h>

#define EEPROM_ADDR_ACC_BIAS_X 0x00
#define EEPROM_ADDR_ACC_BIAS_Y 0x04
#define EEPROM_ADDR_ACC_BIAS_Z 0x08

#define BLE_NAME "Color Matching Bracelet"
#define BLE_SERVICE_UUID "1cf4fab1-d642-4153-a6f2-bf40db8d6f73"
#define BLE_CHARACTERISTIC_UUID_RX "75eb965e-a1e1-4b1d-8bb9-91e562cdb144"
#define BLE_CHARACTERISTIC_UUID_TX "aba19161-392b-4bed-9450-3a238abd0040"

#define MPU_I2C_ADDR 0x68

#define LED_STRIP_PIN 12
#define LED_STRIP_NUM_LEDS 10

#define GESTURE_ACC_THRESHOLD 2.0
#define GESTURE_DURATION_MS 1500
#define GESTURE_LENGTH 50
#define GESTURE_INTERVAL_MS (GESTURE_DURATION_MS / GESTURE_LENGTH)

enum MessageType {
  MESSAGE_STATISTICS,
  MESSAGE_DEBUG,
  MESSAGE_LED_STRIP,
  MESSAGE_MODE,
  MESSAGE_CALIBRATE,
  _NUM_MESSAGE_TYPES
};

enum Mode {
  MODE_NORMAL,
  MODE_EFFECT,
  MODE_EFFECT_NO_COLOR_CHANGE,
  MODE_GESTURE
};

enum LedStripCommandType {
  LED_STRIP_COMMAND_POWER,
  LED_STRIP_COMMAND_BRIGHTNESS,
  LED_STRIP_COMMAND_COLOR
};

enum LedStripEffectType {
  LED_STRIP_EFFECT_NONE,
  LED_STRIP_EFFECT_RAINBOW,
  LED_STRIP_EFFECT_TRAIL,
  LED_STRIP_EFFECT_CIRCLE,
  LED_STRIP_EFFECT_COMPASS,
  LED_STRIP_EFFECT_TEMPERATURE
};

BLEServer* bleServer;
BLECharacteristic* bleTxCharacteristic;
 bool bleIsAdvertising = false;
bool bleDeviceConnected = false;

MPU9250 mpu;

Adafruit_NeoPixel ledStrip(LED_STRIP_NUM_LEDS, LED_STRIP_PIN, NEO_GRB + NEO_KHZ800);
bool ledStripPower;
uint8_t ledStripBrightness = 60;
uint8_t ledStripColor[3] = {255, 255, 255};
LedStripEffectType ledStripEffect = LED_STRIP_EFFECT_NONE;

uint8_t gesture_length = 0;
uint32_t gesture_last_interval_ms;
float gesture[150];

//Battery status:
uint8_t batteryPercentage;

//Storing current mode:
Mode currentMode = MODE_NORMAL;

void calibrateAcc();
void processLedStripCommand(uint8_t* data, uint8_t dataLength);
void sendStatistics();

class BleServerCallbacks : public BLEServerCallbacks {
  void onConnect(BLEServer* bleServer) {
    bleIsAdvertising = false;
    bleDeviceConnected = true;

    Serial.println("BLE device connected");
  };

  void onDisconnect(BLEServer* bleServer) {
    bleDeviceConnected = false;

    Serial.println("BLE device disconnected");
  }
};

class BleCharacteristicCallbacks : public BLECharacteristicCallbacks {
  void onWrite(BLECharacteristic *pCharacteristic) {
    uint8_t* data = pCharacteristic->getData();
    uint8_t dataSize = pCharacteristic->getLength();

    if (dataSize < 3) {
      Serial.println("Received message with invalid size: " + dataSize);
      return;
    }

    if (data[0] != 63) {
      Serial.println("Received message with invalid start character: " + data[0]);
      return;
    }

    uint8_t messageType = data[1];

    if (messageType >= _NUM_MESSAGE_TYPES) {
      Serial.println("Received message with invalid type: " + messageType);
      return;
    }

    uint8_t checksum = data[dataSize - 1];
    uint8_t actualChecksum = 0;

    for(int i = 0; i < dataSize - 1; i ++) {
      actualChecksum ^= data[i];
    }

    if(actualChecksum != checksum) {
      Serial.println("Invalid checksum, dropping message.");
      return;
    }

    uint8_t payloadSize = data[2];
    uint8_t* payload = &data[3];

    switch (messageType) {
      case MESSAGE_STATISTICS: {
        sendStatistics();          
      } break;
      case MESSAGE_DEBUG: {
        Serial.print("Debug message: ");

        for (int i = 0; i < payloadSize; i++) {
          Serial.print((char) payload[i]);
        }

        Serial.println("");

      } break;
      case MESSAGE_LED_STRIP: {
        processLedStripCommand(payload, payloadSize);
      } break;
      case MESSAGE_MODE: {
        Mode newMode = (Mode) payload[0];

        Serial.print("Mode changed from ");
        Serial.print(currentMode);
        Serial.print(" to ");
        Serial.println(newMode);

        currentMode = newMode;

        if (newMode == MODE_EFFECT || newMode == MODE_EFFECT_NO_COLOR_CHANGE) {        
          ledStripEffect = (LedStripEffectType) payload[2];
        }
      } break;
      case MESSAGE_CALIBRATE: {
        calibrateAcc();
      } break;   
    }      
  }
};

void setup() {
  Serial.begin(115200);

  setupLedStrip();
  setupMpu();
  setupBle();

  //Settings variables:
  ledStripPower = false;
  batteryPercentage = 90;  //Dummy for now :)
}

void setupLedStrip() {
  ledStrip.begin();
  ledStrip.setBrightness(ledStripBrightness);
  setLedStripColor(0, 0, 0);
  ledStrip.show();
}

void setupMpu() {
  Wire.begin();

  if (!mpu.setup(MPU_I2C_ADDR)) {
    panic("Failed to initialize MPU");
  }

  float accBiasX;
  float accBiasY;
  float accBiasZ;
  
  EEPROM.get(EEPROM_ADDR_ACC_BIAS_X, accBiasX);
  EEPROM.get(EEPROM_ADDR_ACC_BIAS_Y, accBiasY);
  EEPROM.get(EEPROM_ADDR_ACC_BIAS_Z, accBiasZ);

  mpu.setAccBias(accBiasX, accBiasY, accBiasZ);
}

void setupBle() {
  BLEDevice::init(BLE_NAME);

  bleServer = BLEDevice::createServer();
  bleServer->setCallbacks(new BleServerCallbacks());

  BLEService* service = bleServer->createService(BLE_SERVICE_UUID);

  bleTxCharacteristic = service->createCharacteristic(BLE_CHARACTERISTIC_UUID_TX, BLECharacteristic::PROPERTY_NOTIFY);
  bleTxCharacteristic->addDescriptor(new BLE2902());

  BLECharacteristic* rxCharacteristic = service->createCharacteristic(BLE_CHARACTERISTIC_UUID_RX, BLECharacteristic::PROPERTY_WRITE);
  rxCharacteristic->setCallbacks(new BleCharacteristicCallbacks());

  service->start();

  bleStartAdvertising();
}

void panic(const String& message) {
  Serial.println(message);
  while (1);
}

void loop() {
  if (!updateBle()) {
    updateFadeEffect();
  } else {
    mpu.update();

    if (currentMode == MODE_GESTURE) {
      updateGesture();
    }

    updateCurrentEffect();
  }  
}

bool updateBle() {
  if (!bleDeviceConnected) {
    if (!bleIsAdvertising) {
      delay(500);
      bleStartAdvertising();
    }

    return false;
  }

  return true;
}

void updateGesture() {
  float accX = mpu.getAccX() - mpu.getAccBiasX() / (float) MPU9250::CALIB_ACCEL_SENSITIVITY;
  float accY = mpu.getAccY() - mpu.getAccBiasY() / (float) MPU9250::CALIB_ACCEL_SENSITIVITY;
  float accZ = mpu.getAccZ() - mpu.getAccBiasZ() / (float) MPU9250::CALIB_ACCEL_SENSITIVITY;

  if (gesture_length == 0 && (abs(accX) >= GESTURE_ACC_THRESHOLD || abs(accY) >= GESTURE_ACC_THRESHOLD || abs(accZ) >= GESTURE_ACC_THRESHOLD)) {
    gesture[gesture_length * 3] = accX;
    gesture[gesture_length * 3 + 1] = accY;
    gesture[gesture_length * 3 + 2] = accZ;
    gesture_length++;
    gesture_last_interval_ms = millis();

    Serial.println("Recording gesture...");
  }

  if (gesture_length > 0 && gesture_last_interval_ms + GESTURE_INTERVAL_MS <= millis()) {
    gesture[gesture_length * 3] = accX;
    gesture[gesture_length * 3 + 1] = accY;
    gesture[gesture_length * 3 + 2] = accZ;
    gesture_length++;
    gesture_last_interval_ms = millis();
  }

  if (gesture_length == GESTURE_LENGTH) {
    gesture_length = 0;

    Serial.println("Recorded gesture");
  }
}

void bleStartAdvertising() {
  bleServer->startAdvertising();
  bleIsAdvertising = true;

  Serial.println("Waiting for BLE device connection");
}

void ui32_to_ui8(uint32_t source, uint8_t *dest) {
	dest[0] = (uint8_t)(source >> 24);
	dest[1] = (uint8_t)((source >> 16) & 0xFF);
	dest[2] = (uint8_t)((source >> 8) & 0xFF);
	dest[3] = (uint8_t)(source & 0xFF);
}

uint32_t to_ui32(uint8_t *pData) {
	uint32_t ret = 0;
	ret |= (pData[0] << 24);
	ret |= (pData[1] << 16);
	ret |= (pData[2] << 8);
	ret |= pData[3];

	return ret;
}

void sendMessage(MessageType type, uint8_t *dataToSend, uint8_t data_size) {
  uint8_t checkSum = 0;
  uint8_t size = 4 + data_size;
  uint8_t message[size];

  //Start character:
  message[0] = (uint8_t) '?';
  checkSum ^= '?';

  //Message type:
  message[1] = type;
  checkSum ^= type;

  //Message length:
  message[2] = data_size;
  checkSum ^= data_size;

  //Message content:
  for(int i = 0; i < data_size; i++) {
    message[3 + i] = dataToSend[i];    
    checkSum ^= dataToSend[i];
  }

  message[size - 1] = checkSum;

  bleTxCharacteristic->setValue(message, size);
  bleTxCharacteristic->notify();
}

void sendStatistics() {
  uint8_t size = 6;
  uint8_t dataToSend[size];

  dataToSend[0] = currentMode;
  dataToSend[1] = batteryPercentage;
  dataToSend[2] = (uint8_t) (ledStripPower ? '1' : '0'); 
  dataToSend[3] = ledStripEffect;
  dataToSend[4] = ledStripBrightness;

  //Colors todo dit ff nakijken:
  dataToSend[5] = LED_STRIP_NUM_LEDS;  //First we send length
  
  sendMessage(MESSAGE_STATISTICS, dataToSend, size);
}

void calibrateAcc() {
  Serial.println("Calibrating accelerometer in 2 seconds...");

  setLedStripColor(255, 0, 0);
  ledStrip.show();

  for (int i = 0; i < LED_STRIP_NUM_LEDS; i++) {
    ledStrip.setPixelColor(i, 0, 255, 0);
    ledStrip.show();
    delay(1500 / LED_STRIP_NUM_LEDS);
  }

  mpu.verbose(true);
  mpu.calibrateAccelGyro();
  mpu.verbose(false);

  setLedStripColor(ledStripColor[0], ledStripColor[1], ledStripColor[2]);
  ledStrip.show();

  Serial.println("Accelerometer is calibrated");

  EEPROM.put(EEPROM_ADDR_ACC_BIAS_X, mpu.getAccBiasX());
  EEPROM.put(EEPROM_ADDR_ACC_BIAS_Y, mpu.getAccBiasY());
  EEPROM.put(EEPROM_ADDR_ACC_BIAS_Z, mpu.getAccBiasZ());
}

bool canModeChangeColor(Mode mode) {
  return mode != MODE_EFFECT_NO_COLOR_CHANGE;
}

void processLedStripCommand(uint8_t* data, uint8_t dataLength) {
  int type = data[0];

  Serial.print("Processing ledstrip of type: ");
  Serial.println(type);

  switch (type) {
    case LED_STRIP_COMMAND_POWER: {
      bool power = data[1] == 1;
      setLedStripPower(power);
    } break;
    case LED_STRIP_COMMAND_BRIGHTNESS: {
      ledStripBrightness = (uint8_t) (((float) data[1] / 100.0) * 255.0);

      Serial.print("Brightness: ");
      Serial.println(ledStripBrightness);

      ledStrip.setBrightness(ledStripBrightness);
    } break;
    case LED_STRIP_COMMAND_COLOR: {
      memcpy(ledStripColor, &data[2], 3);
    } break;         
  }
}

void setLedStripPower(bool power) {
  ledStripPower = power;

  if (power && ledStripEffect == LED_STRIP_EFFECT_NONE) {
    setLedStripColor(ledStripColor[0], ledStripColor[1], ledStripColor[2]);
  } else if (!power) {
    setLedStripColor(0, 0, 0);
  }

  ledStrip.show();
}

void setLedStripColor(uint8_t r, uint8_t g, uint8_t b) {
  for(int i = 0; i < LED_STRIP_NUM_LEDS; i++) {
    ledStrip.setPixelColor(i, r, g, b);
  }
}

//**********************
// LedStrip effects
//**********************

void updateCurrentEffect() {
  if (!ledStripPower) {
    return;
  }

  switch (ledStripEffect) {
    case LED_STRIP_EFFECT_NONE: {
      setLedStripColor(ledStripColor[0], ledStripColor[1], ledStripColor[2]);
      ledStrip.show();
    } break;
    case LED_STRIP_EFFECT_RAINBOW: {
      updateRainbowEffect();
    } break;
    case LED_STRIP_EFFECT_TRAIL: {
      updateTrailEffect();
    } break;
    case LED_STRIP_EFFECT_CIRCLE: {
      updateCircleEffect();  
    } break;
    case LED_STRIP_EFFECT_COMPASS: {
      updateCompassEffect();
    } break;
    case LED_STRIP_EFFECT_TEMPERATURE: {
      updateTemperatureEffect();
    } break;
  }
}

void updateFadeEffect() {
  static int16_t index = 0;
  static uint32_t last_interval_time = 0;

  if (millis() >= last_interval_time + 20) {
    float brightness = 1.0 - ((float) abs(128 - index) / 128.0);
    brightness = brightness * brightness * brightness;
    uint8_t r = (uint8_t) ((float) ledStripColor[0] * brightness);
    uint8_t g = (uint8_t) ((float) ledStripColor[1] * brightness);
    uint8_t b = (uint8_t) ((float) ledStripColor[2] * brightness);

    setLedStripColor(r, g, b);
    ledStrip.show();

    index = (index + 1) % 256;
    last_interval_time = millis();
  }
}

void updateRainbowEffect() {
  static uint8_t pos = 0;
  static uint32_t last_interval_time = 0;

  if (millis() >= last_interval_time + 20) {
    for (uint8_t i = 0; i < LED_STRIP_NUM_LEDS; i++) {
      uint8_t wheelPos = ((i * 256 / LED_STRIP_NUM_LEDS) + pos) & 255;
      wheelPos = 255 - wheelPos;

      if (wheelPos < 85) {
        ledStrip.setPixelColor(i, 255 - wheelPos * 3, 0, wheelPos * 3);
      } else if (wheelPos < 170) {
        wheelPos -= 85;
        ledStrip.setPixelColor(i, 0, wheelPos * 3, 255 - wheelPos * 3);
      } else {
        wheelPos -= 170;
        ledStrip.setPixelColor(i, wheelPos * 3, 255 - wheelPos * 3, 0);
      }
    }

    ledStrip.show();

    pos = pos >= 256 ? 0 : pos + 1;
    last_interval_time = millis();
  }
}

void updateTrailEffect() {
  static uint32_t last_interval_time = 0;
  static float brightness = 0.0f;

  float accX = mpu.getAccX() - mpu.getAccBiasX() / (float) MPU9250::CALIB_ACCEL_SENSITIVITY;
  float accY = mpu.getAccY() - mpu.getAccBiasY() / (float) MPU9250::CALIB_ACCEL_SENSITIVITY;
  float accZ = mpu.getAccZ() - mpu.getAccBiasZ() / (float) MPU9250::CALIB_ACCEL_SENSITIVITY;

  if (abs(accX) > 1.5 || abs(accY) > 1.5 || abs(accZ) > 1.5) {
    brightness = sqrt(abs(accX) + abs(accY) + abs(accZ));
  }

  if (millis() >= last_interval_time + 20) {
    if (brightness > 0.0f) {
      float clampedBrightness = brightness;

      if (clampedBrightness > 1.0f) {
        clampedBrightness = 1.0f;
      }

      uint8_t r = (uint8_t) ((float) ledStripColor[0] * clampedBrightness);
      uint8_t g = (uint8_t) ((float) ledStripColor[1] * clampedBrightness);
      uint8_t b = (uint8_t) ((float) ledStripColor[2] * clampedBrightness);

      setLedStripColor(r, g, b);

      brightness -= 0.05f;

      if (brightness < 0.0f) {
        brightness = 0.0f;
      }

      last_interval_time = millis();
    } else {
      setLedStripColor(0, 0, 0);
    }

    ledStrip.show();
  }
}

void updateCircleEffect() {
  static int8_t pos = 0;
  static uint32_t last_interval_time = 0;

  if (millis() >= last_interval_time + 100) {
    for (int8_t i = 0; i < LED_STRIP_NUM_LEDS; i++) {
      int8_t distance = pos >= i ? pos - i : pos + (LED_STRIP_NUM_LEDS - i); 
      float brightness = 1.0 - ((float) distance / (float) LED_STRIP_NUM_LEDS);
      brightness = brightness * brightness * brightness;

      uint8_t r = (uint8_t) ((float) ledStripColor[0] * brightness);
      uint8_t g = (uint8_t) ((float) ledStripColor[1] * brightness);
      uint8_t b = (uint8_t) ((float) ledStripColor[2] * brightness);

      ledStrip.setPixelColor(i, r, g, b);
    }

    ledStrip.show();

    pos = pos >= LED_STRIP_NUM_LEDS ? 0 : pos + 1;
    last_interval_time = millis();
  }
}

void updateCompassEffect() {
  float magX = mpu.getMagX();
  float magY = mpu.getMagY();
  float angle = atan2(magX, magY);
  float southiness = abs(angle) / 3.15f;

  uint8_t r = 255;
  uint8_t g = (uint8_t) (southiness * 255.0f);
  uint8_t b = (uint8_t) (southiness * 255.0f);
  setLedStripColor(r, g, b);
  ledStrip.show();
}

void updateTemperatureEffect() {
  Serial.println(mpu.getTemperature());
}
