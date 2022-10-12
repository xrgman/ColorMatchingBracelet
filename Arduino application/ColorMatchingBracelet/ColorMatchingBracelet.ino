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

#define LED_PIN 13
#define NUM_PIXELS 5 //Nr of leds in bracelet

//Defining message types:
enum messageType {
  INIT,
  STAT,
  DEBUG,
  LEDSTRIP
};

//Creating led strip object:
Adafruit_NeoPixel bracelet(NUM_PIXELS, LED_PIN, NEO_GRB + NEO_KHZ800);

//Bluetooth vars:
BLEServer *pServer = NULL;
BLECharacteristic * pTxCharacteristic;
bool deviceConnected = false;
bool oldDeviceConnected = false;


//Declaring functions:
void processLedstripCommand(std::string command);

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
        if(typeId >= LEDSTRIP) {
          Serial.println("Second or third character is not a number, expected type id here.");
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

        Serial.println("*********");
        Serial.print("Received Value: ");

        for (int i = 0; i < rxValue.length(); i++){
            Serial.print(rxValue[i]);          
        }
          
        Serial.println();
        Serial.println("*********");
      }
    }
};


void setup() {
  Serial.begin(115200);

  //Starting ledstrip:
  bracelet.begin();

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
}

void processLedstripCommand(std::string command) {

}
