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

enum messageType {
  STAT,
  DEBUG,
  LEDSTRIP,
  MODE,
  CALIBRATE,
  _NUM_MESSAGE_TYPES
};

enum Mode {
  NORMAL,
  EFFECT,
  EFFECT_NO_COLOR_CHANGE,
  MUSIC,
  MOTION,
  MOTION_EFFECT
};

enum LedStripCommandType {
  POWER,
  COLOR,
  BRIG,
  LED_EFFECT
};

enum LedStripEffectType {
  NONE,
  RAINBOW,
  CIRCLE,
  FADE
};

BLEServer* bleServer;
BLECharacteristic* bleTxCharacteristic;
bool bleIsAdvertising = false;
bool bleDeviceConnected = false;

MPU9250 mpu;

Adafruit_NeoPixel ledStrip(LED_STRIP_NUM_LEDS, LED_STRIP_PIN, NEO_GRB + NEO_KHZ800);
uint32_t ledStripColors[LED_STRIP_NUM_LEDS];
bool ledStripPower;
uint8_t currentBrightness = 255;

//Effect fields:
uint32_t effectColor; 

//Battery status:
uint8_t batteryPercentage;

//Storing current mode:
Mode currentMode = NORMAL;

//Storing current effect:
LedStripEffectType currentEffectType = NONE;

void calibrateAcc();

void processLedstripCommand(uint8_t* data, uint8_t dataLength);
void clearLedStrip(bool show);
void setLedStripPixel(int pixel, uint32_t color);
void setLedStrip(uint32_t color);
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
      case STAT: {
        sendStatistics();          
      } break;
      case DEBUG: {
        Serial.print("Debug message: ");

        for (int i = 0; i < payloadSize; i++) {
          Serial.print((char) payload[i]);
        }

        Serial.println("");

      } break;
      case LEDSTRIP: {
        processLedstripCommand(payload, payloadSize);
      } break;
      case MODE: {
        Mode newMode = (Mode) payload[0];

        Serial.print("Mode changed from ");
        Serial.print(currentMode);
        Serial.print(" to ");
        Serial.println(newMode);

        currentMode = newMode;

        if(newMode == EFFECT || newMode == EFFECT_NO_COLOR_CHANGE || newMode == MOTION_EFFECT) {            
          int effectTypeId = payload[2];

          //Setting current effect:
          currentEffectType = (LedStripEffectType)effectTypeId;

          effectColor = ledStripColors[0]; //Taking first for now :)
        } 
      } break;
      case CALIBRATE: {
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

  clearLedStrip(true);

  for (int i = 0; i < LED_STRIP_NUM_LEDS; i++) {
    setLedStripPixel(i, ledStrip.Color(255, 255, 255));
  }
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

uint32_t cnt = 0;

void loop() {
  if (!updateBle()) {
    return;
  }

  cnt++;

  switch (currentMode) {
    case EFFECT: {
      processEffects(cnt);
    } break;
    case EFFECT_NO_COLOR_CHANGE: {
      processEffects(cnt);
    } break;
    case MOTION: {
      processMotion();
    } break;
    case MOTION_EFFECT: {
      processMotion();
      processEffects(cnt);  
    } break;
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

void panic(const String& message) {
  Serial.println(message);
  while (1);
}

void bleStartAdvertising() {
  bleServer->startAdvertising();
  bleIsAdvertising = true;

  Serial.println("Waiting for BLE device connection");
}

void ui32_to_ui8(uint32_t source, uint8_t *dest) 
{
	dest[0] = (uint8_t)(source >> 24);
	dest[1] = (uint8_t)((source >> 16) & 0xFF);
	dest[2] = (uint8_t)((source >> 8) & 0xFF);
	dest[3] = (uint8_t)(source & 0xFF);
}

uint32_t to_ui32(uint8_t *pData) 
{
	uint32_t ret = 0;
	ret |= (pData[0] << 24);
	ret |= (pData[1] << 16);
	ret |= (pData[2] << 8);
	ret |= pData[3];

	return ret;
}

void sendMessage(messageType type, uint8_t *dataToSend, uint8_t data_size) {
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

  //Checksum:
  message[size - 1] = checkSum;

  //Serial.println(checkSum);

  bleTxCharacteristic->setValue(message, size);
  bleTxCharacteristic->notify();
}

/**
  Function used to construct statistics message and send it to device.
**/
void sendStatistics() {
  uint8_t size = 6;
  uint8_t dataToSend[size];

  dataToSend[0] = currentMode;
  dataToSend[1] = batteryPercentage;
  dataToSend[2] = (uint8_t) (ledStripPower ? '1' : '0'); 
  dataToSend[3] = currentEffectType;
  dataToSend[4] = currentBrightness;

  //Colors todo dit ff nakijken:
  dataToSend[5] = LED_STRIP_NUM_LEDS;  //First we send length
  
  sendMessage(STAT, dataToSend, size);
}

void calibrateAcc() {
  Serial.println("Calibrating accelerometer...");

  mpu.verbose(true);
  mpu.calibrateAccelGyro();
  mpu.verbose(false);

  Serial.println("Accelerometer is calibrated");

  EEPROM.put(EEPROM_ADDR_ACC_BIAS_X, mpu.getAccBiasX());
  EEPROM.put(EEPROM_ADDR_ACC_BIAS_Y, mpu.getAccBiasY());
  EEPROM.put(EEPROM_ADDR_ACC_BIAS_Z, mpu.getAccBiasZ());
}

//**********************************
// State machine helper functions
//**********************************

bool canModeChangeColor(Mode mode) {
  return mode != EFFECT_NO_COLOR_CHANGE && 
        mode != MUSIC;
}

void processLedstripCommand(uint8_t* data, uint8_t dataLength) {
  //Extracting type:
  int typeId = data[0];

  Serial.print("Processing ledstrip of type: ");
  Serial.println(typeId);

  switch (typeId) {
    case POWER:
      {
        bool powerValue = data[1] == 1;

        setLedStripPower(powerValue);

        break;
      }
    case COLOR:
      {
        uint8_t colorData[4];

        //Extract color data:
        colorData[0] = data[1];
        colorData[1] = data[2];
        colorData[2] = data[3];
        colorData[3] = data[4];    

        //Translate to uint32_t color:
        uint32_t color = to_ui32(colorData);

        if(currentMode == NORMAL || currentMode == MOTION) {
          setLedStrip(color);
        }

        ledStrip.show();

        //Saving for effect:
        effectColor = color;

        break;
      }       
    case BRIG:
      {
        int brightness = data[1];

        currentBrightness = ((float)brightness / 100) * 255;

        Serial.print("Brightness: ");
        Serial.println(currentBrightness);

        ledStrip.setBrightness(currentBrightness);
        ledStrip.show();

        break;
      }      
  }
}

void setLedStripPixel(int pixel, uint32_t color) {
  if (pixel < LED_STRIP_NUM_LEDS) {
    ledStripColors[pixel] = color;

    ledStrip.setPixelColor(pixel, color);
  }
}

void setLedStrip(uint32_t color) {
  for(int i = 0; i < LED_STRIP_NUM_LEDS; i++) {
    setLedStripPixel(i, color);
  }
}

void clearLedStrip(bool show) {
  for (int i = 0; i < LED_STRIP_NUM_LEDS; i++) {
    ledStrip.setPixelColor(i, ledStrip.Color(0, 0, 0));
  }

  if(show) {
    ledStrip.show();
  }
}

void setLedStripPower(bool powerState) {
  ledStripPower = powerState;

  if (powerState && currentEffectType == NONE) {
    //Restoring original state:
    for (int i = 0; i < LED_STRIP_NUM_LEDS; i++) {
      ledStrip.setPixelColor(i, ledStripColors[i]);
    }

    ledStrip.show();
  } else if(!powerState) {
    clearLedStrip(true);
  }
}

//**********************
// LedStrip effects
//**********************

void processEffects(uint32_t counter) {
  if(!ledStripPower) {
    return;
  }

  switch (currentEffectType) {
    case RAINBOW:
      rainbowCycle(10);
      break;
    case FADE:
      fadeEffect(counter, 10000);  
    case CIRCLE:
      circleEffect(counter, 10000);
  }
}

//Stolen from example :)
uint8_t rainbowIdx = 0;

void rainbowCycle(uint8_t wait) {
  //for (j = 0; j < 256; j++) {  // 5 cycles of all colors on wheel

  for (uint8_t i = 0; i < ledStrip.numPixels(); i++) {
    setLedStripPixel(i, Wheel(((i * 256 / ledStrip.numPixels()) + rainbowIdx) & 255));
  }

  ledStrip.show();
  delay(wait);
  
  rainbowIdx = rainbowIdx >= 256 ? 0 : rainbowIdx+1;
}

uint32_t Wheel(byte WheelPos) {
  WheelPos = 255 - WheelPos;

  if (WheelPos < 85) {
    return ledStrip.Color(255 - WheelPos * 3, 0, WheelPos * 3);
  }
  if (WheelPos < 170) {
    WheelPos -= 85;
    return ledStrip.Color(0, WheelPos * 3, 255 - WheelPos * 3);
  }
  WheelPos -= 170;
  return ledStrip.Color(WheelPos * 3, 255 - WheelPos * 3, 0);
}

uint8_t fadeValue = 255;
bool fadeUp = false;

void fadeEffect(uint32_t counter, uint32_t everyXCount) {
  if(counter % everyXCount == 0) {
    if(fadeUp) {
      if(fadeValue >= 255) {
        fadeUp = false;
      }
      
      fadeValue++;
    }
    else {
      if(fadeValue <= 10) {
        fadeUp = true;
      }

      fadeValue--;    
    }
    
    ledStrip.setBrightness(fadeValue);
    ledStrip.show();
  }
}

uint8_t circlePos = 0;

void circleEffect(uint32_t counter, uint32_t everyXCount) {
  if(counter % everyXCount == 0) {
    clearLedStrip(false);
    
    for(int i = circlePos; i < circlePos + 3; i++) {
      ledStrip.setPixelColor(i % LED_STRIP_NUM_LEDS, effectColor);
    }

    ledStrip.show();

    circlePos++;

    //circlePos = circlePos >= LED_STRIP_NUM_LEDS ? 0 : circlePos + 1;
  }
}

//****************
// Motion section
//****************

void processMotion() {
  
}

