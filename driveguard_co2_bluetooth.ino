#include <SoftwareSerial.h>
#include <MHZ19.h>

// CO2 센서
SoftwareSerial ss(2, 3); // RX: 2, TX: 3
MHZ19 mhz(&ss);          // 생성자에 SoftwareSerial 객체 전달

// Bluetooth 모듈
SoftwareSerial bt(10, 11); // RX: 10, TX: 11

unsigned long previousMillis = 0; // Non-blocking delay 변수
const long interval = 1000;       // 1초 간격

void setup() {
    Serial.begin(115200); // PC와 통신
    ss.begin(9600);       // CO2 센서 통신
    bt.begin(9600);       // Bluetooth 통신

    Serial.println(F("Starting..."));
    bt.println(F("Bluetooth Ready")); // 스마트폰으로 초기 메시지 전송
}

void loop() {
    unsigned long currentMillis = millis();
    if (currentMillis - previousMillis >= interval) {
        previousMillis = currentMillis;

        int co2 = mhz.getCO2(); // CO2 농도 읽기

        if (co2 > 0) {
        // Serial 출력
        Serial.print(F("CO2: "));
        Serial.println(co2);

        // Bluetooth로 전송
        bt.print(F("CO2: "));
        bt.println(co2);

        // 상태 분류 및 전송
        if (co2 <= 800) {
            bt.println(F("Status: 정상 (1)"));
        } else if (co2 <= 1500) {
            bt.println(F("Status: 주의 (2)"));
        } else if (co2 > 1500) {
            bt.println(F("Status: 비상 (3)"));
        } else {
            bt.println(F("Error: Unknown state"));
        }
        } else {
        // CO2 값 읽기 실패
        Serial.println(F("Error reading CO2 value"));
        bt.println(F("Error reading CO2 value"));
        }
    }
}
