#include <Arduino.h>
#include <Adafruit_NeoPixel.h>

#include <BLEDevice.h>
#include <BLEServer.h>
#include <BLEUtils.h>
#include <BLE2902.h>

#define SERVICE_UUID           "1cf4fab1-d642-4153-a6f2-bf40db8d6f73" // UART service UUID
#define CHARACTERISTIC_UUID_RX "75eb965e-a1e1-4b1d-8bb9-91e562cdb144"
#define CHARACTERISTIC_UUID_TX "aba19161-392b-4bed-9450-3a238abd0040"
#define BLUETOOTH_NAME "Color Matching Bracelet"

#define LED_PIN 12
#define NUM_PIXELS 10 //Nr of leds in bracelet

//Array with color values
uint32_t ledStripPixelColors[NUM_PIXELS];
bool ledStripPower;
int currentBrightness = 255;

//Battery status:
int batteryPercentage;

//Defining message types:
enum messageType {
  INIT,
  STAT,
  DEBUG,
  LEDSTRIP
};

enum LedStripCommandType {
  POWER,
  COLOR,
  BRIG,
  EFFECT
};

enum LedStripEffectType {
  NONE,
  RAINBOW,
  CIRCLE,
  FADE
};

//Storing current effect:
LedStripEffectType currentEffectType = NONE;

//Creating led strip object:
Adafruit_NeoPixel bracelet(NUM_PIXELS, LED_PIN, NEO_GRB + NEO_KHZ800);

//Bluetooth vars:
BLEServer *pServer = NULL;
BLECharacteristic * pTxCharacteristic;
bool deviceConnected = false;
bool oldDeviceConnected = false;


//Declaring functions:
void processLedstripCommand(std::string command);
void clearLedStrip();
void setLedStripPixel(int pixel, uint32_t color);

/**
  Callback used when device is connected or disconnected:
**/
class MyServerCallbacks: public BLEServerCallbacks {
    void onConnect(BLEServer* pServer) {
      Serial.println("Device connected");

      deviceConnected = true;
    };

    void onDisconnect(BLEServer* pServer) {
      Serial.println("Device disconnected");
      
      deviceConnected = false;
    }
};

/**
  Callback used upon receiving data:
**/
class MyCallbacks: public BLECharacteristicCallbacks {
    void onWrite(BLECharacteristic *pCharacteristic) {
      std::string rxValue = pCharacteristic->getValue();

      if (rxValue.length() > 2) {
        
        //Looking if start character is correct, else discard:
        if(rxValue[0] != '?') {
          Serial.println("Message with invalid start character: " + rxValue[0]);
          return;
        }

        //Extracting type:
        int typeId = int(rxValue[1]);  

        //Checking if type exists:
        if(typeId > LEDSTRIP) {
          Serial.println("Invalid message type.");
          return;
        }

        Serial.print("Processing message of type: ");
        Serial.println(typeId);

        //Grabbing message length:
        int messageLength = int(rxValue[2]);

        //Grabbing message:
        std::string message = rxValue.substr(3, messageLength);

        //Switching on message type:
        switch(typeId) {
          case DEBUG:
            Serial.print("Debug message: ");

            for (int i = 0; i < message.length(); i++){
              Serial.print(message[i]);          
            }

            Serial.println("");

            break;
          case LEDSTRIP:
            processLedstripCommand(message);
            break;
        }

        // Serial.println("*********");
        // Serial.print("Received Value: ");

        // for (int i = 0; i < rxValue.length(); i++){
        //     Serial.print(rxValue[i]);          
        // }
          
        // Serial.println();
        // Serial.println("*********");
      }
    }
};

void sendMessage(messageType type, std::string msg) {
  std::string message = "?";

  message += char(type);
  message += char(msg.length());

  message += msg;

  //TODO checksum

  pTxCharacteristic->setValue(message);
  pTxCharacteristic->notify();
}

/**
  Function used to construct statistics message and send it to device.
**/
void sendStatistics() {
  std::string message = "";

  message += char(batteryPercentage);
  message += ledStripPower ? "1" : "0"; 
  message += char(currentEffectType);
  message += char(currentBrightness);

  //Colors todo dit ff nakijken:
  message += char(NUM_PIXELS); //First we send length
  for(int i = 0; i < NUM_PIXELS; i++) {
    int shiftby = 16;

    for (int j = 0; j < 4; j++) {
        message[i + j] = (ledStripPixelColors[i] >> (shiftby -= 4)) & 0xF;
    }
  }
  
  //((uint32_t)r << 16) | ((uint32_t)g << 8) | b;

  sendMessage(STAT, message);
}


void setup() {
  Serial.begin(115200);

  //Starting ledstrip:
  bracelet.begin();
  
  //Clearing led strip:
  clearLedStrip();

  //Set all pixels to white:
  for(int i = 0; i < NUM_PIXELS; i++) {
    setLedStripPixel(i, bracelet.Color(255, 255, 255));
  }

  //Settings variables:
  ledStripPower = false;
  batteryPercentage = 90; //Dummy for now :)

  //Starting bluetooth:
  BLEDevice::init(BLUETOOTH_NAME);

  //Create the bluetooth server and setting callback:
  pServer = BLEDevice::createServer();
  pServer->setCallbacks(new MyServerCallbacks());

  //Create bluetooth service and setting characteristics:
  BLEService *pService = pServer->createService(SERVICE_UUID);

  pTxCharacteristic = pService->createCharacteristic(CHARACTERISTIC_UUID_TX, BLECharacteristic::PROPERTY_NOTIFY);
  
  pTxCharacteristic->addDescriptor(new BLE2902());

  BLECharacteristic * pRxCharacteristic = pService->createCharacteristic(CHARACTERISTIC_UUID_RX, BLECharacteristic::PROPERTY_WRITE);

  pRxCharacteristic->setCallbacks(new MyCallbacks());

  //Starting bluetooth service:
  pService->start();

  //Start advertising:
  pServer->startAdvertising();

  Serial.println("Waiting for client connection");
}

void rainbow(int wait) {
  for(long firstPixelHue = 0; firstPixelHue < 5*65536; firstPixelHue += 256) {
    for(int i=0; i<bracelet.numPixels(); i++) { 
      int pixelHue = firstPixelHue + (i * 65536L / bracelet.numPixels());
      bracelet.setPixelColor(i, bracelet.gamma32(bracelet.ColorHSV(pixelHue)));
    }
    bracelet.show();
    delay(wait);
  }
}

int cnt = 0;

void loop() {
    // disconnecting
    if (!deviceConnected && oldDeviceConnected) {
        delay(500); // give the bluetooth stack the chance to get things ready
        pServer->startAdvertising(); // restart advertising
        Serial.println("Waiting for client connection");
        oldDeviceConnected = deviceConnected;
    }

    // connecting
    if (deviceConnected && !oldDeviceConnected) {
		    // do stuff here on connecting
        oldDeviceConnected = deviceConnected;
    }  

    //Send statistics if connected:
    if(deviceConnected && cnt % 5000 == 0) {
        sendStatistics();
    }

    cnt++; 

    //Process effect:
    processEffects();
}

void processLedstripCommand(std::string command) {
    //Extracting type:
    int typeId = int(command[0]);  

    switch(typeId) {
      case POWER: 
      {
        bool powerValue = command[1] == '1';
        
        setLedStripPower(powerValue);

        break;
      }
      case BRIG:
      {
        int brightness = int(command[1]); 

        currentBrightness = ((float)brightness/100)*255;

        Serial.print("Brightness: ");
        Serial.println(currentBrightness);

        bracelet.setBrightness(currentBrightness);
        bracelet.show();      

        break;
      }
      case EFFECT:
      {
        int effectTypeId = int(command[1]);

        //Setting current effect:        
        currentEffectType = (LedStripEffectType) effectTypeId;

        break;
      }
    }
}

void setLedStripPixel(int pixel, uint32_t color) {
    if(pixel < NUM_PIXELS) {
      ledStripPixelColors[pixel] = color;

      bracelet.setPixelColor(pixel, color);
    }
}

void clearLedStrip() {
    for(int i = 0; i < NUM_PIXELS; i++) {
      bracelet.setPixelColor(i, bracelet.Color(0, 0, 0));
    }

    bracelet.show();
}


void setLedStripPower(bool powerState) {
  ledStripPower = powerState;  

  if(powerState) {
    //Restoring original state:
    for(int i = 0; i < NUM_PIXELS; i++) {
      bracelet.setPixelColor(i, ledStripPixelColors[i]);
    }

    bracelet.show();
  }
  else {    
    clearLedStrip();
  }
}

//**********************
// LedStrip effects
//**********************

void processEffects() {
  switch(currentEffectType) {
    case RAINBOW:
      rainbowCycle(10);
      break;
  }
}

//Stolen from example :)
//TODO non blocking
void rainbowCycle(uint8_t wait) {
  uint16_t i, j;

  for(j=0; j<256; j++) { // 5 cycles of all colors on wheel
    for(i=0; i< bracelet.numPixels(); i++) {
      setLedStripPixel(i, Wheel(((i * 256 / bracelet.numPixels()) + j) & 255)); 
    }

    bracelet.show();
    delay(wait);
  }
}

uint32_t Wheel(byte WheelPos) {
  WheelPos = 255 - WheelPos;
  
  if(WheelPos < 85) {
    return bracelet.Color(255 - WheelPos * 3, 0, WheelPos * 3);
  }
  if(WheelPos < 170) {
    WheelPos -= 85;
    return bracelet.Color(0, WheelPos * 3, 255 - WheelPos * 3);
  }
  WheelPos -= 170;
  return bracelet.Color(WheelPos * 3, 255 - WheelPos * 3, 0);
}
