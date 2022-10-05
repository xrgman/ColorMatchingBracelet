#include <Arduino.h>
#include <Adafruit_NeoPixel.h>
// #include "FastLED.h"

#define LED_PIN 13
#define NUM_PIXELS 5 //Nr of leds in bracelet

//Creating led strip object:
Adafruit_NeoPixel bracelet(NUM_PIXELS, LED_PIN, NEO_GRB + NEO_KHZ800);

void setup() {
  bracelet.begin();
  Serial.begin(9600);
}

void loop() {
  bracelet.clear(); 

  for(int i=0; i<NUM_PIXELS; i++) {
      bracelet.setPixelColor(i, bracelet.Color(10, 150, 0));
      delay(1);
      bracelet.show();
      delay(500);
      //Serial.println("test");
  }

  delay(1000);
}
