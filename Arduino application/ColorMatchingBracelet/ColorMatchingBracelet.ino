#include <Arduino.h>
#include <Adafruit_NeoPixel.h>

#include <BLEDevice.h>
#include <BLEServer.h>
#include <BLEUtils.h>
#include <BLE2902.h>

#define SERVICE_UUID "1cf4fab1-d642-4153-a6f2-bf40db8d6f73"  // UART service UUID
#define CHARACTERISTIC_UUID_RX "75eb965e-a1e1-4b1d-8bb9-91e562cdb144"
#define CHARACTERISTIC_UUID_TX "aba19161-392b-4bed-9450-3a238abd0040"
#define BLUETOOTH_NAME "Color Matching Bracelet"

#define LED_PIN 12
#define NUM_PIXELS 10  //Nr of leds in bracelet

//Array with color values
uint32_t ledStripPixelColors[NUM_PIXELS];
bool ledStripPower;
uint8_t currentBrightness = 255;

//Effect fields:
uint32_t effectColor; 

//Battery status:
uint8_t batteryPercentage;

//Defining message types:
enum messageType {
  STAT,
  DEBUG,
  LEDSTRIP,
  MODE
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

//Storing current mode:
Mode currentMode = NORMAL;

//Storing current effect:
LedStripEffectType currentEffectType = NONE;

//Creating led strip object:
Adafruit_NeoPixel bracelet(NUM_PIXELS, LED_PIN, NEO_GRB + NEO_KHZ800);

//Bluetooth vars:
BLEServer *pServer = NULL;
BLECharacteristic *pTxCharacteristic;
bool deviceConnected = false;
bool oldDeviceConnected = false;

//Declaring functions:
void processLedstripCommand(std::string command);
void clearLedStrip(bool show);
void setLedStripPixel(int pixel, uint32_t color);
void setLedStrip(uint32_t color);
void sendStatistics();

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

/**
  Callback used when device is connected or disconnected:
**/
class MyServerCallbacks : public BLEServerCallbacks {
  void onConnect(BLEServer *pServer) {
    Serial.println("Device connected");

    deviceConnected = true;
  };

  void onDisconnect(BLEServer *pServer) {
    Serial.println("Device disconnected");

    deviceConnected = false;
  }
};

/**
  Callback used upon receiving data:
**/
class MyCallbacks : public BLECharacteristicCallbacks {
  void onWrite(BLECharacteristic *pCharacteristic) {
    std::string rxValue = pCharacteristic->getValue();

    uint8_t* data = pCharacteristic->getData();
    uint8_t dataSize = pCharacteristic->getLength();
    uint8_t checksumComp = 0;

    if(dataSize > 2) {
      //Looking if start character is correct, else discard:
      if (data[0] != 63) {
        Serial.println("Message with invalid start character: " + data[0]);
        return;
      }

      //Extracting type:
      uint8_t typeId = data[1];

      //Checking if type exists:
      if (typeId > MODE) {
        Serial.println("Invalid message type.");
        return;
      }

      Serial.print("Processing message of type: ");
      Serial.println(typeId);

      //Grabbing message length:
      uint8_t dataLength = data[2];

      //Do something with message:

      //Checksum:
      uint8_t checksum = data[dataSize - 1];

      for(int i = 0; i < dataSize - 1; i ++) {
        checksumComp ^= data[i];
      }

      if(checksumComp != checksum) {
        Serial.println("Invalid checksum, dropping message.");
        return;
      }

      //Grabbing data:
      uint8_t actualData[dataLength]; //3 + length

      memcpy(actualData, &data[3], dataLength);

      //Switching on message type:
      switch (typeId) {
        case STAT:
        {
          sendStatistics();
          break;          
        }
        case DEBUG:
          Serial.print("Debug message: ");

          for (int i = 0; i < dataLength; i++) {
            Serial.print((char) actualData[i]);
          }

          Serial.println("");

          break;
        case LEDSTRIP:
          processLedstripCommand(actualData, dataLength);
          break;
        case MODE:
        {
          Mode newMode = (Mode) actualData[0];

          Serial.print("Mode changed from ");
          Serial.print(currentMode);
          Serial.print(" to ");
          Serial.println(newMode);

          currentMode = newMode;

          if(newMode == EFFECT || newMode == EFFECT_NO_COLOR_CHANGE || newMode == MOTION_EFFECT) {            
            int effectTypeId = actualData[2];

            //Setting current effect:
            currentEffectType = (LedStripEffectType)effectTypeId;

            effectColor = ledStripPixelColors[0]; //Taking first for now :)
          } 
        }       
      }      
    }
  }
};

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

  pTxCharacteristic->setValue(message, size);
  pTxCharacteristic->notify();
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
  dataToSend[5] = NUM_PIXELS;  //First we send length
  
  sendMessage(STAT, dataToSend, size);
}

//**********************************
// State machine helper functions
//**********************************

bool canModeChangeColor(Mode mode) {
  return mode != EFFECT_NO_COLOR_CHANGE && 
        mode != MUSIC;
}

void setup() {
  Serial.begin(115200);

  //Starting ledstrip:
  bracelet.begin();

  //Clearing led strip:
  clearLedStrip(true);

  //Set all pixels to white:
  for (int i = 0; i < NUM_PIXELS; i++) {
    setLedStripPixel(i, bracelet.Color(255, 255, 255));
  }

  //Settings variables:
  ledStripPower = false;
  batteryPercentage = 90;  //Dummy for now :)

  //Starting bluetooth:
  BLEDevice::init(BLUETOOTH_NAME);

  //Create the bluetooth server and setting callback:
  pServer = BLEDevice::createServer();
  pServer->setCallbacks(new MyServerCallbacks());

  //Create bluetooth service and setting characteristics:
  BLEService *pService = pServer->createService(SERVICE_UUID);

  pTxCharacteristic = pService->createCharacteristic(CHARACTERISTIC_UUID_TX, BLECharacteristic::PROPERTY_NOTIFY);

  pTxCharacteristic->addDescriptor(new BLE2902());

  BLECharacteristic *pRxCharacteristic = pService->createCharacteristic(CHARACTERISTIC_UUID_RX, BLECharacteristic::PROPERTY_WRITE);

  pRxCharacteristic->setCallbacks(new MyCallbacks());

  //Starting bluetooth service:
  pService->start();

  //Start advertising:
  pServer->startAdvertising();

  Serial.println("Waiting for client connection");
}

uint32_t cnt = 0;

void loop() {
  // disconnecting
  if (!deviceConnected && oldDeviceConnected) {
    delay(500);                   // give the bluetooth stack the chance to get things ready
    pServer->startAdvertising();  // restart advertising
    Serial.println("Waiting for client connection");
    oldDeviceConnected = deviceConnected;
  }

  // connecting
  if (deviceConnected && !oldDeviceConnected) {
    // do stuff here on connecting
    oldDeviceConnected = deviceConnected;
  }

  //Send statistics if connected:
  // if (deviceConnected && cnt % 10000 == 0) {
  //   sendStatistics();
  // }

  cnt++;

  //Process effect:
  switch(currentMode) {
    case EFFECT: 
    {
      processEffects(cnt);
      break;
    }
    case EFFECT_NO_COLOR_CHANGE:
    {
      processEffects(cnt);
      break;      
    }
    case MOTION: 
    {
      processMotion();
      break;
    }
    case MOTION_EFFECT:
    {
      processMotion();
      processEffects(cnt);  
      break;
    }
  }
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

        bracelet.show();

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

        bracelet.setBrightness(currentBrightness);
        bracelet.show();

        break;
      }      
  }
}

void setLedStripPixel(int pixel, uint32_t color) {
  if (pixel < NUM_PIXELS) {
    ledStripPixelColors[pixel] = color;

    bracelet.setPixelColor(pixel, color);
  }
}

void setLedStrip(uint32_t color) {
  for(int i = 0; i < NUM_PIXELS; i++) {
    setLedStripPixel(i, color);
  }
}

void clearLedStrip(bool show) {
  for (int i = 0; i < NUM_PIXELS; i++) {
    bracelet.setPixelColor(i, bracelet.Color(0, 0, 0));
  }

  if(show) {
    bracelet.show();
  }
}

void setLedStripPower(bool powerState) {
  ledStripPower = powerState;

  if (powerState && currentEffectType == NONE) {
    //Restoring original state:
    for (int i = 0; i < NUM_PIXELS; i++) {
      bracelet.setPixelColor(i, ledStripPixelColors[i]);
    }

    bracelet.show();
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

  for (uint8_t i = 0; i < bracelet.numPixels(); i++) {
    setLedStripPixel(i, Wheel(((i * 256 / bracelet.numPixels()) + rainbowIdx) & 255));
  }

  bracelet.show();
  delay(wait);
  
  rainbowIdx = rainbowIdx >= 256 ? 0 : rainbowIdx+1;
}

uint32_t Wheel(byte WheelPos) {
  WheelPos = 255 - WheelPos;

  if (WheelPos < 85) {
    return bracelet.Color(255 - WheelPos * 3, 0, WheelPos * 3);
  }
  if (WheelPos < 170) {
    WheelPos -= 85;
    return bracelet.Color(0, WheelPos * 3, 255 - WheelPos * 3);
  }
  WheelPos -= 170;
  return bracelet.Color(WheelPos * 3, 255 - WheelPos * 3, 0);
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
    
    bracelet.setBrightness(fadeValue);
    bracelet.show();
  }
}

uint8_t circlePos = 0;

void circleEffect(uint32_t counter, uint32_t everyXCount) {
  if(counter % everyXCount == 0) {
    clearLedStrip(false);
    
    for(int i = circlePos; i < circlePos + 3; i++) {
      bracelet.setPixelColor(i % NUM_PIXELS, effectColor);
    }

    bracelet.show();

    circlePos++;

    //circlePos = circlePos >= NUM_PIXELS ? 0 : circlePos + 1;
  }
}

//****************
// Motion section
//****************

void processMotion() {
  
}

