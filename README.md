# DailyBloom

DailyBloom은 사용자가 작은 습관을 꾸준히 실천할 수 있도록 돕는 Android 기반 습관 형성 앱입니다.  
AI 온보딩 추천, 일일 체크리스트, 캘린더/리포트, AI 채팅을 통한 습관 관리 기능을 제공합니다.

## 제출물 구성

```text
DailyBloom-Graduation-Project/
├─ source/                    소스 코드 및 개발 문서
├─ release/                   실행 파일
├─ documents/                 발표자료 및 결과보고서 PDF
├─ demo-video-link.txt        시연 영상 링크
└─ README.md                  프로젝트 안내 문서
```

## 포함 자료

- 프로젝트 소스 코드: `source/`
- Android 실행 파일: `release/DailyBloom-debug.apk`
- 발표자료 PDF: `documents/DailyBloom_발표자료.pdf`
- 프로젝트 결과 보고서 PDF: `documents/DailyBloom_결과보고서.pdf`
- 시연 영상 링크: `demo-video-link.txt`

## 시연 영상

https://youtu.be/AqvVHSaDHpw

## 주요 기능

- 로그인, 회원가입, 첫 로그인 온보딩 흐름
- 사용자 관심사 기반 AI 초기 습관 추천
- 습관 추가, 수정, 삭제 및 요일별 반복 설정
- 오늘의 체크리스트 완료/해제 관리
- 월별 캘린더 및 리포트 화면
- AI 채팅을 통한 습관 관련 질의응답
- AI 채팅에서 습관 추가/수정/삭제 요청 시 확인 후 적용
- 사용자, 습관, 체크리스트, 리포트, 온보딩, 채팅을 처리하는 백엔드 API

## 실행 파일

Android APK 파일은 아래 위치에 포함되어 있습니다.

```text
release/DailyBloom-debug.apk
```

## 빌드 방법

Android 프로젝트는 `source/` 폴더에 있습니다.

```powershell
cd source
$env:JAVA_HOME='C:\Program Files\Java\jdk-17'
.\gradlew.bat :app:assembleDebug
```

빌드가 완료되면 APK는 아래 경로에 생성됩니다.

```text
source/app/build/outputs/apk/debug/app-debug.apk
```

## 백엔드

백엔드 관련 파일은 `source/backend/`에 포함되어 있습니다.

- Flask gateway 서버
- PostgreSQL 데이터베이스 설정
- Docker Compose 설정
- API 문서

## 기술 스택

- Android Kotlin
- Jetpack Compose
- Gradle
- Flask
- PostgreSQL
- Docker
- Ollama 기반 LLM 연동
