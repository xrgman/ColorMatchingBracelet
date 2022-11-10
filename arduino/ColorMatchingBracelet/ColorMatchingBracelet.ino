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
#define LED_STRIP_MAX_BRIGHTNESS 30

#define VOLTAGE_SENSOR_PIN 25

#define GESTURE_ACC_THRESHOLD 1.5
#define GESTURE_DURATION_MS 1500
#define GESTURE_LENGTH 50
#define GESTURE_SIZE (3 * GESTURE_LENGTH) 
#define GESTURE_INTERVAL_MS (GESTURE_DURATION_MS / GESTURE_LENGTH)

#define RECORDED_GESTURES_MAX 128

enum MessageType {
  MESSAGE_STATISTICS,
  MESSAGE_DEBUG,
  MESSAGE_LED_STRIP,
  MESSAGE_MODE,
  MESSAGE_CALIBRATE,
  MESSAGE_ADD_GESTURE,
  MESSAGE_REMOVE_GESTURE,
  _NUM_MESSAGE_TYPES
};

enum Mode {
  MODE_NORMAL,
  MODE_EFFECT,
  MODE_GESTURE,
  MODE_GESTURE_EFFECT
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
  LED_STRIP_EFFECT_FADE,
  _NUM_LED_STRIP_EFFECTS
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

uint8_t gestureLength = 0;
float gesture[GESTURE_SIZE];
uint32_t gestureLastIntervalTime = 0; 

uint8_t recordedGesturesLength = 0;
float recordedGestures[RECORDED_GESTURES_MAX][GESTURE_SIZE];
LedStripEffectType recordedGesturesEffects[RECORDED_GESTURES_MAX];

//Battery status:
uint8_t batteryPercentage;

//Storing current mode:
Mode currentMode = MODE_NORMAL;

bool shouldRecordGesture = false;
bool shouldCalibrate = false;

/* Bluetooth Callbacks */

void calibrateAcc();
void recordGesture();
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

        if (newMode == MODE_EFFECT || newMode == MODE_GESTURE_EFFECT) {        
          ledStripEffect = (LedStripEffectType) payload[2];
        } else if (newMode == MODE_NORMAL || newMode == MODE_GESTURE) {
          ledStripEffect = LED_STRIP_EFFECT_NONE;
        }
      } break;
      case MESSAGE_CALIBRATE: {
        shouldCalibrate = true;
      } break;   
      case MESSAGE_ADD_GESTURE: {
        shouldRecordGesture = true;
        recordedGesturesEffects[recordedGesturesLength] = (LedStripEffectType) payload[0];
      } break;
      case MESSAGE_REMOVE_GESTURE: {

      } break;
    }      
  }
};

/* Main */

void setup() {
  Serial.begin(115200);

  setupMpu();
  setupBle();

  //Settings variables:
  ledStripPower = false;
  batteryPercentage = 90;  //Dummy for now :)

  ledStrip.setBrightness(LED_STRIP_MAX_BRIGHTNESS);
}

void panic(const String& message) {
  Serial.println(message);
  while (1);
}

void loop() {
  if (!updateBle()) {
    setLedStripColor(0, 0, 0);
    ledStrip.show();
  } else {
    mpu.update();

    if (shouldCalibrate) {
      shouldCalibrate = false;
      calibrateAcc();
    } else if (shouldRecordGesture) {
      shouldRecordGesture = false;
      recordGesture();
    } else {
      if (currentMode == MODE_GESTURE || currentMode == MODE_GESTURE_EFFECT) {
        updateGesture();
      }

      updateCurrentEffect();
    }
  }  
}

/* Bluetooth and Messaging */

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

/* MPU */

void calibrateAcc() {
  ledStripCountDown("Calibrating accelerometer");

  setLedStripColor(0, 255, 0);
  ledStrip.show();

  mpu.verbose(true);
  mpu.calibrateAccelGyro();
  mpu.verbose(false);

  Serial.println("Accelerometer is calibrated");

  EEPROM.put(EEPROM_ADDR_ACC_BIAS_X, mpu.getAccBiasX());
  EEPROM.put(EEPROM_ADDR_ACC_BIAS_Y, mpu.getAccBiasY());
  EEPROM.put(EEPROM_ADDR_ACC_BIAS_Z, mpu.getAccBiasZ());

  setLedStripColor(0, 0, 0);
  ledStrip.show();

  delay(500);
}

void setupMpu() {
  Wire.begin();

  MPU9250Setting setting;
  setting.accel_fs_sel = ACCEL_FS_SEL::A2G;

  if (!mpu.setup(MPU_I2C_ADDR, setting)) {
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

/* Gesture Recognition */

void updateGesture() {
  float accX = mpu.getAccX() - mpu.getAccBiasX() / (float) MPU9250::CALIB_ACCEL_SENSITIVITY;
  float accY = mpu.getAccY() - mpu.getAccBiasY() / (float) MPU9250::CALIB_ACCEL_SENSITIVITY;
  float accZ = mpu.getAccZ() - mpu.getAccBiasZ() / (float) MPU9250::CALIB_ACCEL_SENSITIVITY;

  if (gestureLength == 0 && (abs(accX) >= GESTURE_ACC_THRESHOLD || abs(accY) >= GESTURE_ACC_THRESHOLD || abs(accZ) >= GESTURE_ACC_THRESHOLD)) {
    gesture[0] = accX;
    gesture[1] = accY;
    gesture[2] = accZ;
    gestureLength++;
    gestureLastIntervalTime = millis();

    Serial.println("Recording gesture...");
  }

  if (gestureLength > 0 && gestureLastIntervalTime + GESTURE_INTERVAL_MS <= millis()) {
    gesture[gestureLength * 3] = accX;
    gesture[gestureLength * 3 + 1] = accY;
    gesture[gestureLength * 3 + 2] = accZ;
    gestureLength++;
    gestureLastIntervalTime = millis();
  }

  if (gestureLength == GESTURE_LENGTH) {
    gestureLength = 0;

    classifyGesture();
  }
}

void classifyGesture() {
  Serial.println("Classifying gesture...");

  float smallestMean = 0.35;
  LedStripEffectType effect = ledStripEffect;

  for (int i = 0; i < recordedGesturesLength; i++) {
    float mean = dynamicTimeWarping(gesture, recordedGestures[i]);

    Serial.print("Mean: ");
    Serial.println(mean);

    if (mean < smallestMean) {
      smallestMean = mean;
      effect = recordedGesturesEffects[i];
    }
  }

  if (smallestMean < 0.35) {
    ledStripEffect = effect;
  }
}

float dynamicTimeWarping(float* x, float* y) { 
  static float dtw[GESTURE_LENGTH][GESTURE_LENGTH];

  dtw[0][0] = distance(x, y, 0, 0);

  for (int i = 1; i < GESTURE_LENGTH; i++) {
    dtw[i][0] = distance(x, y, i, 0) + dtw[i - 1][0];
    dtw[0][i] = distance(x, y, 0, i) + dtw[0][i - 1];
  }

  for (int i = 1; i < GESTURE_LENGTH; i++) {
    for (int j = 1; j < GESTURE_LENGTH; j++) {
      dtw[i][j] = distance(x, y, i, j) + min(dtw[i - 1][j], min(dtw[i][j - 1], dtw[i - 1][j - 1]));
    }
  }

  int i = GESTURE_LENGTH - 1;
  int j = GESTURE_LENGTH - 1;
  float distance[GESTURE_LENGTH * 2];
  int length = 0;

  while (i > 0 && j > 0) {
    if (dtw[i - 1][j] <= dtw[i][j - 1] && dtw[i - 1][j] <= dtw[i - 1][j - 1]) {
      distance[length++] = dtw[i][j] - dtw[--i][j];
    } else if (dtw[i][j - 1] < dtw[i - 1][j - 1]) {
      distance[length++] = dtw[i][j] - dtw[i][--j];
    } else {
      distance[length++] = dtw[i][j] - dtw[--i][--j];
    }
  }

  while (i > 0) {
    distance[length++] = dtw[i][0] - dtw[--i][0];
  }

  while (j > 0) {
    distance[length++] = dtw[0][j] - dtw[0][--j];
  }

  distance[length++] = dtw[0][0];

  float mean = 0;

  for (int i = 0; i < length; i++) {
    mean += distance[i];
  }

  mean = mean / (float) length;

  return mean;
}

float distance(float* x, float* y, int i, int j) {
  float axi = x[i];
  float ayi = x[i + 1];
  float azi = x[i + 2];
  float axj = y[j];
  float ayj = y[j + 1];
  float azj = y[j + 2];
  float dir = (axi * axj + ayi * ayj + azi * azj) / (normalize(axi, ayi, azi) * normalize(axj, ayj, azj) + 0.0000001);

  return (1.0 - 0.5 * dir) * normalize(axi - axj, ayi - ayj, azi - azj);
}

float normalize(float x, float y, float z) {
  return sqrt(x * x + y * y + z * z);
}

void recordGesture() {
  ledStripCountDown("Recording new gesture");

  while (1) {
    if (mpu.update()) {
      float accX = mpu.getAccX() - mpu.getAccBiasX() / (float) MPU9250::CALIB_ACCEL_SENSITIVITY;
      float accY = mpu.getAccY() - mpu.getAccBiasY() / (float) MPU9250::CALIB_ACCEL_SENSITIVITY;
      float accZ = mpu.getAccZ() - mpu.getAccBiasZ() / (float) MPU9250::CALIB_ACCEL_SENSITIVITY;

      if (abs(accX) >= GESTURE_ACC_THRESHOLD || abs(accY) >= GESTURE_ACC_THRESHOLD || abs(accZ) >= GESTURE_ACC_THRESHOLD) {
        setLedStripColor(0, 255, 0);
        ledStrip.show();

        recordedGestures[recordedGesturesLength][0] = accX;
        recordedGestures[recordedGesturesLength][1] = accY;
        recordedGestures[recordedGesturesLength][2] = accZ;

        gestureLastIntervalTime = millis();

        break;
      }
    }
  }

  for (int i = 1; i < GESTURE_LENGTH; i++) {
    while (gestureLastIntervalTime + GESTURE_INTERVAL_MS > millis());

    mpu.update();

    float accX = mpu.getAccX() - mpu.getAccBiasX() / (float) MPU9250::CALIB_ACCEL_SENSITIVITY;
    float accY = mpu.getAccY() - mpu.getAccBiasY() / (float) MPU9250::CALIB_ACCEL_SENSITIVITY;
    float accZ = mpu.getAccZ() - mpu.getAccBiasZ() / (float) MPU9250::CALIB_ACCEL_SENSITIVITY;

    recordedGestures[recordedGesturesLength][i * 3] = accX;
    recordedGestures[recordedGesturesLength][i * 3 + 1] = accY;
    recordedGestures[recordedGesturesLength][i * 3 + 2] = accZ;

    gestureLastIntervalTime = millis();
  }

  Serial.println("Recorded gesture");

  recordedGesturesLength++;

  setLedStripColor(0, 0, 0);
  ledStrip.show();
  delay(500);
}

/* Led Strip Control */

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
      //Bind brightness between max brightness:
      uint8_t boundedBrightness = (uint8_t) (((float) data[1] / 100.0) * LED_STRIP_MAX_BRIGHTNESS);

      ledStripBrightness = (uint8_t) (((float) boundedBrightness / 100.0) * 255.0);

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

/* Led Strip Effects */

void ledStripCountDown(const String& action) {
  Serial.println(action + "in 1.5 seconds...");

  setLedStripColor(0, 0, 0);
  ledStrip.show();

  for (int i = 0; i < LED_STRIP_NUM_LEDS; i++) {
    ledStrip.setPixelColor(i, 255, 0, 0);
    ledStrip.show();
    delay(1500 / LED_STRIP_NUM_LEDS);
  }
}

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
    case LED_STRIP_EFFECT_FADE: {
      updateFadeEffect();
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
