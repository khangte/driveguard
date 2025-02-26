<div align="center">
    <h1>🚗 DriveGuard</h1>
    <h3>졸음운전 방지 애플리케이션</h3>
    <p>AI 기반 졸음 감지 및 CO2 측정을 통해 안전한 운전을 돕는 프로젝트</p>
</div>

---

## 📌 프로젝트 소개
DriveGuard는 운전자의 **눈 깜빡임, 하품 감지** 및 **이산화탄소 농도 측정**을 통해 졸음운전을 예방하는 애플리케이션입니다.  
Google MLKit를 활용하여 사용자의 졸음을 감지하고, 
졸음이 감지되면 **경고음 + TTS 음성 안내**를 제공하며,  
카카오맵 API와 카카오맵 키워드 검색을 활용해 **가까운 휴게소 및 졸음쉼터 정보**를 제공합니다.

---

## 📌 시스템 흐름도

<div align="center">
    <img src="![image](https://github.com/user-attachments/assets/c468aaef-1f26-4ba2-a6b6-a5464f6f4cb6)">
</div>

---

## 📚 기술 스택
### 개발 환경
<div>
    <img src="https://img.shields.io/badge/AndroidStudio-3DDC84?style=for-the-badge&logo=androidstudio&logoColor=white">
    <img src="https://img.shields.io/badge/Github-181717?style=for-the-badge&logo=github&logoColor=white">
</div>

### 사용 언어
<div>
    <img src="https://img.shields.io/badge/Java-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white">
    <img src="https://img.shields.io/badge/C%2B%2B-00599C?style=for-the-badge&logo=c%2B%2B&logoColor=white">
</div>

---

## 🛠️ 시스템 요구사항

| 구분 | 세부 내용 |
|------|----------------------------------------------------------|
| **하드웨어** | - 📷 **안드로이드 스마트폰 카메라** <br> - 🔌 **Arduino Uno** <br> - 🌫 **MH-Z19B CO₂ 센서** <br> - 📡 **Bluetooth 모듈 (HC-06)** <br> - ⚙️ **Arduino IDE** (센서 데이터 처리) |
| **소프트웨어** | - 📱 **Android Studio** (애플리케이션 개발) <br> - 🤖 **Google ML Kit** (FaceDetection 라이브러리) <br> - 🗺 **Kakao Maps API Android SDK** (사용자 위치 구현) <br> - 📍 **KakaoMap 애플리케이션** (위치 정보 표시 및 탐색) |
| **외부 라이브러리 및 API** | - 📷 **Android Camera2 API** <br> - 🔊 **TTS 라이브러리** <br> - 🎵 **SoundPool 라이브러리** <br> - 🗺 **Kakao Maps API Android SDK** <br> - 📡 **Bluetooth API** |

---

## 🔥 주요 기능

### ⭐ 실시간 졸음 감지  
- 📷 **카메라 기반 감지** (ML Kit)  
  - 눈 깜빡임, 하품 감지를 통해 졸음운전 판단  
  - 일정 기준 초과 시 경고음 + 음성 안내 발생  
- 🌿 **CO2 농도 감지** (Arduino + MH-Z19 센서)  
  - 차량 내부의 이산화탄소 농도를 측정하여 졸음 위험도 분석  
  - 위험 수치 초과 시 경고음 발생  

### ⭐ 졸음 감지 후 휴게소 안내  
- 🚘 **KakaoMap API로 현재 위치 반영**  
- 🏢 **졸음 감지 시, '휴게소' 또는 '졸음쉼터' 선택 → KakaoMap 키워드 검색으로 연결**  

---
